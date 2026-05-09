DELETE FROM users;

ALTER TABLE users
    ADD COLUMN signing_public_key_fingerprint VARCHAR(64) NOT NULL AFTER public_key_pem,
    ADD COLUMN signing_public_key_pem TEXT NOT NULL AFTER signing_public_key_fingerprint;

CREATE UNIQUE INDEX uk_users_signing_fingerprint
    ON users (signing_public_key_fingerprint);

