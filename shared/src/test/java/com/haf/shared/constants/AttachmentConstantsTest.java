package com.haf.shared.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentConstantsTest {

    @Test
    void normalize_mime_type_strips_parameters_and_lowercases() {
        assertEquals("image/png", AttachmentConstants.normalizeMimeType(" Image/PNG ; charset=utf-8 "));
        assertEquals("application/pdf", AttachmentConstants.normalizeMimeType("application/pdf"));
    }

    @Test
    void normalize_mime_type_returns_null_for_null_or_blank() {
        assertNull(AttachmentConstants.normalizeMimeType(null));
        assertNull(AttachmentConstants.normalizeMimeType("   "));
    }

    @Test
    void is_allowed_attachment_type_accepts_any_valid_mime_type_by_default() {
        assertTrue(AttachmentConstants.isAllowedAttachmentType("image/png"));
        assertTrue(AttachmentConstants.isAllowedAttachmentType("application/pdf; charset=binary"));
        assertTrue(AttachmentConstants.isAllowedAttachmentType("text/plain"));
        assertTrue(AttachmentConstants.isAllowedAttachmentType("application/x-msdownload"));

        assertFalse(AttachmentConstants.isAllowedAttachmentType("not-a-mime"));
        assertFalse(AttachmentConstants.isAllowedAttachmentType(null));
    }

    @Test
    void attachment_policy_supports_exact_subtype_and_global_wildcards() {
        assertTrue(AttachmentConstants.isAttachmentTypeAllowedByPolicy("image/png", java.util.List.of("image/*")));
        assertTrue(AttachmentConstants.isAttachmentTypeAllowedByPolicy("application/pdf", java.util.List.of("*/*")));
        assertFalse(AttachmentConstants.isAttachmentTypeAllowedByPolicy("text/plain", java.util.List.of("image/*")));
        assertFalse(AttachmentConstants.isAttachmentTypeAllowedByPolicy("not-a-mime", java.util.List.of("*/*")));
    }
}
