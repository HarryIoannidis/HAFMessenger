ALTER TABLE message_envelopes
    ADD COLUMN delivered_at TIMESTAMP NULL AFTER delivered,
    ADD COLUMN read_at TIMESTAMP NULL AFTER delivered_at;

UPDATE message_envelopes
   SET delivered_at = COALESCE(delivered_at, created_at)
 WHERE delivered = TRUE
   AND delivered_at IS NULL;

CREATE INDEX idx_envelopes_sender_receipts
    ON message_envelopes (sender_id, expires_at, delivered_at, read_at);
