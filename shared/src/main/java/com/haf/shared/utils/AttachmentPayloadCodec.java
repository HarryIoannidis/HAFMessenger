package com.haf.shared.utils;

import com.haf.shared.constants.AttachmentConstants;
import com.haf.shared.dto.AttachmentInlinePayload;
import com.haf.shared.dto.AttachmentReferencePayload;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * JSON codecs and validation rules for attachment payload messages.
 */
public final class AttachmentPayloadCodec {

    private static final int MAX_FILENAME_LENGTH = 255;

    private AttachmentPayloadCodec() {
    }

    public static String toInlineJson(AttachmentInlinePayload payload) {
        validateInlinePayload(payload);
        return JsonCodec.toJson(payload);
    }

    public static AttachmentInlinePayload fromInlineJson(String json) {
        AttachmentInlinePayload payload = JsonCodec.fromJson(json, AttachmentInlinePayload.class);
        validateInlinePayload(payload);
        return payload;
    }

    public static AttachmentInlinePayload fromInlineJson(byte[] jsonBytes) {
        return fromInlineJson(new String(jsonBytes, StandardCharsets.UTF_8));
    }

    public static String toReferenceJson(AttachmentReferencePayload payload) {
        validateReferencePayload(payload);
        return JsonCodec.toJson(payload);
    }

    public static AttachmentReferencePayload fromReferenceJson(String json) {
        AttachmentReferencePayload payload = JsonCodec.fromJson(json, AttachmentReferencePayload.class);
        validateReferencePayload(payload);
        return payload;
    }

    public static AttachmentReferencePayload fromReferenceJson(byte[] jsonBytes) {
        return fromReferenceJson(new String(jsonBytes, StandardCharsets.UTF_8));
    }

    public static void validateInlinePayload(AttachmentInlinePayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("AttachmentInlinePayload is required");
        }

        validateFileName(payload.getFileName());
        String normalizedType = validateAllowedMimeType(payload.getMediaType());
        payload.setMediaType(normalizedType);

        if (payload.getSizeBytes() <= 0 || payload.getSizeBytes() > AttachmentConstants.DEFAULT_MAX_BYTES) {
            throw new IllegalArgumentException("Attachment payload size is out of bounds");
        }
        if (payload.getDataB64() == null || payload.getDataB64().isBlank()) {
            throw new IllegalArgumentException("Inline attachment dataB64 is required");
        }

        byte[] decoded = decodeBase64(payload.getDataB64(), "Inline attachment dataB64 is invalid");
        if (decoded.length != payload.getSizeBytes()) {
            throw new IllegalArgumentException("Inline attachment sizeBytes does not match decoded data length");
        }
    }

    public static void validateReferencePayload(AttachmentReferencePayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("AttachmentReferencePayload is required");
        }
        if (payload.getAttachmentId() == null || payload.getAttachmentId().isBlank()) {
            throw new IllegalArgumentException("Attachment reference attachmentId is required");
        }
        validateFileName(payload.getFileName());
        String normalizedType = validateAllowedMimeType(payload.getMediaType());
        payload.setMediaType(normalizedType);
        if (payload.getSizeBytes() <= 0 || payload.getSizeBytes() > AttachmentConstants.DEFAULT_MAX_BYTES) {
            throw new IllegalArgumentException("Attachment reference size is out of bounds");
        }
    }

    public static String validateAllowedMimeType(String mimeType) {
        String normalized = AttachmentConstants.normalizeMimeType(mimeType);
        if (!AttachmentConstants.DEFAULT_ALLOWED_TYPES_SET.contains(normalized)) {
            throw new IllegalArgumentException("Attachment MIME type is not allowed: " + mimeType);
        }
        return normalized;
    }

    private static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Attachment filename is required");
        }
        if (fileName.length() > MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException("Attachment filename is too long");
        }
        if (fileName.contains("\0") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Attachment filename contains invalid path separators");
        }
    }

    private static byte[] decodeBase64(String data, String message) {
        try {
            return Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }
}
