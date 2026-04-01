package com.haf.client.services;

import com.haf.client.core.ChatSession;
import com.haf.client.core.CurrentUserSession;
import com.haf.client.core.NetworkSession;
import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.client.models.UserProfileInfo;
import com.haf.client.network.DefaultMessageReceiver;
import com.haf.client.network.DefaultMessageSender;
import com.haf.client.network.WebSocketAdapter;
import com.haf.client.utils.SslContextUtils;
import com.haf.client.viewmodels.MessagesViewModel;
import com.haf.shared.exceptions.CryptoOperationException;
import com.haf.shared.exceptions.JsonCodecException;
import com.haf.shared.requests.LoginRequest;
import com.haf.shared.responses.LoginResponse;
import com.haf.shared.responses.PublicKeyResponse;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.SystemClockProvider;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultLoginService implements LoginService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLoginService.class);

    static final int MAX_LOGIN_ATTEMPTS = 3;
    static final int LOGIN_HTTP_TIMEOUT_SECONDS = 10;
    static final int LOGIN_RETRY_DELAY_MS = 400;

    private static final URI LOGIN_URI = URI.create("https://localhost:8443/api/v1/login");
    private static final URI WEBSOCKET_URI = URI.create("wss://localhost:8444/");

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
        HttpResponse<String> send(LoginCommand command) throws IOException, InterruptedException;
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
        void initialize(String userId, String sessionId, String passphrase) throws IOException, CryptoOperationException;
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
        if (command == null) {
            return new LoginResult.Failure("Connection failed. Please try again.");
        }

        LoginCommand normalizedCommand = normalize(command);

        for (int attempt = 1; attempt <= MAX_LOGIN_ATTEMPTS; attempt++) {
            LoginResult attemptResult = performAttempt(normalizedCommand, attempt);
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
     * @param command normalized login command
     * @param attempt current attempt number (1-based)
     * @return attempt result, or {@code null} when caller should retry
     */
    private LoginResult performAttempt(LoginCommand command, int attempt) {
        try {
            HttpResponse<String> httpResponse = loginGateway.send(command);
            LoginResponse response = JsonCodec.fromJson(httpResponse.body(), LoginResponse.class);

            if (!isAuthenticated(httpResponse.statusCode(), response)) {
                return new LoginResult.Rejected(resolveRejectedMessage(response));
            }
            return initializeSessionOrRetry(response, command, attempt);
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
     * @param response successful login response payload
     * @param command  login command containing user passphrase
     * @param attempt  current attempt number
     * @return success/failure result, or {@code null} when caller should retry
     */
    private LoginResult initializeSessionOrRetry(LoginResponse response, LoginCommand command, int attempt) {
        try {
            sessionBootstrap.initialize(response.getUserId(), response.getSessionId(), command.password());
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
            if (isLastAttempt(attempt)) {
                LOGGER.error( "Failed to initialize WebSocket session", ex);
                return new LoginResult.Failure("Failed to initialize secure session locally.");
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
            LOGGER.error( "Login failed", error);
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
     * Extracts user-friendly rejection text from login response.
     *
     * @param response parsed login response body
     * @return rejection reason or default login-failed text
     */
    private static String resolveRejectedMessage(LoginResponse response) {
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
     * Sends the HTTP login request to the server.
     *
     * @param command normalized login command
     * @return HTTP response containing login payload
     * @throws IOException          when request construction or transport fails
     * @throws InterruptedException when the calling thread is interrupted
     */
    private static HttpResponse<String> sendLoginRequest(LoginCommand command)
            throws IOException, InterruptedException {
        LoginRequest request = new LoginRequest();
        request.setEmail(command.email());
        request.setPassword(command.password());
        String json = JsonCodec.toJson(request);

        HttpClient client = HttpClient.newBuilder()
                .sslContext(SslContextUtils.getTrustingSslContext())
                .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(LOGIN_URI)
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
        ClockProvider clockProvider = SystemClockProvider.getInstance();
        char[] passphrase = passphraseStr.toCharArray();
        try {
            UserKeystoreKeyProvider keyProvider = new UserKeystoreKeyProvider(userId, passphrase);

            WebSocketAdapter wsAdapter = new WebSocketAdapter(WEBSOCKET_URI, sessionId);
            keyProvider.setDirectoryServiceFetcher(recipientId -> fetchPublicKey(wsAdapter, recipientId));

            DefaultMessageSender sender = new DefaultMessageSender(keyProvider, clockProvider, wsAdapter);
            DefaultMessageReceiver receiver = new DefaultMessageReceiver(keyProvider, clockProvider, wsAdapter,
                    keyProvider.getSenderId());

            NetworkSession.set(wsAdapter);
            ChatSession.set(new MessagesViewModel(sender, receiver));
        } catch (GeneralSecurityException e) {
            throw new CryptoOperationException("Keystore initialization failed", e);
        }
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
}
