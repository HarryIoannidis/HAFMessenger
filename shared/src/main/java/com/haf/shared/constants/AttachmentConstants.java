package com.haf.shared.constants;

import java.util.regex.Pattern;

/**
 * Defines shared attachment limits and attachment content-type constants.
 */
public final class AttachmentConstants {

    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    public static final long DEFAULT_MAX_BYTES = 10L * 1024L * 1024L;
    public static final long DEFAULT_INLINE_MAX_BYTES = 2L * 1024L * 1024L;
    public static final int DEFAULT_CHUNK_BYTES = 4 * 1024 * 1024;
    public static final long DEFAULT_UNBOUND_TTL_SECONDS = 1800L;

    public static final String CONTENT_TYPE_INLINE = "application/vnd.haf.attachment-inline+json";
    public static final String CONTENT_TYPE_REFERENCE = "application/vnd.haf.attachment-ref+json";
    public static final String CONTENT_TYPE_ENCRYPTED_BLOB = "application/vnd.haf.encrypted-message+json";

    public static final String HEADER_CHUNK_INDEX = "X-Attachment-Chunk-Index";
    public static final String HEADER_ATTACHMENT_ID = "X-Attachment-Id";
    public static final String HEADER_ATTACHMENT_ENCRYPTED_SIZE = "X-Attachment-Encrypted-Size";
    public static final String HEADER_ATTACHMENT_CHUNK_COUNT = "X-Attachment-Chunk-Count";
    public static final String HEADER_ATTACHMENT_CONTENT_TYPE = "X-Attachment-Content-Type";

    private static final Pattern MIME_TYPE_PATTERN = Pattern
            .compile("[a-z0-9!#$%&'*+.^_`|~-]+/[a-z0-9!#$%&'*+.^_`|~-]+");

    /**
     * Prevents instantiation of this constants holder.
     */
    private AttachmentConstants() {
    }

    /**
     * Normalizes MIME value by stripping parameters and lowercasing.
     *
     * @param mimeType MIME value to normalize
     * @return normalized MIME type without parameters, or {@code null} when input
     *         is blank/null
     */
    public static String normalizeMimeType(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        int semicolon = mimeType.indexOf(';');
        String base = semicolon >= 0 ? mimeType.substring(0, semicolon) : mimeType;
        String normalized = base.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Returns true if MIME type is syntactically valid for chat attachments.
     *
     * @param mimeType MIME value to validate
     * @return {@code true} when the MIME type is syntactically valid
     */
    public static boolean isValidAttachmentType(String mimeType) {
        return isValidMimeType(mimeType);
    }

    /**
     * Returns true when a MIME type is syntactically valid for an attachment/media
     * content type.
     *
     * @param mimeType MIME value to validate
     * @return {@code true} when the normalized value is a concrete MIME type
     */
    public static boolean isValidMimeType(String mimeType) {
        String normalized = normalizeMimeType(mimeType);
        return normalized != null && MIME_TYPE_PATTERN.matcher(normalized).matches();
    }

}
