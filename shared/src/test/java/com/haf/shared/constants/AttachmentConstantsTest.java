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
    void is_valid_attachment_type_accepts_any_valid_mime_type_by_default() {
        assertTrue(AttachmentConstants.isValidAttachmentType("image/png"));
        assertTrue(AttachmentConstants.isValidAttachmentType("application/pdf; charset=binary"));
        assertTrue(AttachmentConstants.isValidAttachmentType("text/plain"));
        assertTrue(AttachmentConstants.isValidAttachmentType("application/x-msdownload"));

        assertFalse(AttachmentConstants.isValidAttachmentType("not-a-mime"));
        assertFalse(AttachmentConstants.isValidAttachmentType(null));
    }

}
