package com.haf.shared.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haf.shared.exceptions.JsonCodecException;

public final class JsonCodec {

    /**
     * Private constructor to prevent instantiation.
     */
    private JsonCodec() {
    }

    private static final ObjectMapper M = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    /**
     * Serializes object in JSON with strict settings (NO NULL, FAIL_ON_UNKNOWN).
     *
     * @param value any DTO (KeyMetadata, EncryptedMessage, etc.)
     * @return JSON string
     * @throws JsonCodecException if serialization fails
     */
    public static String toJson(Object value) {
        try {
            return M.writeValueAsString(value);
        } catch (Exception e) {
            throw new JsonCodecException("toJson failed", e);
        }
    }

    /**
     * Deserializes JSON to given type with FAIL_ON_UNKNOWN_PROPERTIES.
     *
     * @param JSON incoming JSON
     * @param type target class
     * @param <T>  type of return
     * @return t-type object
     * @throws JsonCodecException if deserialization fails
     */
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return M.readValue(json, type);
        } catch (Exception e) {
            throw new JsonCodecException("fromJson failed", e);
        }
    }
}