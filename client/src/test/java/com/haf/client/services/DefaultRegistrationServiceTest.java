package com.haf.client.services;

import com.haf.shared.dto.EncryptedFileDTO;
import com.haf.shared.responses.RegisterResponse;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;
import javax.net.ssl.SSLSession;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultRegistrationServiceTest {

    @Test
    void register_happy_path_returns_success_and_persists_keystore() {
        AtomicInteger saveCalls = new AtomicInteger();
        AtomicReference<String> savedUserId = new AtomicReference<>();
        AtomicReference<char[]> savedPassphrase = new AtomicReference<>();

        DefaultRegistrationService service = new DefaultRegistrationService(
                EccKeyIO::generate,
                HttpClient::newHttpClient,
                client -> "",
                (client, request) -> response(201, JsonCodec.toJson(RegisterResponse.success("user-1"))),
                (file, adminPublicKey) -> new EncryptedFileDTO(),
                (registrationKeyPair, userId, passphrase) -> {
                    saveCalls.incrementAndGet();
                    savedUserId.set(userId);
                    savedPassphrase.set(passphrase);
                });

        RegistrationService.RegistrationResult result = service.register(command(null, null));

        RegistrationService.RegistrationResult.Success success = assertInstanceOf(
                RegistrationService.RegistrationResult.Success.class, result);
        assertEquals("user-1", success.userId());
        assertEquals(1, saveCalls.get());
        assertEquals("user-1", savedUserId.get());
        assertArrayEquals("pass123".toCharArray(), savedPassphrase.get());
    }

    @Test
    void register_backend_rejection_returns_rejected() {
        AtomicInteger saveCalls = new AtomicInteger();

        DefaultRegistrationService service = new DefaultRegistrationService(
                EccKeyIO::generate,
                HttpClient::newHttpClient,
                client -> "",
                (client, request) -> response(400, JsonCodec.toJson(RegisterResponse.error("Email already exists"))),
                (file, adminPublicKey) -> new EncryptedFileDTO(),
                (registrationKeyPair, userId, passphrase) -> saveCalls.incrementAndGet());

        RegistrationService.RegistrationResult result = service.register(command(null, null));

        RegistrationService.RegistrationResult.Rejected rejected = assertInstanceOf(
                RegistrationService.RegistrationResult.Rejected.class, result);
        assertEquals("Email already exists", rejected.message());
        assertEquals(0, saveCalls.get());
    }

    @Test
    void register_admin_key_fetch_failure_is_non_fatal() throws Exception {
        AtomicInteger saveCalls = new AtomicInteger();
        AtomicInteger encryptCalls = new AtomicInteger();

        Path idPath = Files.createTempFile("haf-id-", ".jpg");
        Path selfiePath = Files.createTempFile("haf-selfie-", ".jpg");
        Files.write(idPath, new byte[] { 1, 2, 3 });
        Files.write(selfiePath, new byte[] { 4, 5, 6 });

        DefaultRegistrationService service = new DefaultRegistrationService(
                EccKeyIO::generate,
                HttpClient::newHttpClient,
                client -> {
                    throw new RuntimeException("admin key endpoint unavailable");
                },
                (client, request) -> {
                    assertNull(request.getIdPhoto());
                    assertNull(request.getSelfiePhoto());
                    return response(201, JsonCodec.toJson(RegisterResponse.success("user-2")));
                },
                (file, adminPublicKey) -> {
                    encryptCalls.incrementAndGet();
                    return new EncryptedFileDTO();
                },
                (registrationKeyPair, userId, passphrase) -> saveCalls.incrementAndGet());

        RegistrationService.RegistrationResult result = service.register(command(idPath.toFile(), selfiePath.toFile()));

        RegistrationService.RegistrationResult.Success success = assertInstanceOf(
                RegistrationService.RegistrationResult.Success.class, result);
        assertEquals("user-2", success.userId());
        assertEquals(0, encryptCalls.get());
        assertEquals(1, saveCalls.get());
    }

    @Test
    void register_exception_path_returns_failure() {
        DefaultRegistrationService service = new DefaultRegistrationService(
                EccKeyIO::generate,
                HttpClient::newHttpClient,
                client -> "",
                (client, request) -> {
                    throw new RuntimeException("transport failure");
                },
                (file, adminPublicKey) -> new EncryptedFileDTO(),
                (registrationKeyPair, userId, passphrase) -> {
                });

        RegistrationService.RegistrationResult result = service.register(command(null, null));

        RegistrationService.RegistrationResult.Failure failure = assertInstanceOf(
                RegistrationService.RegistrationResult.Failure.class, result);
        assertEquals("Connection failed. Please try again.", failure.message());
    }

    private static RegistrationService.RegistrationCommand command(File idPhoto, File selfiePhoto) {
        return new RegistrationService.RegistrationCommand(
                "Test User",
                "RN12345",
                "ID67890",
                "SMINIAS",
                "+306900000000",
                "user@haf.gr",
                "pass123",
                idPhoto,
                selfiePhoto);
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
