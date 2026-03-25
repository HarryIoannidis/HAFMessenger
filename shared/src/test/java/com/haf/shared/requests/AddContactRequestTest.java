package com.haf.shared.requests;

import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AddContactRequestTest {

    @Test
    void constructor_and_setter_getter_work() {
        AddContactRequest request = new AddContactRequest("u-1");
        assertEquals("u-1", request.getContactId());

        request.setContactId("u-2");
        assertEquals("u-2", request.getContactId());
    }

    @Test
    void json_roundtrip_preserves_contact_id() {
        AddContactRequest request = new AddContactRequest("u-99");

        String json = JsonCodec.toJson(request);
        AddContactRequest decoded = JsonCodec.fromJson(json, AddContactRequest.class);

        assertEquals("u-99", decoded.getContactId());
    }
}
