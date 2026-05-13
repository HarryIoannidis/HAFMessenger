package com.haf.client.network;

import com.haf.client.exceptions.HttpCommunicationException;
import com.haf.client.utils.AuthErrorClassifier;
import com.haf.shared.constants.AttachmentConstants;
import com.haf.shared.crypto.MessageEncryptor;
import com.haf.shared.crypto.MessageSignatureService;
import com.haf.shared.requests.AttachmentBindRequest;
import com.haf.shared.responses.AttachmentBindResponse;
import com.haf.shared.responses.AttachmentChunkResponse;
import com.haf.shared.requests.AttachmentCompleteRequest;
import com.haf.shared.responses.AttachmentCompleteResponse;
import com.haf.shared.requests.AttachmentInitRequest;
import com.haf.shared.responses.AttachmentInitResponse;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.responses.ApiErrorResponse;
import com.haf.shared.responses.MessagingPolicyResponse;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.MessageValidationException;
import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.MessageValidator;
import com.haf.shared.websocket.RealtimeErrorCode;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;

/**
 * Encrypts and submits outbound messages and attachment API calls.
 */
public class DefaultMessageSender implements MessageSender {
    private final KeyProvider keyProvider;
    private final ClockProvider clockProvider;
    private final AuthHttpClient authHttpClient;
    private final RealtimeTransport realtimeTransport;

    /**
     * Creates a DefaultMessageSender with the specified dependencies.
     *
     * @param keyProvider      the key provider for retrieving recipient public keys
     * @param clockProvider    the clock provider for deterministic timestamps
     * @param authHttpClient    authenticated HTTP adapter for REST-only operations
     * @param realtimeTransport WSS transport for live message sends
     */
    public DefaultMessageSender(KeyProvider keyProvider, ClockProvider clockProvider,
            AuthHttpClient authHttpClient, RealtimeTransport realtimeTransport) {
        this.keyProvider = keyProvider;
        this.clockProvider = clockProvider;
        this.authHttpClient = authHttpClient;
        this.realtimeTransport = Objects.requireNonNull(realtimeTransport, "realtimeTransport");
    }

