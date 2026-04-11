package com.haf.shared.dto;

import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSearchResultTest {

    @Test
    void constructor_supports_active_flag() {
        UserSearchResult dto = new UserSearchResult(
                "u-1",
                "Jane Doe",
                "123",
                "jane@haf.gr",
                "SMINIAS",
                "6900000000",
                "2026-01-01",
                false);

        assertFalse(dto.isActive());
    }

    @Test
    void json_roundtrip_preserves_active_flag() {
        UserSearchResult dto = new UserSearchResult(
                "u-1",
                "Jane Doe",
                "123",
                "jane@haf.gr",
                "SMINIAS",
                true);

        String json = JsonCodec.toJson(dto);
        UserSearchResult decoded = JsonCodec.fromJson(json, UserSearchResult.class);

        assertTrue(decoded.isActive());
    }
}
