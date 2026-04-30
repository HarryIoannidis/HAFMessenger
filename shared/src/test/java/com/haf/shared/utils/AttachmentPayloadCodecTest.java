package com.haf.shared.utils;

import com.haf.shared.constants.AttachmentConstants;
import com.haf.shared.dto.AttachmentInlinePayload;
import com.haf.shared.dto.AttachmentReferencePayload;
import com.haf.shared.exceptions.JsonCodecException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class AttachmentPayloadCodecTest {

    @Test
    void inline_payload_round_trip_validates_and_normalizes_mime() {
        byte[] bytes = "inline-image".getBytes(StandardCharsets.UTF_8);
        AttachmentInlinePayload payload = new AttachmentInlinePayload();
        payload.setFileName("photo.PNG");
        payload.setMediaType("image/png; charset=binary");
        payload.setSizeBytes(bytes.length);
        payload.setDataB64(Base64.getEncoder().encodeToString(bytes));

        String json = AttachmentPayloadCodec.toInlineJson(payload);
        AttachmentInlinePayload parsed = AttachmentPayloadCodec.fromInlineJson(json);

        assertEquals("photo.PNG", parsed.getFileName());
        assertEquals("image/png", parsed.getMediaType());
        assertEquals(bytes.length, parsed.getSizeBytes());
        assertArrayEquals(bytes, Base64.getDecoder().decode(parsed.getDataB64()));
    }

    @Test
    void inline_payload_rejects_invalid_mime_type() {
        AttachmentInlinePayload payload = new AttachmentInlinePayload();
        payload.setFileName("evil.js");
        payload.setMediaType("application-javascript");
        payload.setSizeBytes(4);
        payload.setDataB64(Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3, 4 }));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AttachmentPayloadCodec.toInlineJson(payload));
        assertTrue(ex.getMessage().contains("not allowed"));
    }

    @Test
    void inline_payload_rejects_unknown_json_fields() {
        String json = """
                {
                  "fileName":"x.png",
                  "mediaType":"image/png",
                  "sizeBytes":1,
                  "dataB64":"AA==",
                  "unknown":42
                }
                """;

        assertThrows(JsonCodecException.class, () -> AttachmentPayloadCodec.fromInlineJson(json));
    }

    @Test
    void reference_payload_round_trip_validates() {
        AttachmentReferencePayload payload = new AttachmentReferencePayload();
        payload.setAttachmentId("att-1");
        payload.setFileName("report.pdf");
        payload.setMediaType("application/pdf");
        payload.setSizeBytes(2048);

        String json = AttachmentPayloadCodec.toReferenceJson(payload);
        AttachmentReferencePayload parsed = AttachmentPayloadCodec.fromReferenceJson(json);

        assertEquals("att-1", parsed.getAttachmentId());
        assertEquals("report.pdf", parsed.getFileName());
        assertEquals("application/pdf", parsed.getMediaType());
        assertEquals(2048, parsed.getSizeBytes());
    }

    @Test
    void reference_payload_rejects_empty_attachment_id() {
        AttachmentReferencePayload payload = new AttachmentReferencePayload();
        payload.setAttachmentId(" ");
        payload.setFileName("sheet.xlsx");
        payload.setMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        payload.setSizeBytes(22);

        assertThrows(IllegalArgumentException.class, () -> AttachmentPayloadCodec.toReferenceJson(payload));
    }

    @Test
    void allowlist_contains_expected_defaults() {
        assertTrue(AttachmentConstants.DEFAULT_ALLOWED_TYPES_SET.contains(AttachmentConstants.MIME_TYPE_WILDCARD));
        assertTrue(AttachmentConstants.isAllowedAttachmentType("image/webp"));
        assertTrue(AttachmentConstants.isAllowedAttachmentType("text/plain"));
        assertTrue(AttachmentConstants.isAllowedAttachmentType("application/vnd.android.package-archive"));
    }
}
