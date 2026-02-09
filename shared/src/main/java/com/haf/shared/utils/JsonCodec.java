package com.haf.shared.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonCodec {
    private JsonCodec() {}

    private static final ObjectMapper M = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    /**
     * Serializes object in JSON with strict settings (NO NULL, FAIL_ON_UNKNOWN).
     *
     * @param value any DTO (KeyMetadata, EncryptedMessage, etc.)
     * @return jSON string
     * @throws RuntimeException if serialization fails
     */
    public static String toJson(Object value) {
        try { return M.writeValueAsString(value); }
        catch (Exception e) { throw new RuntimeException("toJson failed", e); }
    }

    /**
     * Deserializes JSON to given type with FAIL_ON_UNKNOWN_PROPERTIES.
     *
     * @param JSON incoming JSON
     * @param type target class
     * @param <T> type of return
     * @return t-type object
     * @throws RuntimeException if deserialization fails
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try { return M.readValue(json, type); }
        catch (Exception e) { throw new RuntimeException("fromJson failed", e); }
    }
}
