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
    void is_allowed_attachment_type_accepts_allowed_values_and_rejects_unsupported() {
        assertTrue(AttachmentConstants.isAllowedAttachmentType("image/png"));
        assertTrue(AttachmentConstants.isAllowedAttachmentType("application/pdf; charset=binary"));

        assertFalse(AttachmentConstants.isAllowedAttachmentType("text/plain"));
        assertFalse(AttachmentConstants.isAllowedAttachmentType(null));
    }
}
