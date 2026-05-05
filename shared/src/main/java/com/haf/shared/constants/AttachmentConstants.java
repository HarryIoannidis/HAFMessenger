package com.haf.shared.constants;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Defines shared attachment limits, MIME allowlists, and attachment content-type
 * constants.
 */
public final class AttachmentConstants {

    public static final String MIME_TYPE_WILDCARD = "*/*";
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    public static final long DEFAULT_MAX_BYTES = 10L * 1024L * 1024L;
    public static final long DEFAULT_INLINE_MAX_BYTES = 2L * 1024L * 1024L;
    public static final int DEFAULT_CHUNK_BYTES = 2 * 1024 * 1024;
    public static final long DEFAULT_UNBOUND_TTL_SECONDS = 1800L;

    public static final String CONTENT_TYPE_INLINE = "application/vnd.haf.attachment-inline+json";
    public static final String CONTENT_TYPE_REFERENCE = "application/vnd.haf.attachment-ref+json";
    public static final String CONTENT_TYPE_ENCRYPTED_BLOB = "application/vnd.haf.encrypted-message+json";

    public static final List<String> DEFAULT_ALLOWED_TYPES = List.of(MIME_TYPE_WILDCARD);

    public static final Set<String> DEFAULT_ALLOWED_TYPES_SET = Set.copyOf(DEFAULT_ALLOWED_TYPES);

    private static final Pattern MIME_TYPE_PATTERN = Pattern
            .compile("[a-z0-9!#$%&'*+.^_`|~-]+/[a-z0-9!#$%&'*+.^_`|~-]+");
    private static final Pattern MIME_POLICY_PATTERN = Pattern
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
     * Returns true if MIME type is allowed for chat attachments.
     *
     * @param mimeType MIME value to validate
     * @return {@code true} when the MIME type is in the attachment allowlist
     */
    public static boolean isAllowedAttachmentType(String mimeType) {
        return isAttachmentTypeAllowedByPolicy(mimeType, DEFAULT_ALLOWED_TYPES);
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

    /**
     * Returns true when a value is valid for attachment allowlist policy entries.
     * Policy entries may be exact MIME types, type wildcards such as {@code image/*},
     * or the global wildcard {@code *&#47;*}.
     *
     * @param mimeType policy entry candidate
     * @return {@code true} when the entry can participate in an allowlist
     */
    public static boolean isValidAttachmentPolicyType(String mimeType) {
        String normalized = normalizeMimeType(mimeType);
        return normalized != null && MIME_POLICY_PATTERN.matcher(normalized).matches();
    }

    /**
     * Returns true when a concrete attachment MIME type is permitted by an allowlist.
     *
     * @param mimeType      attachment MIME type
     * @param allowedTypes  allowed exact MIME types or wildcard policy entries
     * @return {@code true} when the MIME type is valid and matched by policy
     */
    public static boolean isAttachmentTypeAllowedByPolicy(String mimeType, List<String> allowedTypes) {
        String normalized = normalizeMimeType(mimeType);
        if (normalized == null || !isValidMimeType(normalized) || allowedTypes == null || allowedTypes.isEmpty()) {
            return false;
        }

        for (String allowedType : allowedTypes) {
            String allowed = normalizeMimeType(allowedType);
            if (allowed != null && isValidAttachmentPolicyType(allowed) && matchesPolicy(normalized, allowed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPolicy(String mimeType, String policyType) {
        if (MIME_TYPE_WILDCARD.equals(policyType) || mimeType.equals(policyType)) {
            return true;
        }

        int mimeSlash = mimeType.indexOf('/');
        int policySlash = policyType.indexOf('/');
        if (mimeSlash < 0 || policySlash < 0) {
            return false;
        }

        String mimePrimary = mimeType.substring(0, mimeSlash);
        String mimeSubtype = mimeType.substring(mimeSlash + 1);
        String policyPrimary = policyType.substring(0, policySlash);
        String policySubtype = policyType.substring(policySlash + 1);
        boolean primaryMatches = "*".equals(policyPrimary) || policyPrimary.equals(mimePrimary);
        boolean subtypeMatches = "*".equals(policySubtype) || policySubtype.equals(mimeSubtype);
        return primaryMatches && subtypeMatches;
    }
}
