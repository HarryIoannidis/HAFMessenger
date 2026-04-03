package com.haf.client.services;

import com.haf.client.exceptions.RegistrationFlowException;
import com.haf.client.utils.ClientRuntimeConfig;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles client registration flow, key generation, and payload submission.
 */
public class DefaultRegistrationService implements RegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultRegistrationService.class);

    @FunctionalInterface
    interface KeyPairProvider {
        /**
         * Generates registration key pair.
         *
         * @return generated key pair
         * @throws RegistrationFlowException when key generation fails
         */
        KeyPair generate() throws RegistrationFlowException;
    }

    @FunctionalInterface
    interface HttpClientProvider {
        /**
         * Creates HTTP client configured for registration requests.
         *
         * @return configured HTTP client
         * @throws RegistrationFlowException when client creation fails
         */
        HttpClient create() throws RegistrationFlowException;
    }

    @FunctionalInterface
    interface AdminKeyProvider {
        /**
         * Fetches admin public key PEM used for registration photo encryption.
         *
         * @param client HTTP client to use
         * @return admin public key PEM, or {@code null} when unavailable
         * @throws InterruptedException when call is interrupted
         * @throws RegistrationFlowException when fetch/parsing fails
         */
        String fetch(HttpClient client) throws InterruptedException, RegistrationFlowException;
    }

    @FunctionalInterface
    interface RegistrationGateway {
        /**
         * Sends registration request payload to backend.
         *
         * @param client configured HTTP client
         * @param request registration request payload
         * @return HTTP response with registration result
         * @throws InterruptedException when request is interrupted
         * @throws RegistrationFlowException when send fails
         */
        HttpResponse<String> send(HttpClient client, RegisterRequest request)
                throws InterruptedException, RegistrationFlowException;
    }

    @FunctionalInterface
    interface PhotoEncryptor {
        /**
         * Encrypts a registration photo using admin public key.
         *
         * @param file photo file to encrypt
         * @param adminPublicKey admin public key for hybrid encryption
         * @return encrypted-file DTO payload
         * @throws RegistrationFlowException when encryption fails
         */
        EncryptedFileDTO encrypt(File file, PublicKey adminPublicKey) throws RegistrationFlowException;
    }

    @FunctionalInterface
    interface KeystoreSaver {
        /**
         * Persists generated registration key pair to local keystore.
         *
         * @param registrationKeyPair generated key pair
         * @param userId registered user id
         * @param passphrase passphrase used for key protection
         * @throws RegistrationFlowException when persistence fails
         */
        void save(KeyPair registrationKeyPair, String userId, char[] passphrase) throws RegistrationFlowException;
    }

    private final KeyPairProvider keyPairProvider;
    private final HttpClientProvider httpClientProvider;
    private final AdminKeyProvider adminKeyProvider;
    private final RegistrationGateway registrationGateway;
    private final PhotoEncryptor photoEncryptor;
    private final KeystoreSaver keystoreSaver;

    /**
     * Creates registration service with default crypto/http/keystore behavior.
     */
    public DefaultRegistrationService() {
        this(EccKeyIO::generate,
                DefaultRegistrationService::createHttpClient,
                DefaultRegistrationService::fetchAdminPublicKeyPem,
                DefaultRegistrationService::sendRegistrationRequest,
                DefaultRegistrationService::encryptPhoto,
                DefaultRegistrationService::saveKeypairToKeystore);
    }

    /**
     * Creates registration service with injectable dependencies.
     *
     * @param keyPairProvider registration keypair provider
     * @param httpClientProvider HTTP client factory
     * @param adminKeyProvider admin key fetch strategy
     * @param registrationGateway registration submission gateway
     * @param photoEncryptor photo encryption strategy
     * @param keystoreSaver local keystore persistence strategy
     */
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

    /**
     * Executes full registration flow: key generation, optional photo encryption,
     * backend submission, and local key persistence.
     *
     * @param command registration command containing form payload and photos
     * @return registration result representing success, rejection, or failure
     */
    @Override
    public RegistrationResult register(RegistrationCommand command) {
        if (command == null) {
            return new RegistrationResult.Failure("Connection failed. Please try again.");
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
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return new RegistrationResult.Failure("Registration interrupted.");
        } catch (RegistrationFlowException e) {
            LOGGER.error( "Registration failed", e);
            return new RegistrationResult.Failure("Connection failed. Please try again.");
        } catch (RuntimeException e) {
            LOGGER.error( "Registration failed", e);
            return new RegistrationResult.Failure("Connection failed. Please try again.");
        }
    }

    /**
     * Fetches admin key while converting failures into warning logs and null result.
     *
     * @param client HTTP client used for admin key fetch
     * @return admin PEM value, or {@code null} when unavailable
     * @throws InterruptedException when thread interruption occurs
     */
    private String fetchAdminKeySafely(HttpClient client) throws InterruptedException {
        try {
            return adminKeyProvider.fetch(client);
        } catch (InterruptedException interruptedException) {
            throw interruptedException;
        } catch (RegistrationFlowException ex) {
            LOGGER.warn( "Failed to fetch admin public key", ex);
            return null;
        } catch (RuntimeException ex) {
            LOGGER.warn( "Failed to fetch admin public key", ex);
            return null;
        }
    }

    /**
     * Encrypts optional registration photos when admin public key is available.
     *
     * @param command registration command containing photo files
     * @param request request DTO that receives encrypted photo payloads
     * @param adminPem admin public key PEM text
     * @throws RegistrationFlowException when PEM parsing or photo encryption fails
     */
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

    /**
     * Persists generated keypair locally after successful backend registration.
     *
     * @param registrationKeyPair generated registration keypair
     * @param userId backend-assigned user id
     * @param password user password used as passphrase
     */
    private void persistKeypair(KeyPair registrationKeyPair, String userId, String password) {
        try {
            char[] passphrase = password == null ? new char[0] : password.toCharArray();
            keystoreSaver.save(registrationKeyPair, userId, passphrase);
        } catch (RegistrationFlowException e) {
            LOGGER.error( "Failed to save Keystore after successful registration", e);
        }
    }

    /**
     * Maps registration command + public key into backend register request DTO.
     *
     * @param command registration command payload
     * @param registrationKeyPair generated registration keypair
     * @return register request DTO ready for submission
     */
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

    /**
     * Determines whether HTTP/register payload pair indicates successful
     * registration.
     *
     * @param statusCode HTTP response status
     * @param response parsed register response body
     * @return {@code true} when registration succeeded
     */
    private static boolean isSuccessful(int statusCode, RegisterResponse response) {
        return (statusCode == 200 || statusCode == 201) && response != null && response.getError() == null;
    }

    /**
     * Extracts rejection message from register response.
     *
     * @param response parsed register response
     * @return rejection reason or default registration-failed message
     */
    private static String resolveRejectedMessage(RegisterResponse response) {
        if (response != null && response.getError() != null) {
            return response.getError();
        }
        return "Registration failed.";
    }

    /**
     * Creates TLS-enabled HTTP client used for registration API calls.
     *
     * @return configured HTTP client
     * @throws RegistrationFlowException when SSL context initialization fails
     */
    private static HttpClient createHttpClient() throws RegistrationFlowException {
        ClientRuntimeConfig runtimeConfig = ClientRuntimeConfig.load();
        try {
            return HttpClient.newBuilder()
                    .sslContext(SslContextUtils.getSslContextForMode(runtimeConfig.isDev()))
                    .sslParameters(SslContextUtils.createHttpsSslParameters())
                    .build();
        } catch (Exception ex) {
            throw new RegistrationFlowException("Failed to create HTTP client for registration", ex);
        }
    }

    /**
     * Fetches admin public key PEM from config endpoint.
     *
     * @param client configured HTTP client
     * @return admin public key PEM, or {@code null} when endpoint does not return key
     * @throws InterruptedException when request is interrupted
     * @throws RegistrationFlowException when fetch/parsing fails
     */
    private static String fetchAdminPublicKeyPem(HttpClient client)
            throws InterruptedException, RegistrationFlowException {
        ClientRuntimeConfig runtimeConfig = ClientRuntimeConfig.load();
        try {
            HttpRequest adminKeyRequest = HttpRequest.newBuilder()
                    .uri(resolveAdminKeyUri(runtimeConfig))
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

    /**
     * Sends registration request to backend endpoint.
     *
     * @param client configured HTTP client
     * @param request register request payload
     * @return HTTP response from registration endpoint
     * @throws InterruptedException when request is interrupted
     * @throws RegistrationFlowException when submission fails
     */
    private static HttpResponse<String> sendRegistrationRequest(HttpClient client, RegisterRequest request)
            throws InterruptedException, RegistrationFlowException {
        ClientRuntimeConfig runtimeConfig = ClientRuntimeConfig.load();
        try {
            String json = JsonCodec.toJson(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(resolveRegisterUri(runtimeConfig))
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

    static URI resolveServerBaseUri(ClientRuntimeConfig runtimeConfig) {
        return runtimeConfig.serverBaseUri();
    }

    static URI resolveAdminKeyUri(ClientRuntimeConfig runtimeConfig) {
        return resolveServerBaseUri(runtimeConfig).resolve("/api/v1/config/admin-key");
    }

    static URI resolveRegisterUri(ClientRuntimeConfig runtimeConfig) {
        return resolveServerBaseUri(runtimeConfig).resolve("/api/v1/register");
    }

    /**
     * Encrypts registration photo with ephemeral ECDH + AES-GCM payload.
     *
     * @param file plaintext photo file
     * @param adminPublicKey admin public key used for key agreement
     * @return encrypted file DTO sent to backend
     * @throws RegistrationFlowException when encryption process fails
     */
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

    /**
     * Saves registration keypair into user's keystore root directory.
     *
     * @param registrationKeyPair generated registration keypair
     * @param userId registered user id
     * @param passphrase passphrase used to encrypt private key
     * @throws RegistrationFlowException when persistence fails
     */
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

    /**
     * Resolves keystore root path (preferred, then fallback) and ensures secure
     * directory permissions.
     *
     * @param userId user id used to namespace keystore path
     * @return resolved keystore root path
     * @throws RegistrationFlowException when neither preferred nor fallback root can be initialized
     */
    private static Path getOrCreateKeystoreRoot(String userId) throws RegistrationFlowException {
        try {
            Path root = KeystoreRoot.preferred(userId);
            FilePerms.ensureDir700(root);
            return root;
        } catch (Exception _) {
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
