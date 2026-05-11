package com.haf.shared.websocket;

import com.haf.shared.dto.EncryptedMessage;
import java.util.List;
import java.util.UUID;

/**
 * JSON DTO for real-time WebSocket events.
 */
public class RealtimeEvent {
    private String type;
    private String eventId;
    private String correlationId;
    private long timestampEpochMs;
    private String nonce;
    private String senderId;
    private String recipientId;
    private String envelopeId;
    private String clientMessageId;
    private String recipientKeyFingerprint;
    private long expiresAtEpochMs;
    private EncryptedMessage encryptedMessage;
    private List<String> envelopeIds;
    private boolean active;
    private boolean duplicate;
    private String code;
    private String error;
    private long retryAfterSeconds;

    public RealtimeEvent() {
        // JSON deserialization
    }

    public static RealtimeEvent outbound(RealtimeEventType type) {
        RealtimeEvent event = new RealtimeEvent();
        event.setType(type.name());
        event.setEventId(UUID.randomUUID().toString());
        event.setTimestampEpochMs(System.currentTimeMillis());
        event.setNonce(UUID.randomUUID().toString());
        return event;
    }

    public static RealtimeEvent serverEvent(RealtimeEventType type) {
        RealtimeEvent event = new RealtimeEvent();
        event.setType(type.name());
        event.setEventId(UUID.randomUUID().toString());
        event.setTimestampEpochMs(System.currentTimeMillis());
        event.setNonce(UUID.randomUUID().toString());
        return event;
    }

    public static RealtimeEvent error(String code, String message) {
        RealtimeEvent event = serverEvent(RealtimeEventType.ERROR);
        event.setCode(code);
        event.setError(message);
        return event;
    }

    public RealtimeEventType eventType() {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return RealtimeEventType.valueOf(type.trim());
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public long getTimestampEpochMs() {
        return timestampEpochMs;
    }

    public void setTimestampEpochMs(long timestampEpochMs) {
        this.timestampEpochMs = timestampEpochMs;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getEnvelopeId() {
        return envelopeId;
    }

    public void setEnvelopeId(String envelopeId) {
        this.envelopeId = envelopeId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getRecipientKeyFingerprint() {
        return recipientKeyFingerprint;
    }

    public void setRecipientKeyFingerprint(String recipientKeyFingerprint) {
        this.recipientKeyFingerprint = recipientKeyFingerprint;
    }

    public long getExpiresAtEpochMs() {
        return expiresAtEpochMs;
    }

    public void setExpiresAtEpochMs(long expiresAtEpochMs) {
        this.expiresAtEpochMs = expiresAtEpochMs;
    }

    public EncryptedMessage getEncryptedMessage() {
        return encryptedMessage;
    }

    public void setEncryptedMessage(EncryptedMessage encryptedMessage) {
        this.encryptedMessage = encryptedMessage;
    }

    public List<String> getEnvelopeIds() {
        return envelopeIds;
    }

    public void setEnvelopeIds(List<String> envelopeIds) {
        this.envelopeIds = envelopeIds;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
