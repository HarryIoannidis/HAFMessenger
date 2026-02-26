-- Add `id_number`, and foreign keys to `file_uploads` for ID photo and Selfie.
ALTER TABLE users
ADD COLUMN id_number VARCHAR(100) AFTER reg_number,
ADD COLUMN id_photo_id VARCHAR(64) AFTER id_number,
ADD COLUMN selfie_photo_id VARCHAR(64) AFTER id_photo_id;

-- Add optional foreign key constraints to ensure the photos actually exist in `file_uploads`.
-- If the files are deleted cascade, nullify these pointers to avoid crashing the user record.
ALTER TABLE users
ADD CONSTRAINT fk_users_id_photo FOREIGN KEY (id_photo_id) REFERENCES file_uploads(file_id) ON DELETE SET NULL,
ADD CONSTRAINT fk_users_selfie_photo FOREIGN KEY (selfie_photo_id) REFERENCES file_uploads(file_id) ON DELETE SET NULL;
