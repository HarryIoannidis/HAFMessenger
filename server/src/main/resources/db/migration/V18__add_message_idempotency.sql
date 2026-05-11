ALTER TABLE message_envelopes
    ADD COLUMN client_message_id VARCHAR(128) NULL AFTER envelope_id;

CREATE UNIQUE INDEX uq_envelopes_sender_client_message
    ON message_envelopes (sender_id, client_message_id);
