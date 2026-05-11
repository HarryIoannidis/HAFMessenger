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

    /**
     * Prevents instantiation of this utility class.
     */
    private AttachmentPayloadCodec() {
    }

    /**
     * Serializes an inline attachment payload to JSON after validation.
     *
     * @param payload inline attachment payload to serialize
     * @return JSON representation of the payload
     * @throws IllegalArgumentException when the payload is invalid
     */
    public static String toInlineJson(AttachmentInlinePayload payload) {
        validateInlinePayload(payload);
        return JsonCodec.toJson(payload);
    }

    /**
     * Deserializes inline attachment JSON and validates the resulting payload.
     *
     * @param json inline attachment JSON content
     * @return validated inline attachment payload
     * @throws IllegalArgumentException when the JSON or decoded payload is invalid
     */
    public static AttachmentInlinePayload fromInlineJson(String json) {
        AttachmentInlinePayload payload = JsonCodec.fromJson(json, AttachmentInlinePayload.class);
        validateInlinePayload(payload);
        return payload;
    }

    /**
     * Deserializes inline attachment JSON bytes and validates the resulting payload.
     *
     * @param jsonBytes UTF-8 JSON bytes
     * @return validated inline attachment payload
     * @throws IllegalArgumentException when the JSON or decoded payload is invalid
     */
    public static AttachmentInlinePayload fromInlineJson(byte[] jsonBytes) {
        return fromInlineJson(new String(jsonBytes, StandardCharsets.UTF_8));
    }

    /**
     * Serializes an attachment reference payload to JSON after validation.
     *
     * @param payload reference payload to serialize
     * @return JSON representation of the payload
     * @throws IllegalArgumentException when the payload is invalid
     */
    public static String toReferenceJson(AttachmentReferencePayload payload) {
        validateReferencePayload(payload);
        return JsonCodec.toJson(payload);
    }

    /**
     * Deserializes reference attachment JSON and validates the resulting payload.
     *
     * @param json reference attachment JSON content
     * @return validated reference payload
     * @throws IllegalArgumentException when the JSON or decoded payload is invalid
     */
    public static AttachmentReferencePayload fromReferenceJson(String json) {
        AttachmentReferencePayload payload = JsonCodec.fromJson(json, AttachmentReferencePayload.class);
        validateReferencePayload(payload);
        return payload;
    }

    /**
     * Deserializes reference attachment JSON bytes and validates the resulting payload.
     *
     * @param jsonBytes UTF-8 JSON bytes
     * @return validated reference payload
     * @throws IllegalArgumentException when the JSON or decoded payload is invalid
     */
    public static AttachmentReferencePayload fromReferenceJson(byte[] jsonBytes) {
        return fromReferenceJson(new String(jsonBytes, StandardCharsets.UTF_8));
    }

    /**
     * Validates an inline attachment payload for required metadata and data length consistency.
     *
     * @param payload inline payload to validate
     * @throws IllegalArgumentException when validation fails
     */
    public static void validateInlinePayload(AttachmentInlinePayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("AttachmentInlinePayload is required");
        }

        validateFileName(payload.getFileName());
        String normalizedType = validateMimeType(payload.getMediaType());
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

    /**
     * Validates an attachment reference payload for required fields and allowed constraints.
     *
     * @param payload reference payload to validate
     * @throws IllegalArgumentException when validation fails
     */
    public static void validateReferencePayload(AttachmentReferencePayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("AttachmentReferencePayload is required");
        }
        if (payload.getAttachmentId() == null || payload.getAttachmentId().isBlank()) {
            throw new IllegalArgumentException("Attachment reference attachmentId is required");
        }
        validateFileName(payload.getFileName());
        String normalizedType = validateMimeType(payload.getMediaType());
        payload.setMediaType(normalizedType);
        if (payload.getSizeBytes() <= 0 || payload.getSizeBytes() > AttachmentConstants.DEFAULT_MAX_BYTES) {
            throw new IllegalArgumentException("Attachment reference size is out of bounds");
        }
    }

    /**
     * Validates and normalizes an attachment MIME type.
     *
     * @param mimeType MIME type to validate
     * @return normalized MIME type
     * @throws IllegalArgumentException when the MIME type is missing or invalid
     */
    public static String validateMimeType(String mimeType) {
        String normalized = AttachmentConstants.normalizeMimeType(mimeType);
        if (!AttachmentConstants.isValidAttachmentType(normalized)) {
            throw new IllegalArgumentException("Attachment MIME type is invalid: " + mimeType);
        }
        return normalized;
    }

    /**
     * Validates an attachment file name for emptiness, size, and path traversal characters.
     *
     * @param fileName file name to validate
     * @throws IllegalArgumentException when the file name is invalid
     */
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

    /**
     * Decodes Base64 content and rethrows format failures with a caller-specific message.
     *
     * @param data    Base64 string to decode
     * @param message message to include when decoding fails
     * @return decoded bytes
     * @throws IllegalArgumentException when Base64 decoding fails
     */
    private static byte[] decodeBase64(String data, String message) {
        try {
            return Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }
}
