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

    public MessagingPolicyResponse() {
        // Required for JSON deserialization
    }

    public long getAttachmentMaxBytes() {
        return attachmentMaxBytes;
    }

    public void setAttachmentMaxBytes(long attachmentMaxBytes) {
        this.attachmentMaxBytes = attachmentMaxBytes;
    }

    public long getAttachmentInlineMaxBytes() {
        return attachmentInlineMaxBytes;
    }

    public void setAttachmentInlineMaxBytes(long attachmentInlineMaxBytes) {
        this.attachmentInlineMaxBytes = attachmentInlineMaxBytes;
    }

    public int getAttachmentChunkBytes() {
        return attachmentChunkBytes;
    }

    public void setAttachmentChunkBytes(int attachmentChunkBytes) {
        this.attachmentChunkBytes = attachmentChunkBytes;
    }

    public List<String> getAttachmentAllowedTypes() {
        return attachmentAllowedTypes;
    }

    public void setAttachmentAllowedTypes(List<String> attachmentAllowedTypes) {
        this.attachmentAllowedTypes = attachmentAllowedTypes;
    }

    public long getAttachmentUnboundTtlSeconds() {
        return attachmentUnboundTtlSeconds;
    }

    public void setAttachmentUnboundTtlSeconds(long attachmentUnboundTtlSeconds) {
        this.attachmentUnboundTtlSeconds = attachmentUnboundTtlSeconds;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

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

    public static MessagingPolicyResponse error(String message) {
        MessagingPolicyResponse response = new MessagingPolicyResponse();
        response.setError(message);
        return response;
    }
}
