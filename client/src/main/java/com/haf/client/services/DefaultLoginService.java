package com.haf.client.services;

import com.haf.client.core.ChatSession;
import com.haf.client.core.AuthSessionState;
import com.haf.client.core.CurrentUserSession;
import com.haf.client.core.NetworkSession;
import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.client.models.UserProfileInfo;
import com.haf.client.network.DefaultMessageReceiver;
import com.haf.client.network.DefaultMessageSender;
import com.haf.client.network.WebSocketAdapter;
import com.haf.client.utils.ClientRuntimeConfig;
import com.haf.client.utils.SslContextUtils;
import com.haf.client.viewmodels.MessagesViewModel;
import com.haf.shared.exceptions.CryptoOperationException;
import com.haf.shared.exceptions.JsonCodecException;
import com.haf.shared.requests.LoginRequest;
import com.haf.shared.responses.LoginResponse;
import com.haf.shared.responses.PublicKeyResponse;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.SystemClockProvider;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles login transport and initializes authenticated client session state.
 */
public class DefaultLoginService implements LoginService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLoginService.class);

    static final int MAX_LOGIN_ATTEMPTS = 3;
    static final int LOGIN_HTTP_TIMEOUT_SECONDS = 10;
    static final int LOGIN_RETRY_DELAY_MS = 400;
    static final String ACCOUNT_ALREADY_LOGGED_IN = "Account is already logged in.";
    static final String KEY_MISMATCH_FAILURE_MESSAGE = "Local secure keys on this device do not match this account on the server. "
            + "Import this account keystore from the original device to receive messages.";

    @FunctionalInterface
    interface LoginGateway {
        /**
         * Executes the login transport call.
         *
         * @param command login command containing credentials
         * @return HTTP response containing login payload
         * @throws IOException          when transport I/O fails
         * @throws InterruptedException when the calling thread is interrupted
         */
        HttpResponse<String> send(LoginCommand command, TakeoverPayload takeoverPayload)
                throws IOException, InterruptedException;
    }

    /**
     * Typed takeover payload submitted to the login endpoint when forced takeover
     * is requested.
     *
     * @param publicKeyPem public key PEM used for takeover rotation
     * @param fingerprint  SHA-256 fingerprint of the provided public key
     */
    record TakeoverPayload(String publicKeyPem, String fingerprint) {
    }

    /**
     * In-memory generated takeover key material used for one takeover attempt
     * sequence.
     *
     * @param keyPair      generated X25519 key pair
     * @param publicKeyPem PEM-encoded public key
     * @param fingerprint  SHA-256 fingerprint of the generated public key
     */
    private record GeneratedTakeoverKey(KeyPair keyPair, String publicKeyPem, String fingerprint) {
    }

    @FunctionalInterface
    interface SessionBootstrap {
        /**
         * Initializes local secure-session state after successful authentication.
         *
         * @param userId     authenticated user identifier
         * @param sessionId  authenticated server session id
         * @param passphrase passphrase used to unlock local key material
         * @throws IOException              when key-store or network I/O fails
         * @throws CryptoOperationException when cryptographic setup fails
         */
        void initialize(String userId, String sessionId, String passphrase)
                throws IOException, CryptoOperationException;
    }

    @FunctionalInterface
    interface Sleeper {
        /**
         * Blocks for the requested retry delay.
         *
         * @param millis delay duration in milliseconds
         * @throws InterruptedException when sleep is interrupted
         */
        void sleep(long millis) throws InterruptedException;
    }

    private final LoginGateway loginGateway;
    private final SessionBootstrap sessionBootstrap;
    private final Sleeper sleeper;

    /**
     * Creates the default login service using real HTTP/network/bootstrap
     * dependencies.
     */
    public DefaultLoginService() {
        this(DefaultLoginService::sendLoginRequest, DefaultLoginService::initializeSecureSession, Thread::sleep);
    }

    /**
     * Creates a login service with injectable dependencies (primarily for tests).
     *
     * @param loginGateway     gateway used to submit login requests
     * @param sessionBootstrap callback used to initialize local secure session
     * @param sleeper          delay strategy for retry backoff
     */
    DefaultLoginService(LoginGateway loginGateway, SessionBootstrap sessionBootstrap, Sleeper sleeper) {
        this.loginGateway = loginGateway;
        this.sessionBootstrap = sessionBootstrap;
        this.sleeper = sleeper;
    }

    /**
     * Attempts user login with bounded retries and local secure-session bootstrap.
     *
     * @param command login credentials command
     * @return login result representing success, rejection, or failure
     */
    @Override
    public LoginResult login(LoginCommand command) {
        CurrentUserSession.clear();
        AuthSessionState.clear();
        if (command == null) {
            return new LoginResult.Failure("Connection failed. Please try again.");
        }

        LoginCommand normalizedCommand = normalize(command);

        for (int attempt = 1; attempt <= MAX_LOGIN_ATTEMPTS; attempt++) {
            LoginResult attemptResult = performAttempt(normalizedCommand, attempt, null, true);
            if (attemptResult != null) {
                return attemptResult;
            }
        }
        return new LoginResult.Failure("Connection failed. Please try again.");
    }

    /**
     * Executes explicit forced-takeover login using a fresh local key pair.
     *
     * @param command login credentials command
     * @return login result representing success, rejection, takeover-required, or
     *         failure
     */
    @Override
    public LoginResult performKeyTakeover(LoginCommand command) {
        CurrentUserSession.clear();
        AuthSessionState.clear();
        if (command == null) {
            return new LoginResult.Failure("Connection failed. Please try again.");
        }

        LoginCommand normalizedCommand = normalize(command);
        GeneratedTakeoverKey takeoverKey;
        try {
            takeoverKey = generateTakeoverKey();
        } catch (Exception ex) {
            LOGGER.error("Failed to generate takeover key material", ex);
            return new LoginResult.Failure("Failed to rotate secure keys for takeover.");
        }

        for (int attempt = 1; attempt <= MAX_LOGIN_ATTEMPTS; attempt++) {
            LoginResult attemptResult = performAttempt(normalizedCommand, attempt, takeoverKey, false);
            if (attemptResult != null) {
                return attemptResult;
            }
        }
        return new LoginResult.Failure("Connection failed. Please try again.");
    }

    /**
     * Performs a single login attempt and returns a terminal result or
     * {@code null} to continue retrying.
     *
     * @param command                normalized login command
     * @param attempt                current attempt number (1-based)
     * @param takeoverKey            generated takeover key material when running
     *                               forced takeover
     * @param mapDuplicateToTakeover whether login {@code 409} should be mapped to
     *                               takeover-required result
     * @return attempt result, or {@code null} when caller should retry
     */
    private LoginResult performAttempt(
            LoginCommand command,
            int attempt,
            GeneratedTakeoverKey takeoverKey,
            boolean mapDuplicateToTakeover) {
        try {
            TakeoverPayload takeoverPayload = takeoverKey == null
                    ? null
                    : new TakeoverPayload(takeoverKey.publicKeyPem(), takeoverKey.fingerprint());
            HttpResponse<String> httpResponse = loginGateway.send(command, takeoverPayload);
            LoginResponse response = JsonCodec.fromJson(httpResponse.body(), LoginResponse.class);

            if (mapDuplicateToTakeover
                    && isDuplicateSessionConflict(httpResponse.statusCode(), response)) {
                return new LoginResult.TakeoverRequired(
                        LoginService.TakeoverReason.DUPLICATE_SESSION,
                        resolveRejectedMessage(httpResponse.statusCode(), response));
            }
            if (!isAuthenticated(httpResponse.statusCode(), response)) {
                return new LoginResult.Rejected(resolveRejectedMessage(httpResponse.statusCode(), response));
            }
            return initializeSessionOrRetry(response, command, attempt, takeoverKey);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return new LoginResult.Failure("Connection was interrupted.");
        } catch (Exception e) {
            return handleRequestFailure(attempt, e);
        }
    }

    /**
     * Initializes local secure session after successful authentication response.
     *
     * @param response    successful login response payload
     * @param command     login command containing user passphrase
     * @param attempt     current attempt number
     * @param takeoverKey generated takeover key material when forced takeover is
     *                    active
     * @return success/failure result, or {@code null} when caller should retry
     */
    private LoginResult initializeSessionOrRetry(
            LoginResponse response,
            LoginCommand command,
            int attempt,
            GeneratedTakeoverKey takeoverKey) {
        try {
            if (takeoverKey != null) {
                persistTakeoverKeyMaterial(response.getUserId(), command.password(), takeoverKey);
            }
            sessionBootstrap.initialize(response.getUserId(), response.getSessionId(), command.password());
            AuthSessionState.set(
                    response.getSessionId(),
                    response.getRefreshToken(),
                    response.getAccessExpiresAtEpochSeconds(),
                    response.getRefreshExpiresAtEpochSeconds());
            CurrentUserSession.set(new UserProfileInfo(
                    response.getUserId(),
                    response.getFullName(),
                    response.getRank(),
                    response.getRegNumber(),
                    response.getJoinedDate(),
                    response.getEmail(),
                    response.getTelephone(),
                    true));
            return new LoginResult.Success();
        } catch (Exception ex) {
            if (isKeyMismatchFailure(ex)) {
                revokeSessionBestEffort(response.getSessionId());
                AuthSessionState.clear();
                return new LoginResult.TakeoverRequired(
                        LoginService.TakeoverReason.KEY_MISMATCH,
                        KEY_MISMATCH_FAILURE_MESSAGE);
            }
            if (isLastAttempt(attempt)) {
                LOGGER.error("Failed to initialize WebSocket session", ex);
                return new LoginResult.Failure(resolveSessionInitializationFailureMessage(ex));
            }
            LOGGER.warn("Secure session initialization failed on attempt {}/{}, retrying...",
                    attempt, MAX_LOGIN_ATTEMPTS, ex);
            return waitBeforeRetry();
        }
    }

    /**
     * Converts request/transport failures into retry or terminal failure results.
     *
     * @param attempt current attempt number
     * @param error   failure caught during request execution
     * @return failure result, or {@code null} when retry should continue
     */
    private LoginResult handleRequestFailure(int attempt, Exception error) {
        if (isLastAttempt(attempt)) {
            LOGGER.error("Login failed", error);
            return new LoginResult.Failure("Connection failed. Please try again.");
        }
        LOGGER.warn("Login attempt {}/{} failed, retrying...", attempt, MAX_LOGIN_ATTEMPTS, error);
        return waitBeforeRetry();
    }

    /**
     * Waits before retrying login.
     *
     * @return {@code null} when wait succeeds (caller may retry), otherwise failure
     *         result
     */
    private LoginResult waitBeforeRetry() {
        try {
            sleeper.sleep(LOGIN_RETRY_DELAY_MS);
            return null;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return new LoginResult.Failure("Connection was interrupted.");
        }
    }

    /**
     * Normalizes nullable login fields to non-null strings.
     *
     * @param command raw login command
     * @return normalized login command safe for downstream processing
     */
    private static LoginCommand normalize(LoginCommand command) {
        String email = command.email() == null ? "" : command.email();
        String password = command.password() == null ? "" : command.password();
        return new LoginCommand(email, password);
    }

    /**
     * Determines whether an HTTP/login payload pair represents successful
     * authentication.
     *
     * @param statusCode HTTP status code
     * @param response   parsed login response body
     * @return {@code true} when authentication succeeded
     */
    private static boolean isAuthenticated(int statusCode, LoginResponse response) {
        return statusCode == 200 && response != null && response.getError() == null;
    }

    /**
     * Determines whether login response indicates duplicate active session
     * conflict.
     *
     * @param statusCode HTTP status code
     * @param response   parsed login response body
     * @return {@code true} when duplicate-session takeover flow should be offered
     */
    private static boolean isDuplicateSessionConflict(int statusCode, LoginResponse response) {
        if (statusCode != 409) {
            return false;
        }
        if (response == null || response.getError() == null) {
            return true;
        }
        return ACCOUNT_ALREADY_LOGGED_IN.equalsIgnoreCase(response.getError().trim());
    }

    /**
     * Extracts user-friendly rejection text from login response.
     *
     * @param response parsed login response body
     * @return rejection reason or default login-failed text
     */
    private static String resolveRejectedMessage(int statusCode, LoginResponse response) {
        if (statusCode == 429) {
            long retryAfterSeconds = response != null && response.getRetryAfterSeconds() != null
                    ? response.getRetryAfterSeconds()
                    : 0L;
            if (retryAfterSeconds > 0L) {
                long retryAfterMinutes = Math.max(1L, (retryAfterSeconds + 59L) / 60L);
                return "Too many login attempts. Try again in " + retryAfterMinutes
                        + (retryAfterMinutes == 1L ? " minute." : " minutes.");
            }
            return "Too many login attempts. Try again later.";
        }
        if (response != null && response.getError() != null) {
            return response.getError();
        }
        return "Login failed.";
    }

    /**
     * Checks whether the given attempt is the final allowed attempt.
     *
     * @param attempt current attempt number
     * @return {@code true} when no retries remain
     */
    private static boolean isLastAttempt(int attempt) {
        return attempt >= MAX_LOGIN_ATTEMPTS;
    }

    /**
     * Checks whether an error chain indicates local/server key mismatch.
     *
     * @param error exception to inspect
     * @return {@code true} when mismatch marker message is found
     */
    private static boolean isKeyMismatchFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && KEY_MISMATCH_FAILURE_MESSAGE.equals(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Generates fresh takeover key material for forced account takeover.
     *
     * @return generated takeover key bundle
     * @throws Exception when key generation fails
     */
    private static GeneratedTakeoverKey generateTakeoverKey() throws Exception {
        KeyPair keyPair = EccKeyIO.generate();
        String publicKeyPem = EccKeyIO.publicPem(keyPair.getPublic());
        String fingerprint = FingerprintUtil.sha256Hex(EccKeyIO.publicDer(keyPair.getPublic()));
        return new GeneratedTakeoverKey(keyPair, publicKeyPem, fingerprint);
    }

    /**
     * Persists takeover key locally as current key before secure-session bootstrap.
     *
     * @param userId      authenticated user id
     * @param passphrase  keystore passphrase
     * @param takeoverKey generated takeover key material
     * @throws CryptoOperationException when local keystore rotation fails
     */
    private static void persistTakeoverKeyMaterial(
            String userId,
            String passphrase,
            GeneratedTakeoverKey takeoverKey) throws CryptoOperationException {
        char[] passphraseChars = passphrase == null ? new char[0] : passphrase.toCharArray();
        try {
            UserKeystoreKeyProvider keyProvider = new UserKeystoreKeyProvider(userId, passphraseChars);
            List<com.haf.shared.dto.KeyMetadata> metadata = keyProvider.getKeyStore().listMetadata();
            String currentKeyId = resolveCurrentKeyId(metadata);
            String nextKeyId = buildTakeoverKeyId();
            if (currentKeyId == null || currentKeyId.isBlank()) {
                keyProvider.getKeyStore().saveKeypair(nextKeyId, takeoverKey.keyPair(), passphraseChars);
            } else {
                keyProvider.getKeyStore().rotate(currentKeyId, nextKeyId, takeoverKey.keyPair(), passphraseChars);
            }
        } catch (Exception ex) {
            throw new CryptoOperationException("Failed to persist takeover key material locally", ex);
        } finally {
            java.util.Arrays.fill(passphraseChars, '\0');
        }
    }

    /**
     * Resolves current key id from key metadata, preferring CURRENT entries.
     *
     * @param metadata local keystore metadata entries
     * @return key id to demote during rotation, or {@code null} when no entries
     */
    private static String resolveCurrentKeyId(List<com.haf.shared.dto.KeyMetadata> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return metadata.stream()
                .filter(java.util.Objects::nonNull)
                .filter(entry -> entry.keyId() != null && !entry.keyId().isBlank())
                .min(Comparator
                        .comparing((com.haf.shared.dto.KeyMetadata entry) -> !"CURRENT".equalsIgnoreCase(
                                String.valueOf(entry.status())))
                        .thenComparing(Comparator.comparingLong(com.haf.shared.dto.KeyMetadata::createdAtEpochSec)
                                .reversed()))
                .map(com.haf.shared.dto.KeyMetadata::keyId)
                .orElse(null);
    }

    /**
     * Builds a unique key id label for takeover-rotated local key material.
     *
     * @return unique key id
     */
    private static String buildTakeoverKeyId() {
        return "key-takeover-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Sends the HTTP login request to the server.
     *
     * @param command normalized login command
     * @return HTTP response containing login payload
     * @throws IOException          when request construction or transport fails
     * @throws InterruptedException when the calling thread is interrupted
     */
    private static HttpResponse<String> sendLoginRequest(LoginCommand command, TakeoverPayload takeoverPayload)
            throws IOException, InterruptedException {
        ClientRuntimeConfig runtimeConfig = ClientRuntimeConfig.load();
        LoginRequest request = new LoginRequest();
        request.setEmail(command.email());
        request.setPassword(command.password());
        if (takeoverPayload != null) {
            request.setForceTakeover(Boolean.TRUE);
            request.setTakeoverPublicKeyPem(takeoverPayload.publicKeyPem());
            request.setTakeoverPublicKeyFingerprint(takeoverPayload.fingerprint());
        }
        String json = JsonCodec.toJson(request);

        HttpClient client = HttpClient.newBuilder()
                .sslContext(SslContextUtils.getSslContextForMode(runtimeConfig.isDev()))
                .sslParameters(SslContextUtils.createHttpsSslParameters())
                .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(resolveLoginUri(runtimeConfig))
                .timeout(Duration.ofSeconds(LOGIN_HTTP_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Builds local websocket + messaging session state for authenticated users.
     *
     * @param userId        authenticated user identifier
     * @param sessionId     authenticated websocket session id
     * @param passphraseStr passphrase used to unlock local keys
     * @throws IOException              when key-store or network I/O fails
     * @throws CryptoOperationException when cryptographic setup fails
     */
    private static void initializeSecureSession(String userId, String sessionId, String passphraseStr)
            throws IOException, CryptoOperationException {
        ClientRuntimeConfig runtimeConfig = ClientRuntimeConfig.load();
        ClockProvider clockProvider = SystemClockProvider.getInstance();
        char[] passphrase = passphraseStr.toCharArray();
        try {
            UserKeystoreKeyProvider keyProvider = new UserKeystoreKeyProvider(userId, passphrase);

            WebSocketAdapter wsAdapter = new WebSocketAdapter(
                    resolveWebSocketUri(runtimeConfig),
                    resolveServerBaseUri(runtimeConfig),
                    sessionId);
            keyProvider.setDirectoryServiceFetcher(recipientId -> fetchPublicKey(wsAdapter, recipientId));
            verifyLocalIdentityFingerprint(userId, keyProvider, wsAdapter);

            DefaultMessageSender sender = new DefaultMessageSender(keyProvider, clockProvider, wsAdapter);
            DefaultMessageReceiver receiver = new DefaultMessageReceiver(keyProvider, clockProvider, wsAdapter,
                    keyProvider.getSenderId(),
                    runtimeConfig.messagingTransportMode());

            NetworkSession.set(wsAdapter);
            ChatSession.set(new MessagesViewModel(sender, receiver));
        } catch (GeneralSecurityException e) {
            throw new CryptoOperationException("Keystore initialization failed", e);
        }
    }

    /**
     * Returns a user-facing secure-session initialization failure message.
     *
     * @param error initialization error
     * @return user-facing failure message
     */
    private static String resolveSessionInitializationFailureMessage(Exception error) {
        if (error != null && KEY_MISMATCH_FAILURE_MESSAGE.equals(error.getMessage())) {
            return KEY_MISMATCH_FAILURE_MESSAGE;
        }
        return "Failed to initialize secure session locally.";
    }

    /**
     * Revokes a temporary authenticated session id created before key mismatch was
     * detected.
     *
     * @param sessionId session id to revoke
     */
    private static void revokeSessionBestEffort(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            ClientRuntimeConfig runtimeConfig = ClientRuntimeConfig.load();
            HttpClient client = HttpClient.newBuilder()
                    .sslContext(SslContextUtils.getSslContextForMode(runtimeConfig.isDev()))
                    .sslParameters(SslContextUtils.createHttpsSslParameters())
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(resolveServerBaseUri(runtimeConfig).resolve("/api/v1/logout"))
                    .timeout(Duration.ofSeconds(LOGIN_HTTP_TIMEOUT_SECONDS))
                    .header("Authorization", "Bearer " + sessionId)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.info("Best-effort temporary session revoke interrupted");
        } catch (Exception ex) {
            LOGGER.info("Best-effort temporary session revoke failed: {}", ex.getMessage());
        }
    }

    static URI resolveServerBaseUri(ClientRuntimeConfig runtimeConfig) {
        return runtimeConfig.serverBaseUri();
    }

    static URI resolveLoginUri(ClientRuntimeConfig runtimeConfig) {
        return resolveServerBaseUri(runtimeConfig).resolve("/api/v1/login");
    }

    static URI resolveWebSocketUri(ClientRuntimeConfig runtimeConfig) {
        return runtimeConfig.webSocketBaseUri();
    }

    /**
     * Fetches a recipient public key from the authenticated directory endpoint.
     *
     * @param wsAdapter   authenticated websocket adapter used for REST call
     * @param recipientId recipient identifier to look up
     * @return PEM-encoded public key, or {@code null} when key is not available
     * @throws CryptoOperationException when network/json operations fail
     */
    private static String fetchPublicKey(WebSocketAdapter wsAdapter, String recipientId) {
        try {
            String path = "/api/v1/users/" + recipientId + "/key";
            String jsonResponse = wsAdapter.getAuthenticated(path).get();
            PublicKeyResponse keyRes = JsonCodec.fromJson(jsonResponse, PublicKeyResponse.class);
            if (keyRes.isSuccess() && keyRes.getPublicKeyPem() != null) {
                return keyRes.getPublicKeyPem();
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CryptoOperationException("Directory service fetch interrupted", e);
        } catch (ExecutionException | JsonCodecException e) {
            throw new CryptoOperationException("Directory service fetch failed", e);
        }
    }

    /**
     * Verifies that the local current public key fingerprint matches server-side
     * identity for the authenticated account.
     *
     * This prevents silently entering a session state where outbound sends work
     * but inbound decryption fails with AEAD tag errors on devices that do not
     * have the original keystore for the account.
     *
     * @param userId      authenticated user id
     * @param keyProvider local keystore-backed key provider
     * @param wsAdapter   authenticated adapter for key-lookup API
     * @throws CryptoOperationException when local fingerprint conflicts with server
     *                                  identity
     */
    private static void verifyLocalIdentityFingerprint(String userId,
            UserKeystoreKeyProvider keyProvider,
            WebSocketAdapter wsAdapter) throws CryptoOperationException {
        try {
            String path = "/api/v1/users/" + userId + "/key";
            String jsonResponse = wsAdapter.getAuthenticated(path).get();
            PublicKeyResponse keyResponse = JsonCodec.fromJson(jsonResponse, PublicKeyResponse.class);

            if (keyResponse == null
                    || !keyResponse.isSuccess()
                    || keyResponse.getFingerprint() == null
                    || keyResponse.getFingerprint().isBlank()) {
                return;
            }

            String serverFingerprint = keyResponse.getFingerprint().trim();
            String localFingerprint = FingerprintUtil.sha256Hex(
                    EccKeyIO.publicDer(keyProvider.getKeyStore().loadCurrentPublic()));
            if (!serverFingerprint.equalsIgnoreCase(localFingerprint)) {
                throw new CryptoOperationException(KEY_MISMATCH_FAILURE_MESSAGE);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CryptoOperationException("Secure key verification interrupted", e);
        } catch (ExecutionException | JsonCodecException e) {
            LOGGER.warn("Could not verify local key fingerprint against server identity; continuing.", e);
        } catch (CryptoOperationException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoOperationException("Failed to verify local secure key identity", e);
        }
    }
}
