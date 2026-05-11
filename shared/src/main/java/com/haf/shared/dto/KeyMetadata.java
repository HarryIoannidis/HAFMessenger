package com.haf.shared.dto;

/**
 * Store metadata about a key pair.
 *
 * @param keyId             The ID of the key pair.
 * @param algorithm         The algorithm used to generate the key pair.
 * @param fingerprint       The fingerprint of the public key.
 * @param label             The label of the key pair.
 * @param createdAtEpochSec The epoch time when the key pair was created.
 * @param status            The status of the key pair.
 */
public record KeyMetadata(
                String keyId,
                String algorithm,
                String fingerprint,
                String label,
                long createdAtEpochSec,
                String status) {
}