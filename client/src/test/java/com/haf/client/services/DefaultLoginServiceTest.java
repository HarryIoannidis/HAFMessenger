package com.haf.client.services;

import com.haf.shared.responses.LoginResponse;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DefaultLoginServiceTest {

    @Test
    void login_success_on_first_attempt() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger bootstrapCalls = new AtomicInteger();

        DefaultLoginService service = new DefaultLoginService(
                command -> {
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
                command -> {
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
                command -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("network down");
                },
                (userId, sessionId, passphrase) -> {
                },
                millis -> sleepCalls.incrementAndGet());

        LoginService.LoginResult result = service.login(new LoginService.LoginCommand("user@haf.gr", "password"));

        LoginService.LoginResult.Failure failure = assertInstanceOf(LoginService.LoginResult.Failure.class, result);
        assertEquals(DefaultLoginService.CONNECTION_FAILED_MESSAGE, failure.message());
        assertEquals(DefaultLoginService.MAX_LOGIN_ATTEMPTS, attempts.get());
        assertEquals(DefaultLoginService.MAX_LOGIN_ATTEMPTS - 1, sleepCalls.get());
    }

    @Test
    void login_secure_session_init_failure_retries_then_fails() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger bootstrapCalls = new AtomicInteger();
        AtomicInteger sleepCalls = new AtomicInteger();

        DefaultLoginService service = new DefaultLoginService(
                command -> {
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
        assertEquals(DefaultLoginService.SECURE_SESSION_FAILED_MESSAGE, failure.message());
        assertEquals(DefaultLoginService.MAX_LOGIN_ATTEMPTS, attempts.get());
        assertEquals(DefaultLoginService.MAX_LOGIN_ATTEMPTS, bootstrapCalls.get());
        assertEquals(DefaultLoginService.MAX_LOGIN_ATTEMPTS - 1, sleepCalls.get());
    }

    @Test
    void login_backend_rejection_returns_rejected_without_retry() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger bootstrapCalls = new AtomicInteger();

        DefaultLoginService service = new DefaultLoginService(
                command -> {
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
                command -> {
                    attempts.incrementAndGet();
                    return response(409, JsonCodec.toJson(LoginResponse.error("Account is already logged in.")));
                },
                (userId, sessionId, passphrase) -> bootstrapCalls.incrementAndGet(),
                millis -> {
                });

        LoginService.LoginResult result = service.login(new LoginService.LoginCommand("user@haf.gr", "password"));

        LoginService.LoginResult.Rejected rejected = assertInstanceOf(LoginService.LoginResult.Rejected.class, result);
        assertEquals("Account is already logged in.", rejected.message());
        assertEquals(1, attempts.get());
        assertEquals(0, bootstrapCalls.get());
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
