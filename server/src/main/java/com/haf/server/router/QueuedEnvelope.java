package com.haf.server.router;

import com.haf.shared.dto.EncryptedMessage;

/**
 * Represents a queued envelope for delivery.
 * @param envelopeId the unique identifier of the envelope.
 * @param payload the encrypted message payload.
 * @param createdAtEpochMs the timestamp when the envelope was created.
 * @param expiresAtEpochMs the timestamp when the envelope expires.
 */
public record QueuedEnvelope(String envelopeId,
                             EncryptedMessage payload,
                             long createdAtEpochMs,
                             long expiresAtEpochMs) {
}

