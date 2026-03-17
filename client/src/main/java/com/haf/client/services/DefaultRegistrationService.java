package com.haf.client.services;

import com.haf.client.exceptions.RegistrationFlowException;
import com.haf.client.utils.SslContextUtils;
import com.haf.shared.crypto.CryptoECC;
import com.haf.shared.crypto.CryptoService;
import com.haf.shared.dto.EncryptedFileDTO;
import com.haf.shared.keystore.KeystoreRoot;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.requests.RegisterRequest;
import com.haf.shared.responses.RegisterResponse;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.JsonCodec;

import javax.crypto.SecretKey;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DefaultRegistrationService implements RegistrationService {

    private static final Logger LOGGER = Logger.getLogger(DefaultRegistrationService.class.getName());

    static final String REGISTRATION_INTERRUPTED_MESSAGE = "Registration interrupted.";
    static final String CONNECTION_FAILED_MESSAGE = "Connection failed. Please try again.";
    static final String REGISTRATION_FAILED_MESSAGE = "Registration failed.";

    private static final URI ADMIN_KEY_URI = URI.create("https://localhost:8443/api/v1/config/admin-key");
    private static final URI REGISTER_URI = URI.create("https://localhost:8443/api/v1/register");

    @FunctionalInterface
    interface KeyPairProvider {
        KeyPair generate() throws RegistrationFlowException;
    }

    @FunctionalInterface
    interface HttpClientProvider {
        HttpClient create() throws RegistrationFlowException;
    }

    @FunctionalInterface
    interface AdminKeyProvider {
        String fetch(HttpClient client) throws InterruptedException, RegistrationFlowException;
    }

    @FunctionalInterface
    interface RegistrationGateway {
        HttpResponse<String> send(HttpClient client, RegisterRequest request)
                throws InterruptedException, RegistrationFlowException;
    }

    @FunctionalInterface
    interface PhotoEncryptor {
        EncryptedFileDTO encrypt(File file, PublicKey adminPublicKey) throws RegistrationFlowException;
    }

    @FunctionalInterface
    interface KeystoreSaver {
        void save(KeyPair registrationKeyPair, String userId, char[] passphrase) throws RegistrationFlowException;
    }

    private final KeyPairProvider keyPairProvider;
    private final HttpClientProvider httpClientProvider;
    private final AdminKeyProvider adminKeyProvider;
    private final RegistrationGateway registrationGateway;
    private final PhotoEncryptor photoEncryptor;
    private final KeystoreSaver keystoreSaver;

    public DefaultRegistrationService() {
        this(EccKeyIO::generate,
                DefaultRegistrationService::createHttpClient,
                DefaultRegistrationService::fetchAdminPublicKeyPem,
                DefaultRegistrationService::sendRegistrationRequest,
                DefaultRegistrationService::encryptPhoto,
                DefaultRegistrationService::saveKeypairToKeystore);
    }

    DefaultRegistrationService(KeyPairProvider keyPairProvider,
            HttpClientProvider httpClientProvider,
            AdminKeyProvider adminKeyProvider,
            RegistrationGateway registrationGateway,
            PhotoEncryptor photoEncryptor,
            KeystoreSaver keystoreSaver) {
        this.keyPairProvider = keyPairProvider;
        this.httpClientProvider = httpClientProvider;
        this.adminKeyProvider = adminKeyProvider;
        this.registrationGateway = registrationGateway;
        this.photoEncryptor = photoEncryptor;
        this.keystoreSaver = keystoreSaver;
    }

    @Override
    public RegistrationResult register(RegistrationCommand command) {
        if (command == null) {
            return new RegistrationResult.Failure(CONNECTION_FAILED_MESSAGE);
        }
        try {
            KeyPair registrationKeyPair = keyPairProvider.generate();
            RegisterRequest request = buildRegistrationRequest(command, registrationKeyPair);

            HttpClient client = httpClientProvider.create();
            String adminPem = fetchAdminKeySafely(client);
            encryptPhotosIfAdminKeyAvailable(command, request, adminPem);

            HttpResponse<String> httpResponse = registrationGateway.send(client, request);
            RegisterResponse response = JsonCodec.fromJson(httpResponse.body(), RegisterResponse.class);

            if (isSuccessful(httpResponse.statusCode(), response)) {
                persistKeypair(registrationKeyPair, response.getUserId(), command.password());
                return new RegistrationResult.Success(response.getUserId());
            }

            return new RegistrationResult.Rejected(resolveRejectedMessage(response));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RegistrationResult.Failure(REGISTRATION_INTERRUPTED_MESSAGE);
        } catch (RegistrationFlowException e) {
            LOGGER.log(Level.SEVERE, "Registration failed", e);
            return new RegistrationResult.Failure(CONNECTION_FAILED_MESSAGE);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Registration failed", e);
            return new RegistrationResult.Failure(CONNECTION_FAILED_MESSAGE);
        }
    }

    private String fetchAdminKeySafely(HttpClient client) throws InterruptedException {
        try {
            return adminKeyProvider.fetch(client);
        } catch (InterruptedException interruptedException) {
            throw interruptedException;
        } catch (RegistrationFlowException ex) {
            LOGGER.log(Level.WARNING, "Failed to fetch admin public key", ex);
            return null;
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Failed to fetch admin public key", ex);
            return null;
        }
    }

    private void encryptPhotosIfAdminKeyAvailable(RegistrationCommand command, RegisterRequest request, String adminPem)
            throws RegistrationFlowException {
        if (adminPem == null || adminPem.isBlank()) {
            return;
        }

        PublicKey adminPublicKey = EccKeyIO.publicFromPem(adminPem);
        if (command.idPhoto() != null) {
            request.setIdPhoto(photoEncryptor.encrypt(command.idPhoto(), adminPublicKey));
        }
        if (command.selfiePhoto() != null) {
            request.setSelfiePhoto(photoEncryptor.encrypt(command.selfiePhoto(), adminPublicKey));
        }
    }

    private void persistKeypair(KeyPair registrationKeyPair, String userId, String password) {
        try {
            char[] passphrase = password == null ? new char[0] : password.toCharArray();
            keystoreSaver.save(registrationKeyPair, userId, passphrase);
        } catch (RegistrationFlowException e) {
            LOGGER.log(Level.SEVERE, "Failed to save Keystore after successful registration", e);
        }
    }

    private static RegisterRequest buildRegistrationRequest(RegistrationCommand command, KeyPair registrationKeyPair) {
        RegisterRequest request = new RegisterRequest();
        request.setFullName(command.name());
        request.setRegNumber(command.regNum());
        request.setIdNumber(command.idNum());
        request.setRank(command.rank());
        request.setTelephone(command.phone());
        request.setEmail(command.email());
        request.setPassword(command.password());
        request.setPublicKeyPem(EccKeyIO.publicPem(registrationKeyPair.getPublic()));
        request.setPublicKeyFingerprint(FingerprintUtil.sha256Hex(EccKeyIO.publicDer(registrationKeyPair.getPublic())));
        return request;
    }

    private static boolean isSuccessful(int statusCode, RegisterResponse response) {
        return (statusCode == 200 || statusCode == 201) && response != null && response.getError() == null;
    }

    private static String resolveRejectedMessage(RegisterResponse response) {
        if (response != null && response.getError() != null) {
            return response.getError();
        }
        return REGISTRATION_FAILED_MESSAGE;
    }

    private static HttpClient createHttpClient() throws RegistrationFlowException {
        try {
            return HttpClient.newBuilder()
                    .sslContext(SslContextUtils.getTrustingSslContext())
                    .build();
        } catch (Exception ex) {
            throw new RegistrationFlowException("Failed to create HTTP client for registration", ex);
        }
    }

    private static String fetchAdminPublicKeyPem(HttpClient client)
            throws InterruptedException, RegistrationFlowException {
        try {
            HttpRequest adminKeyRequest = HttpRequest.newBuilder()
                    .uri(ADMIN_KEY_URI)
                    .GET()
                    .build();
            HttpResponse<String> adminKeyResponse = client.send(adminKeyRequest, HttpResponse.BodyHandlers.ofString());

            if (adminKeyResponse.statusCode() != 200) {
                return null;
            }

            String body = adminKeyResponse.body();
            int start = body.indexOf('"', body.indexOf(':') + 1) + 1;
            int end = body.lastIndexOf('"');
            if (start > 0 && end > start) {
                return body.substring(start, end).replace("\\n", "\n");
            }
            return null;
        } catch (InterruptedException interruptedException) {
            throw interruptedException;
        } catch (Exception ex) {
            throw new RegistrationFlowException("Failed to fetch admin public key", ex);
        }
    }

    private static HttpResponse<String> sendRegistrationRequest(HttpClient client, RegisterRequest request)
            throws InterruptedException, RegistrationFlowException {
        try {
            String json = JsonCodec.toJson(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(REGISTER_URI)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            return client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException interruptedException) {
            throw interruptedException;
        } catch (Exception ex) {
            throw new RegistrationFlowException("Failed to submit registration request", ex);
        }
    }

    private static EncryptedFileDTO encryptPhoto(File file, PublicKey adminPublicKey) throws RegistrationFlowException {
        try {
            byte[] plaintext = Files.readAllBytes(file.toPath());
            KeyPair ephemeral = EccKeyIO.generate();
            SecretKey aesKey = CryptoECC.generateAndDeriveAesKey(ephemeral.getPrivate(), adminPublicKey);
            byte[] iv = CryptoService.generateIv();
            byte[] cipherTextAndTag = CryptoService.encryptAesGcm(plaintext, aesKey, iv, null);
            int tagLen = 16;
            int ctLen = cipherTextAndTag.length - tagLen;
            byte[] cipherText = java.util.Arrays.copyOfRange(cipherTextAndTag, 0, ctLen);
            byte[] authTag = java.util.Arrays.copyOfRange(cipherTextAndTag, ctLen, cipherTextAndTag.length);

            EncryptedFileDTO dto = new EncryptedFileDTO();
            dto.setCiphertextB64(Base64.getEncoder().encodeToString(cipherText));
            dto.setIvB64(Base64.getEncoder().encodeToString(iv));
            dto.setTagB64(Base64.getEncoder().encodeToString(authTag));
            dto.setEphemeralPublicB64(Base64.getEncoder().encodeToString(EccKeyIO.publicDer(ephemeral.getPublic())));
            dto.setContentType("image/jpeg");
            dto.setOriginalSize(plaintext.length);
            return dto;
        } catch (Exception ex) {
            throw new RegistrationFlowException("Failed to encrypt registration photo", ex);
        }
    }

    private static void saveKeypairToKeystore(KeyPair registrationKeyPair, String userId, char[] passphrase)
            throws RegistrationFlowException {
        try {
            Path root = getOrCreateKeystoreRoot(userId);
            UserKeystore keystore = new UserKeystore(root);
            String keyId = UserKeystore.todayKeyId();
            keystore.saveKeypair(keyId, registrationKeyPair, passphrase);
        } catch (RegistrationFlowException registrationFlowException) {
            throw registrationFlowException;
        } catch (Exception ex) {
            throw new RegistrationFlowException("Failed to persist registration keypair", ex);
        }
    }

    private static Path getOrCreateKeystoreRoot(String userId) throws RegistrationFlowException {
        try {
            Path root = KeystoreRoot.preferred(userId);
            FilePerms.ensureDir700(root);
            return root;
        } catch (Exception e) {
            try {
                Path root = KeystoreRoot.userFallback(userId);
                FilePerms.ensureDir700(root);
                return root;
            } catch (Exception ex) {
                throw new RegistrationFlowException("Failed to initialize keystore root", ex);
            }
        }
    }
}