    /**
     * Sends a message to the specified recipient.
     *
     * @param payload     the plaintext bytes to send
     * @param recipientId the recipient's identifier
     * @param contentType the MIME content type of the payload
     * @param ttlSeconds  the time-to-live in seconds
     * @throws MessageValidationException if the message is not valid
     * @throws KeyNotFoundException       if the key is not found
     * @throws IOException                if the message cannot be sent
     */
    @Override
    public void sendMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds)
            throws MessageValidationException, KeyNotFoundException, IOException {
        sendMessageWithResult(payload, recipientId, contentType, ttlSeconds);
    }

    /**
     * Sends a plaintext payload and returns metadata about the created server
     * envelope.
     *
     * @param payload     plaintext bytes to send
     * @param recipientId recipient identifier
     * @param contentType payload MIME content type
     * @param ttlSeconds  time-to-live for the encrypted envelope
     * @return send result containing envelope id and expiry metadata
     * @throws MessageValidationException if generated encrypted envelope fails
     *                                    validation
     * @throws KeyNotFoundException       if recipient key cannot be resolved
     * @throws IOException                if encryption or network submission fails
     */
    @Override
    public SendResult sendMessageWithResult(byte[] payload, String recipientId, String contentType, long ttlSeconds)
            throws MessageValidationException, KeyNotFoundException, IOException {
        PreparedEncryptedMessage prepared = prepareEncryptedMessage(payload, recipientId, contentType, ttlSeconds);
        try {
            return sendEncryptedMessageWithFingerprint(prepared.encryptedMessage(), prepared.recipientFingerprint());
        } catch (IOException firstFailure) {
            if (!isStaleRecipientKeyFailure(firstFailure)) {
                throw firstFailure;
            }
            PreparedEncryptedMessage retried = prepareEncryptedMessage(payload, recipientId, contentType, ttlSeconds);
            return sendEncryptedMessageWithFingerprint(retried.encryptedMessage(), retried.recipientFingerprint());
        }
    }

    /**
     * Encrypts plaintext payload and builds a validated {@link EncryptedMessage}
     * envelope.
     *
     * @param payload     plaintext bytes to encrypt
     * @param recipientId recipient identifier
     * @param contentType payload MIME content type
     * @param ttlSeconds  envelope time-to-live in seconds
     * @return encrypted message envelope ready to be sent
     * @throws MessageValidationException if generated envelope violates validation
     *                                    rules
     * @throws KeyNotFoundException       if recipient key cannot be resolved
     * @throws IOException                if encryption fails unexpectedly
     */
    @Override
    public EncryptedMessage encryptMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds)
            throws MessageValidationException, KeyNotFoundException, IOException {
        return prepareEncryptedMessage(payload, recipientId, contentType, ttlSeconds).encryptedMessage();
    }

    /**
     * Encrypts plaintext payload and returns envelope plus recipient key
     * fingerprint used during encryption.
     *
     * @param payload     plaintext bytes to encrypt
     * @param recipientId recipient identifier
     * @param contentType payload MIME content type
     * @param ttlSeconds  envelope time-to-live in seconds
     * @return prepared encrypted message with recipient key fingerprint
     * @throws MessageValidationException if generated envelope violates validation
     *                                    rules
     * @throws KeyNotFoundException       if recipient key cannot be resolved
     * @throws IOException                if encryption fails unexpectedly
     */
    private PreparedEncryptedMessage prepareEncryptedMessage(
            byte[] payload,
            String recipientId,
            String contentType,
            long ttlSeconds) throws MessageValidationException, KeyNotFoundException, IOException {

        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        if (recipientId == null || recipientId.isBlank()) {
            throw new IllegalArgumentException("RecipientId cannot be null or empty");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("ContentType cannot be null or empty");
        }

        try {
            String senderId = keyProvider.getSenderId();
            PrivateKey senderSigningPrivateKey = keyProvider.getSenderSigningPrivateKey();
            String senderSigningFingerprint = keyProvider.getSenderSigningKeyFingerprint();
            PublicKey recipientPublicKey = keyProvider.getRecipientPublicKey(recipientId);
            MessageEncryptor encryptor = new MessageEncryptor(recipientPublicKey, senderId, recipientId, clockProvider);
            EncryptedMessage encryptedMessage = encryptor.encrypt(payload, contentType, ttlSeconds);
            MessageSignatureService.sign(encryptedMessage, senderSigningPrivateKey, senderSigningFingerprint);
            String recipientFingerprint = FingerprintUtil.sha256Hex(EccKeyIO.publicDer(recipientPublicKey));

            List<MessageValidator.ErrorCode> errors = MessageValidator.validateOrCollectErrors(encryptedMessage);
            if (!errors.isEmpty()) {
                throw new MessageValidationException(errors);
            }
            return new PreparedEncryptedMessage(encryptedMessage, recipientFingerprint);
        } catch (KeyNotFoundException | MessageValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to encrypt message", e);
        }
    }

    /**
     * Sends an already-encrypted message envelope to the server.
     *
     * @param encryptedMessage encrypted message envelope
     * @return send result containing envelope id and expiration timestamp
     * @throws IOException if transport fails or server returns invalid response
     *                     payload
     */
    @Override
    public SendResult sendEncryptedMessage(EncryptedMessage encryptedMessage) throws IOException {
        return sendEncryptedMessageWithFingerprint(encryptedMessage, null);
    }

    /**
     * Sends an encrypted message with optional recipient fingerprint header.
     *
     * @param encryptedMessage        encrypted message envelope
     * @param recipientKeyFingerprint optional recipient key fingerprint
     * @return send result containing envelope id and expiration timestamp
     * @throws IOException if transport fails or server returns invalid response
     */
    private SendResult sendEncryptedMessageWithFingerprint(
            EncryptedMessage encryptedMessage,
            String recipientKeyFingerprint) throws IOException {
        if (encryptedMessage == null) {
            throw new IllegalArgumentException("Encrypted message cannot be null");
        }

        return realtimeTransport.sendMessage(encryptedMessage, recipientKeyFingerprint);
    }

    /**
     * Detects stale-recipient-key failures returned by message ingress.
     *
     * @param error send failure candidate
     * @return {@code true} when failure indicates recipient key mismatch
     */
    private static boolean isStaleRecipientKeyFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof RealtimeClientTransport.RealtimeException realtimeException
                    && realtimeException.codeEnum() == RealtimeErrorCode.STALE_RECIPIENT_KEY) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Prepared encrypted message payload with recipient key fingerprint used for
     * encryption.
     *
     * @param encryptedMessage     encrypted envelope
     * @param recipientFingerprint recipient key fingerprint
     */
    private record PreparedEncryptedMessage(EncryptedMessage encryptedMessage, String recipientFingerprint) {
    }

    /**
     * Fetches current messaging policy from the server.
     *
     * @return messaging policy response
     * @throws IOException if request or response decoding fails
     */
    @Override
    public MessagingPolicyResponse fetchMessagingPolicy() throws IOException {
        return decodeResponse(authHttpClient.getAuthenticated("/api/v1/config/messaging"),
                MessagingPolicyResponse.class);
    }

    /**
     * Initializes a multipart/chunked attachment upload session.
     *
     * @param request attachment initialization request payload
     * @return attachment initialization response
     * @throws IOException if request or response decoding fails
     */
    @Override
    public AttachmentInitResponse initAttachmentUpload(AttachmentInitRequest request) throws IOException {
        String body = JsonCodec.toJson(request);
        return decodeResponse(authHttpClient.postAuthenticated("/api/v1/attachments/" + "init", body),
                AttachmentInitResponse.class);
    }

    /**
     * Uploads a single attachment chunk to an existing upload session.
     *
     * @param attachmentId server-provided attachment upload id
     * @param request      chunk payload request
     * @return chunk upload response
     * @throws IOException if request or response decoding fails
     */
    @Override
    public AttachmentChunkResponse uploadAttachmentChunk(String attachmentId, int chunkIndex, byte[] chunkBytes)
            throws IOException {
        return decodeResponse(
                authHttpClient.postAuthenticatedBytes(
                        "/api/v1/attachments/" + attachmentId + "/chunk",
                        chunkBytes,
                        AttachmentConstants.APPLICATION_OCTET_STREAM,
                        Map.of(AttachmentConstants.HEADER_CHUNK_INDEX, String.valueOf(chunkIndex))),
                AttachmentChunkResponse.class);
    }

    /**
     * Completes an attachment upload after all chunks are uploaded.
     *
     * @param attachmentId server-provided attachment upload id
     * @param request      completion payload
     * @return upload completion response
     * @throws IOException if request or response decoding fails
     */
    @Override
    public AttachmentCompleteResponse completeAttachmentUpload(String attachmentId, AttachmentCompleteRequest request)
            throws IOException {
        String body = JsonCodec.toJson(request);
        return decodeResponse(
                authHttpClient.postAuthenticated("/api/v1/attachments/" + attachmentId + "/complete", body),
                AttachmentCompleteResponse.class);
    }

    /**
     * Binds a completed attachment upload to a message payload.
     *
     * @param attachmentId server-provided attachment upload id
     * @param request      bind request payload
     * @return bind response
     * @throws IOException if request or response decoding fails
     */
    @Override
    public AttachmentBindResponse bindAttachmentUpload(String attachmentId, AttachmentBindRequest request)
            throws IOException {
        String body = JsonCodec.toJson(request);
        return decodeResponse(authHttpClient.postAuthenticated("/api/v1/attachments/" + attachmentId + "/bind", body),
                AttachmentBindResponse.class);
    }

    /**
     * Downloads attachment metadata/content wrapper by attachment id.
     *
     * @param attachmentId attachment identifier
     * @return attachment download response
     * @throws IOException if request or response decoding fails
     */
    @Override
    public AttachmentDownload downloadAttachment(String attachmentId) throws IOException {
        try {
            HttpResponse<byte[]> response = authHttpClient.getAuthenticatedBytes("/api/v1/attachments/" + attachmentId)
                    .join();
            byte[] body = response.body() == null ? new byte[0] : response.body();
            String resolvedAttachmentId = header(response, AttachmentConstants.HEADER_ATTACHMENT_ID, attachmentId);
            String contentType = header(response, AttachmentConstants.HEADER_ATTACHMENT_CONTENT_TYPE,
                    AttachmentConstants.CONTENT_TYPE_ENCRYPTED_BLOB);
            long encryptedSize = longHeader(response, AttachmentConstants.HEADER_ATTACHMENT_ENCRYPTED_SIZE,
                    body.length);
            int chunkCount = intHeader(response, AttachmentConstants.HEADER_ATTACHMENT_CHUNK_COUNT, 0);
            return new AttachmentDownload(resolvedAttachmentId, contentType, encryptedSize, chunkCount, body);
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to download attachment", cause != null ? cause : ex);
        } catch (Exception ex) {
            throw new IOException("Failed to download attachment", ex);
        }
    }

    /**
     * Resolves a JSON HTTP response future and decodes it into the requested
     * response type.
     *
     * @param future asynchronous JSON response future
     * @param type   target response type
     * @param <T>    response DTO type
     * @return decoded response object
     * @throws IOException if the request fails or JSON cannot be decoded
     */
    private <T> T decodeResponse(java.util.concurrent.CompletableFuture<String> future, Class<T> type)
            throws IOException {
        try {
            String response = future.join();
            if (response == null || response.isBlank()) {
                throw new IOException("Server returned an empty response");
            }
            return JsonCodec.fromJson(response, type);
        } catch (HttpCommunicationException ex) {
            throw toIoException(ex);
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof HttpCommunicationException communicationException) {
                throw toIoException(communicationException);
            }
            throw new IOException("Failed to decode server response: " + abbreviateBody(causeMessage(cause)),
                    cause != null ? cause : ex);
        } catch (Exception ex) {
            throw new IOException("Failed to decode server response: " + abbreviateBody(causeMessage(ex)), ex);
        }
    }

    private static IOException toIoException(HttpCommunicationException communicationException) {
        int status = communicationException.getStatusCode();
        String parsedError = AuthErrorClassifier.parseMessage(communicationException.getResponseBody());
        String message = parsedError == null || parsedError.isBlank()
                ? abbreviateBody(causeMessage(communicationException))
                : abbreviateBody(parsedError);
        Long retryAfterSeconds = parseRetryAfterSeconds(communicationException.getResponseBody());
        if (retryAfterSeconds != null && retryAfterSeconds > 0L) {
            message = (message == null || message.isBlank() ? "rate limit" : message)
                    + " (retry after " + retryAfterSeconds + "s)";
        }
        if (message == null || message.isBlank()) {
            message = "HTTP request failed";
        }
        return new IOException("HTTP " + status + " from server: " + message, communicationException);
    }

    private static Long parseRetryAfterSeconds(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            ApiErrorResponse payload = JsonCodec.fromJson(responseBody, ApiErrorResponse.class);
            if (payload == null || payload.getRetryAfterSeconds() == null) {
                return null;
            }
            long retryAfter = payload.getRetryAfterSeconds();
            return retryAfter > 0L ? retryAfter : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String causeMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private static String abbreviateBody(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String singleLine = text.replace("\r", " ").replace("\n", " ").trim();
        int maxLen = 180;
        if (singleLine.length() <= maxLen) {
            return singleLine;
        }
        return singleLine.substring(0, maxLen - 3) + "...";
    }

    private static String header(HttpResponse<?> response, String name, String fallback) {
        return response.headers().firstValue(name)
                .filter(value -> !value.isBlank())
                .orElse(fallback);
    }

    private static long longHeader(HttpResponse<?> response, String name, long fallback) {
        String value = header(response, name, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int intHeader(HttpResponse<?> response, String name, int fallback) {
        long parsed = longHeader(response, name, fallback);
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            return fallback;
        }
        return (int) parsed;
    }
}
