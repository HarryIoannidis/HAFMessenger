package com.haf.shared.constants;

import java.util.List;
import java.util.Set;

/**
 * Defines shared attachment limits, MIME allowlists, and attachment content-type constants.
 */
public final class AttachmentConstants {

    public static final long DEFAULT_MAX_BYTES = 10L * 1024L * 1024L;
    public static final long DEFAULT_INLINE_MAX_BYTES = 1L * 1024L * 1024L;
    public static final int DEFAULT_CHUNK_BYTES = 512 * 1024;
    public static final long DEFAULT_UNBOUND_TTL_SECONDS = 1800L;

    public static final String CONTENT_TYPE_INLINE = "application/vnd.haf.attachment-inline+json";
    public static final String CONTENT_TYPE_REFERENCE = "application/vnd.haf.attachment-ref+json";
    public static final String CONTENT_TYPE_ENCRYPTED_BLOB = "application/vnd.haf.encrypted-message+json";

    public static final List<String> DEFAULT_ALLOWED_TYPES = List.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    public static final Set<String> DEFAULT_ALLOWED_TYPES_SET = Set.copyOf(DEFAULT_ALLOWED_TYPES);

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
     * Returns true if MIME type is allowed for chat attachments.
     *
     * @param mimeType MIME value to validate
     * @return {@code true} when the MIME type is in the attachment allowlist
     */
    public static boolean isAllowedAttachmentType(String mimeType) {
        String normalized = normalizeMimeType(mimeType);
        return normalized != null && DEFAULT_ALLOWED_TYPES_SET.contains(normalized);
    }
}
