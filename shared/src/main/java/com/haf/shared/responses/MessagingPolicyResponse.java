package com.haf.shared.responses;

import java.io.Serializable;
import java.util.List;

/**
 * Server-side messaging policy used by clients to decide attachment transport.
 */
public class MessagingPolicyResponse implements Serializable {
    private long attachmentMaxBytes;
    private long attachmentInlineMaxBytes;
    private int attachmentChunkBytes;
    private List<String> attachmentAllowedTypes;
    private long attachmentUnboundTtlSeconds;
    private String error;

    /**
     * Creates an empty messaging-policy response DTO for JSON deserialization.
     */
    public MessagingPolicyResponse() {
        // Required for JSON deserialization
    }

    /**
     * Returns maximum attachment bytes accepted by the server.
     *
     * @return maximum attachment bytes
     */
    public long getAttachmentMaxBytes() {
        return attachmentMaxBytes;
    }

    /**
     * Sets maximum attachment bytes accepted by the server.
     *
     * @param attachmentMaxBytes maximum attachment bytes
     */
    public void setAttachmentMaxBytes(long attachmentMaxBytes) {
        this.attachmentMaxBytes = attachmentMaxBytes;
    }

    /**
     * Returns maximum inline-attachment bytes.
     *
     * @return maximum inline-attachment bytes
     */
    public long getAttachmentInlineMaxBytes() {
        return attachmentInlineMaxBytes;
    }

    /**
     * Sets maximum inline-attachment bytes.
     *
     * @param attachmentInlineMaxBytes maximum inline-attachment bytes
     */
    public void setAttachmentInlineMaxBytes(long attachmentInlineMaxBytes) {
        this.attachmentInlineMaxBytes = attachmentInlineMaxBytes;
    }

    /**
     * Returns chunk size used for chunked uploads.
     *
     * @return chunk size in bytes
     */
    public int getAttachmentChunkBytes() {
        return attachmentChunkBytes;
    }

    /**
     * Sets chunk size used for chunked uploads.
     *
     * @param attachmentChunkBytes chunk size in bytes
     */
    public void setAttachmentChunkBytes(int attachmentChunkBytes) {
        this.attachmentChunkBytes = attachmentChunkBytes;
    }

    /**
     * Returns allowed attachment MIME types.
     *
     * @return allowed attachment MIME types
     */
    public List<String> getAttachmentAllowedTypes() {
        return attachmentAllowedTypes;
    }

    /**
     * Sets allowed attachment MIME types.
     *
     * @param attachmentAllowedTypes allowed attachment MIME types
     */
    public void setAttachmentAllowedTypes(List<String> attachmentAllowedTypes) {
        this.attachmentAllowedTypes = attachmentAllowedTypes;
    }

    /**
     * Returns TTL for unbound uploads, in seconds.
     *
     * @return unbound-upload TTL in seconds
     */
    public long getAttachmentUnboundTtlSeconds() {
        return attachmentUnboundTtlSeconds;
    }

    /**
     * Sets TTL for unbound uploads, in seconds.
     *
     * @param attachmentUnboundTtlSeconds unbound-upload TTL in seconds
     */
    public void setAttachmentUnboundTtlSeconds(long attachmentUnboundTtlSeconds) {
        this.attachmentUnboundTtlSeconds = attachmentUnboundTtlSeconds;
    }

    /**
     * Returns error text for failed responses.
     *
     * @return error text
     */
    public String getError() {
        return error;
    }

    /**
     * Sets error text for failed responses.
     *
     * @param error error text
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Creates a successful messaging-policy response.
     *
     * @param maxBytes maximum attachment bytes
     * @param inlineMaxBytes maximum inline-attachment bytes
     * @param chunkBytes chunk size in bytes
     * @param allowedTypes allowed attachment MIME types
     * @param unboundTtlSeconds unbound-upload TTL in seconds
     * @return populated success response
     */
    public static MessagingPolicyResponse success(long maxBytes,
            long inlineMaxBytes,
            int chunkBytes,
            List<String> allowedTypes,
            long unboundTtlSeconds) {
        MessagingPolicyResponse response = new MessagingPolicyResponse();
        response.setAttachmentMaxBytes(maxBytes);
        response.setAttachmentInlineMaxBytes(inlineMaxBytes);
        response.setAttachmentChunkBytes(chunkBytes);
        response.setAttachmentAllowedTypes(allowedTypes);
        response.setAttachmentUnboundTtlSeconds(unboundTtlSeconds);
        return response;
    }

    /**
     * Creates an error messaging-policy response.
     *
     * @param message error text
     * @return populated error response
     */
    public static MessagingPolicyResponse error(String message) {
        MessagingPolicyResponse response = new MessagingPolicyResponse();
        response.setError(message);
        return response;
    }
}
