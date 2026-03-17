package com.haf.client.services;

import com.haf.client.core.ChatSession;
import com.haf.client.core.NetworkSession;
import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.client.network.DefaultMessageReceiver;
import com.haf.client.network.DefaultMessageSender;
import com.haf.client.network.WebSocketAdapter;
import com.haf.client.utils.SslContextUtils;
import com.haf.client.viewmodels.MessageViewModel;
import com.haf.shared.exceptions.CryptoOperationException;
import com.haf.shared.requests.LoginRequest;
import com.haf.shared.responses.LoginResponse;
import com.haf.shared.responses.PublicKeyResponse;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.SystemClockProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultLoginService implements LoginService {

    private static final Logger LOGGER = Logger.getLogger(DefaultLoginService.class.getName());

    static final int MAX_LOGIN_ATTEMPTS = 3;
    static final int LOGIN_HTTP_TIMEOUT_SECONDS = 10;
    static final int LOGIN_RETRY_DELAY_MS = 400;
    static final String LOGIN_FAILED_MESSAGE = "Login failed.";
    static final String CONNECTION_INTERRUPTED_MESSAGE = "Connection was interrupted.";
    static final String CONNECTION_FAILED_MESSAGE = "Connection failed. Please try again.";
    static final String SECURE_SESSION_FAILED_MESSAGE = "Failed to initialize secure session locally.";

    private static final URI LOGIN_URI = URI.create("https://localhost:8443/api/v1/login");
    private static final URI WEBSOCKET_URI = URI.create("wss://localhost:8444/");

    @FunctionalInterface
    interface LoginGateway {
        HttpResponse<String> send(LoginCommand command) throws Exception;
    }

    @FunctionalInterface
    interface SessionBootstrap {
        void initialize(String userId, String sessionId, String passphrase) throws Exception;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final LoginGateway loginGateway;
    private final SessionBootstrap sessionBootstrap;
    private final Sleeper sleeper;

    public DefaultLoginService() {
        this(DefaultLoginService::sendLoginRequest, DefaultLoginService::initializeSecureSession, Thread::sleep);
    }

    DefaultLoginService(LoginGateway loginGateway, SessionBootstrap sessionBootstrap, Sleeper sleeper) {
        this.loginGateway = loginGateway;
        this.sessionBootstrap = sessionBootstrap;
        this.sleeper = sleeper;
    }

    @Override
    public LoginResult login(LoginCommand command) {
        if (command == null) {
            return new LoginResult.Failure(CONNECTION_FAILED_MESSAGE);
        }

        LoginCommand normalizedCommand = normalize(command);

        for (int attempt = 1; attempt <= MAX_LOGIN_ATTEMPTS; attempt++) {
            LoginResult attemptResult = performAttempt(normalizedCommand, attempt);
            if (attemptResult != null) {
                return attemptResult;
            }
        }
        return new LoginResult.Failure(CONNECTION_FAILED_MESSAGE);
    }

    private LoginResult performAttempt(LoginCommand command, int attempt) {
        try {
            HttpResponse<String> httpResponse = loginGateway.send(command);
            LoginResponse response = JsonCodec.fromJson(httpResponse.body(), LoginResponse.class);

            if (!isAuthenticated(httpResponse.statusCode(), response)) {
                return new LoginResult.Rejected(resolveRejectedMessage(response));
            }
            return initializeSessionOrRetry(response, command, attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LoginResult.Failure(CONNECTION_INTERRUPTED_MESSAGE);
        } catch (Exception e) {
            return handleRequestFailure(attempt, e);
        }
    }

    private LoginResult initializeSessionOrRetry(LoginResponse response, LoginCommand command, int attempt) {
        try {
            sessionBootstrap.initialize(response.getUserId(), response.getSessionId(), command.password());
            return new LoginResult.Success();
        } catch (Exception ex) {
            if (isLastAttempt(attempt)) {
                LOGGER.log(Level.SEVERE, "Failed to initialize WebSocket session", ex);
                return new LoginResult.Failure(SECURE_SESSION_FAILED_MESSAGE);
            }
            LOGGER.log(Level.WARNING, ex, () -> "Secure session initialization failed on attempt "
                    + attempt + "/" + MAX_LOGIN_ATTEMPTS + ", retrying...");
            return waitBeforeRetry();
        }
    }

    private LoginResult handleRequestFailure(int attempt, Exception error) {
        if (isLastAttempt(attempt)) {
            LOGGER.log(Level.SEVERE, "Login failed", error);
            return new LoginResult.Failure(CONNECTION_FAILED_MESSAGE);
        }
        LOGGER.log(Level.WARNING, error,
                () -> "Login attempt " + attempt + "/" + MAX_LOGIN_ATTEMPTS + " failed, retrying...");
        return waitBeforeRetry();
    }

    private LoginResult waitBeforeRetry() {
        try {
            sleeper.sleep(LOGIN_RETRY_DELAY_MS);
            return null;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return new LoginResult.Failure(CONNECTION_INTERRUPTED_MESSAGE);
        }
    }

    private static LoginCommand normalize(LoginCommand command) {
        String email = command.email() == null ? "" : command.email();
        String password = command.password() == null ? "" : command.password();
        return new LoginCommand(email, password);
    }

    private static boolean isAuthenticated(int statusCode, LoginResponse response) {
        return statusCode == 200 && response != null && response.getError() == null;
    }

    private static String resolveRejectedMessage(LoginResponse response) {
        if (response != null && response.getError() != null) {
            return response.getError();
        }
        return LOGIN_FAILED_MESSAGE;
    }

    private static boolean isLastAttempt(int attempt) {
        return attempt >= MAX_LOGIN_ATTEMPTS;
    }

    private static HttpResponse<String> sendLoginRequest(LoginCommand command) throws Exception {
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

    private static void initializeSecureSession(String userId, String sessionId, String passphraseStr) throws Exception {
        ClockProvider clockProvider = SystemClockProvider.getInstance();
        char[] passphrase = passphraseStr.toCharArray();
        UserKeystoreKeyProvider keyProvider = new UserKeystoreKeyProvider(userId, passphrase);

        WebSocketAdapter wsAdapter = new WebSocketAdapter(WEBSOCKET_URI, sessionId);
        keyProvider.setDirectoryServiceFetcher(recipientId -> fetchPublicKey(wsAdapter, recipientId));

        DefaultMessageSender sender = new DefaultMessageSender(keyProvider, clockProvider, wsAdapter);
        DefaultMessageReceiver receiver = new DefaultMessageReceiver(keyProvider, clockProvider, wsAdapter,
                keyProvider.getSenderId());

        NetworkSession.set(wsAdapter);
        ChatSession.set(new MessageViewModel(sender, receiver));
        receiver.start();
    }

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
        } catch (ExecutionException | com.haf.shared.exceptions.JsonCodecException e) {
            throw new CryptoOperationException("Directory service fetch failed", e);
        }
    }
}
