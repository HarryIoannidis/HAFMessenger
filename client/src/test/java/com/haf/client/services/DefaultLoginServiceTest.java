package com.haf.client.services;

import com.haf.client.core.CurrentUserSession;
import com.haf.client.models.UserProfileInfo;
import com.haf.client.utils.ClientRuntimeConfig;
import com.haf.shared.exceptions.CryptoOperationException;
import com.haf.shared.responses.LoginResponse;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultLoginServiceTest {

    @BeforeEach
    void clearCurrentUserSession() {
        CurrentUserSession.clear();
    }

    @Test
    void endpoint_resolution_uses_localhost_in_dev_mode() {
        ClientRuntimeConfig config = ClientRuntimeConfig.fromProperties(new Properties());

        assertEquals(URI.create("https://localhost:8443/api/v1/login"), DefaultLoginService.resolveLoginUri(config));
        assertEquals(URI.create("wss://localhost:8444/"), DefaultLoginService.resolveWebSocketUri(config));
        assertEquals(ClientRuntimeConfig.MessagingTransportMode.WEBSOCKET, config.messagingTransportMode());
    }

    @Test
    void endpoint_resolution_uses_prod_endpoints_when_dev_is_disabled() {
        Properties properties = new Properties();
        properties.setProperty("app.isDev", "false");
        properties.setProperty("server.url.prod", "https://prod.example.test");
        ClientRuntimeConfig config = ClientRuntimeConfig.fromProperties(properties);

        assertEquals(URI.create("https://prod.example.test/api/v1/login"), DefaultLoginService.resolveLoginUri(config));
        assertEquals(URI.create("wss://prod.example.test/"), DefaultLoginService.resolveWebSocketUri(config));
        assertEquals(ClientRuntimeConfig.MessagingTransportMode.HTTPS_POLLING, config.messagingTransportMode());
    }

    @Test
    void login_success_on_first_attempt() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger bootstrapCalls = new AtomicInteger();

        DefaultLoginService service = new DefaultLoginService(
                (command, takeoverPayload) -> {
                    attempts.incrementAndGet();
                    return response(200, JsonCodec.toJson(LoginResponse.success("u1", "s1", "User", "Rank", "ONLINE")));
                },
                (userId, sessionId, passphrase) -> bootstrapCalls.incrementAndGet(),
                millis -> {
                });

        LoginService.LoginResult result = service.login(new LoginService.LoginCommand("user@haf.gr", "password"));

        assertInstanceOf(LoginService.LoginResult.Success.class, result);
        assertEquals(1, attempts.get());
        assertEquals(1, bootstrapCalls.get());
    }

    @Test
    void login_transient_failure_then_success() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger bootstrapCalls = new AtomicInteger();
        AtomicInteger sleepCalls = new AtomicInteger();

        DefaultLoginService service = new DefaultLoginService(
                (command, takeoverPayload) -> {
                    int currentAttempt = attempts.incrementAndGet();
                    if (currentAttempt == 1) {
                        throw new RuntimeException("temporary outage");
                    }
                    return response(200, JsonCodec.toJson(LoginResponse.success("u1", "s1", "User", "Rank", "ONLINE")));
                },
                (userId, sessionId, passphrase) -> bootstrapCalls.incrementAndGet(),
                millis -> sleepCalls.incrementAndGet());

        LoginService.LoginResult result = service.login(new LoginService.LoginCommand("user@haf.gr", "password"));

        assertInstanceOf(LoginService.LoginResult.Success.class, result);
        assertEquals(2, attempts.get());
        assertEquals(1, bootstrapCalls.get());
        assertEquals(1, sleepCalls.get());
    }

    @Test
    void login_transient_failure_all_attempts_returns_failure() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger sleepCalls = new AtomicInteger();

        DefaultLoginService service = new DefaultLoginService(
                (command, takeoverPayload) -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("network down");
                },
                (userId, sessionId, passphrase) -> {
                },
                millis -> sleepCalls.incrementAndGet());

        LoginService.LoginResult result = service.login(new LoginService.LoginCommand("user@haf.gr", "password"));

        LoginService.LoginResult.Failure failure = assertInstanceOf(LoginService.LoginResult.Failure.class, result);
        assertEquals("Connection failed. Please try again.", failure.message());
        assertEquals(DefaultLoginService.MAX_LOGIN_ATTEMPTS, attempts.get());
        assertEquals(DefaultLoginService.MAX_LOGIN_ATTEMPTS - 1, sleepCalls.get());
    }

    @Test
    void login_secure_session_init_failure_retries_then_fails() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger bootstrapCalls = new AtomicInteger();
        AtomicInteger sleepCalls = new AtomicInteger();

        DefaultLoginService service = new DefaultLoginService(
                (command, takeoverPayload) -> {
                    attempts.incrementAndGet();
                    return response(200, JsonCodec.toJson(LoginResponse.success("u1", "s1", "User", "Rank", "ONLINE")));
                },
                (userId, sessionId, passphrase) -> {
                    bootstrapCalls.incrementAndGet();
                    throw new RuntimeException("ws init failed");
                },
                millis -> sleepCalls.incrementAndGet());

        LoginService.LoginResult result = service.login(new LoginService.LoginCommand("user@haf.gr", "password"));

        LoginService.LoginResult.Failure failure = assertInstanceOf(LoginService.LoginResult.Failure.class, result);
        assertEquals("Failed to initialize secure session locally.", failure.message());
        assertEquals(DefaultLoginService.MAX_LOGIN_ATTEMPTS, attempts.get());
        assertEquals(DefaultLoginService.MAX_LOGIN_ATTEMPTS, bootstrapCalls.get());
        assertEquals(DefaultLoginService.MAX_LOGIN_ATTEMPTS - 1, sleepCalls.get());
    }

    @Test
    void login_key_mismatch_failure_returns_actionable_message() {
        AtomicInteger attempts = new AtomicInteger();

        DefaultLoginService service = new DefaultLoginService(
                (command, takeoverPayload) -> {
                    attempts.incrementAndGet();
                    return response(200, JsonCodec.toJson(LoginResponse.success("u1", "s1", "User", "Rank", "ONLINE")));
                },
                (userId, sessionId, passphrase) -> {
                    throw new CryptoOperationException(DefaultLoginService.KEY_MISMATCH_FAILURE_MESSAGE);
                },
                millis -> {
                });

        LoginService.LoginResult result = service.login(new LoginService.LoginCommand("user@haf.gr", "password"));

        LoginService.LoginResult.TakeoverRequired takeoverRequired = assertInstanceOf(
                LoginService.LoginResult.TakeoverRequired.class,
                result);
        assertEquals(LoginService.TakeoverReason.KEY_MISMATCH, takeoverRequired.reason());
        assertEquals(DefaultLoginService.KEY_MISMATCH_FAILURE_MESSAGE, takeoverRequired.message());
        assertEquals(1, attempts.get());
    }

    @Test
    void login_backend_rejection_returns_rejected_without_retry() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger bootstrapCalls = new AtomicInteger();

        DefaultLoginService service = new DefaultLoginService(
                (command, takeoverPayload) -> {
                    attempts.incrementAndGet();
                    return response(401, JsonCodec.toJson(LoginResponse.error("Invalid credentials")));
                },
                (userId, sessionId, passphrase) -> bootstrapCalls.incrementAndGet(),
                millis -> {
                });

        LoginService.LoginResult result = service.login(new LoginService.LoginCommand("user@haf.gr", "bad-pass"));

        LoginService.LoginResult.Rejected rejected = assertInstanceOf(LoginService.LoginResult.Rejected.class, result);
        assertEquals("Invalid credentials", rejected.message());
        assertEquals(1, attempts.get());
        assertEquals(0, bootstrapCalls.get());
    }

    @Test
    void login_already_logged_in_rejection_returns_message_without_retry() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger bootstrapCalls = new AtomicInteger();

        DefaultLoginService service = new DefaultLoginService(
                (command, takeoverPayload) -> {
                    attempts.incrementAndGet();
                    return response(409, JsonCodec.toJson(LoginResponse.error("Account is already logged in.")));
                },
                (userId, sessionId, passphrase) -> bootstrapCalls.incrementAndGet(),
                millis -> {
                });

        LoginService.LoginResult result = service.login(new LoginService.LoginCommand("user@haf.gr", "password"));

        LoginService.LoginResult.TakeoverRequired takeoverRequired = assertInstanceOf(
                LoginService.LoginResult.TakeoverRequired.class,
                result);
        assertEquals(LoginService.TakeoverReason.DUPLICATE_SESSION, takeoverRequired.reason());
        assertEquals("Account is already logged in.", takeoverRequired.message());
        assertEquals(1, attempts.get());
        assertEquals(0, bootstrapCalls.get());
    }

    @Test
    void perform_key_takeover_submits_takeover_payload_and_bootstraps_session() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger bootstrapCalls = new AtomicInteger();

        DefaultLoginService service = new DefaultLoginService(
                (command, takeoverPayload) -> {
                    attempts.incrementAndGet();
                    if (takeoverPayload == null
                            || takeoverPayload.publicKeyPem() == null
                            || takeoverPayload.publicKeyPem().isBlank()
                            || takeoverPayload.fingerprint() == null
                            || takeoverPayload.fingerprint().isBlank()) {
                        return response(400, JsonCodec.toJson(LoginResponse.error("missing takeover payload")));
                    }
                    return response(200, JsonCodec.toJson(LoginResponse.success("u1", "s1", "User", "Rank", "ONLINE")));
                },
                (userId, sessionId, passphrase) -> bootstrapCalls.incrementAndGet(),
                millis -> {
                });

        LoginService.LoginResult result = service.performKeyTakeover(
                new LoginService.LoginCommand("user@haf.gr", "password"));

        assertInstanceOf(LoginService.LoginResult.Success.class, result);
        assertEquals(1, attempts.get());
        assertEquals(1, bootstrapCalls.get());
    }

    @Test
    void login_success_stores_current_user_profile() {
        DefaultLoginService service = new DefaultLoginService(
                (command, takeoverPayload) -> response(200, JsonCodec.toJson(LoginResponse.success(
                        "u1",
                        "s1",
                        "User Name",
                        "Rank",
                        "REG-001",
                        "user@haf.gr",
                        "6900000000",
                        "2026-01-01",
                        "ONLINE"))),
                (userId, sessionId, passphrase) -> {
                },
                millis -> {
                });

        LoginService.LoginResult result = service.login(new LoginService.LoginCommand("user@haf.gr", "password"));

        assertInstanceOf(LoginService.LoginResult.Success.class, result);
        assertEquals("u1", CurrentUserSession.get().userId());
        assertEquals("User Name", CurrentUserSession.get().fullName());
        assertEquals("REG-001", CurrentUserSession.get().regNumber());
        assertEquals("user@haf.gr", CurrentUserSession.get().email());
        assertEquals("6900000000", CurrentUserSession.get().telephone());
        assertEquals("2026-01-01", CurrentUserSession.get().joinedDate());
    }

    @Test
    void login_failure_clears_previous_current_user_profile() {
        CurrentUserSession.set(new UserProfileInfo(
                "old", "Old", "Rank", "REG", "2025-01-01", "old@haf.gr", "6999999999", true));
        DefaultLoginService service = new DefaultLoginService(
                (command, takeoverPayload) -> response(401, JsonCodec.toJson(LoginResponse.error("Invalid credentials"))),
                (userId, sessionId, passphrase) -> {
                },
                millis -> {
                });

        service.login(new LoginService.LoginCommand("user@haf.gr", "bad-pass"));

        assertNull(CurrentUserSession.get());
    }

    private static HttpResponse<String> response(int statusCode, String body) {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://localhost")).build();
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (name, value) -> true);
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return request.uri();
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
}
