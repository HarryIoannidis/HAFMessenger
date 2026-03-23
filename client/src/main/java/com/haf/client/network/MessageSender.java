package com.haf.client.network;

import com.haf.shared.requests.AttachmentBindRequest;
import com.haf.shared.responses.AttachmentBindResponse;
import com.haf.shared.requests.AttachmentChunkRequest;
import com.haf.shared.responses.AttachmentChunkResponse;
import com.haf.shared.requests.AttachmentCompleteRequest;
import com.haf.shared.responses.AttachmentCompleteResponse;
import com.haf.shared.responses.AttachmentDownloadResponse;
import com.haf.shared.requests.AttachmentInitRequest;
import com.haf.shared.responses.AttachmentInitResponse;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.responses.MessagingPolicyResponse;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.MessageValidationException;
import java.io.IOException;

public interface MessageSender {

    /**
     * Sends an encrypted message to a recipient.
     *
     * @param payload     the plaintext bytes to send
     * @param recipientId the recipient's identifier
     * @param contentType the MIME content type of the payload
     * @param ttlSeconds  the time-to-live in seconds
     * @throws MessageValidationException if message validation fails
     * @throws KeyNotFoundException       if the recipient's public key cannot be
     *                                    found
     * @throws IOException                if network communication fails
     */
    void sendMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds)
            throws MessageValidationException, KeyNotFoundException, IOException;

    /**
     * Encrypts payload bytes into an encrypted message without sending.
     * 
     * @param payload     the plaintext bytes to send
     * @param recipientId the recipient's identifier
     * @param contentType the MIME content type of the payload
     * @param ttlSeconds  the time-to-live in seconds
     * @throws MessageValidationException if message validation fails
     * @throws KeyNotFoundException       if the recipient's public key cannot be
     *                                    found
     * @throws IOException                if network communication fails
     */
    default EncryptedMessage encryptMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds)
            throws MessageValidationException, KeyNotFoundException, IOException {
        throw new UnsupportedOperationException("encryptMessage is not implemented");
    }

    /**
     * Sends an already encrypted message and returns ingress metadata.
     * 
     * @param encryptedMessage the encrypted message to send
     * @throws IOException if network communication fails
     */
    default SendResult sendEncryptedMessage(EncryptedMessage encryptedMessage)
            throws IOException {
        throw new UnsupportedOperationException("sendEncryptedMessage is not implemented");
    }

    /**
     * Sends plaintext and returns ingress metadata.
     * 
     * @param payload     the plaintext bytes to send
     * @param recipientId the recipient's identifier
     * @param contentType the MIME content type of the payload
     * @param ttlSeconds  the time-to-live in seconds
     * @throws MessageValidationException if message validation fails
     * @throws KeyNotFoundException       if the recipient's public key cannot be
     *                                    found
     * @throws IOException                if network communication fails
     */
    default SendResult sendMessageWithResult(byte[] payload, String recipientId, String contentType, long ttlSeconds)
            throws MessageValidationException, KeyNotFoundException, IOException {
        EncryptedMessage encrypted = encryptMessage(payload, recipientId, contentType, ttlSeconds);
        return sendEncryptedMessage(encrypted);
    }

    /**
     * Fetches server-side messaging and attachment policy.
     * 
     * @throws IOException if network communication fails
     */
    default MessagingPolicyResponse fetchMessagingPolicy() throws IOException {
        throw new UnsupportedOperationException("fetchMessagingPolicy is not implemented");
    }

    /**
     * Starts attachment upload.
     * 
     * @param request the attachment upload request
     * @throws IOException if network communication fails
     */
    default AttachmentInitResponse initAttachmentUpload(AttachmentInitRequest request) throws IOException {
        throw new UnsupportedOperationException("initAttachmentUpload is not implemented");
    }

    /**
     * Uploads one attachment chunk.
     * 
     * @param attachmentId the attachment identifier
     * @param request      the attachment chunk request
     * @throws IOException if network communication fails
     */
    default AttachmentChunkResponse uploadAttachmentChunk(String attachmentId, AttachmentChunkRequest request)
            throws IOException {
        throw new UnsupportedOperationException("uploadAttachmentChunk is not implemented");
    }

    /**
     * Completes attachment upload.
     * 
     * @param attachmentId the attachment identifier
     * @param request      the attachment complete request
     * @throws IOException if network communication fails
     */
    default AttachmentCompleteResponse completeAttachmentUpload(String attachmentId, AttachmentCompleteRequest request)
            throws IOException {
        throw new UnsupportedOperationException("completeAttachmentUpload is not implemented");
    }

    /**
     * Binds upload to message envelope so attachment inherits envelope TTL.
     * 
     * @param attachmentId the attachment identifier
     * @param request      the attachment bind request
     * @throws IOException if network communication fails
     */
    default AttachmentBindResponse bindAttachmentUpload(String attachmentId, AttachmentBindRequest request)
            throws IOException {
        throw new UnsupportedOperationException("bindAttachmentUpload is not implemented");
    }

    /**
     * Downloads encrypted attachment blob by reference ID.
     * 
     * @param attachmentId the attachment identifier
     * @throws IOException if network communication fails
     */
    default AttachmentDownloadResponse downloadAttachment(String attachmentId) throws IOException {
        throw new UnsupportedOperationException("downloadAttachment is not implemented");
    }

    /**
     * Result returned by message ingress endpoint.
     * 
     * @param envelopeId       the envelope identifier
     * @param expiresAtEpochMs the expiration time in milliseconds
     */
    record SendResult(String envelopeId, long expiresAtEpochMs) {
    }
}
