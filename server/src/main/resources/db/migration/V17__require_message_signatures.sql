ALTER TABLE message_envelopes
    ADD COLUMN signature_algorithm VARCHAR(32) NOT NULL AFTER aad_hash,
    ADD COLUMN sender_signing_key_fingerprint VARCHAR(64) NOT NULL AFTER signature_algorithm,
    ADD COLUMN signature VARBINARY(64) NOT NULL AFTER auth_tag;

CREATE INDEX idx_envelopes_sender_signing_fingerprint
    ON message_envelopes (sender_signing_key_fingerprint);

