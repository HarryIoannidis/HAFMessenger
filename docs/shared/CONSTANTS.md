## CryptoConstants

### Σκοπός
- Σταθερές για AES-GCM και RSA-OAEP crypto operations.

### AES
- 'AES = 'AES''.
- 'AES_GCM_TRANSFORMATION = 'AES/GCM/NoPadding''.
- 'AES_KEY_BITS = 256'.
- 'GCM_TAG_BITS = 128'.
- 'GCM_IV_BYTES = 12'.
- 'SALT_LEN = 16'.

### RSA
- 'RSA_OAEP_TRANSFORMATION = 'RSA/ECB/OAEPWithSHA-256AndMGF1Padding''.
- 'OAEP_MGF_ALGO = 'MGF1''.
- 'OAEP_HASH = 'SHA-256''.
- 'RSA_MIN_BITS = 2048'.
- 'RSA_MAX_BITS = 4096'.

***

## MessageHeader

### Σκοπός
- Wire protocol policy και validation rules.

### Protocol
- 'VERSION = "1"'.
- 'ALGO_AEAD = 'AES-256-GCM+RSA-OAEP''.

### AEAD sizes
- 'IV_BYTES = 12' (από 'CryptoConstants.GCM_IV_BYTES').
- 'GCM_TAG_BYTES = 16' (128 bits / 8).

### Identity policy
- 'MIN_SENDER_LEN = 3'.
- 'MIN_RECIPIENT_LEN = 3'.

### TTL policy
- 'MAX_TTL_SECONDS = 86400' (24 hours).
- 'MIN_TTL_SECONDS = 60' (1 minute).

### Size limits
- 'MAX_CIPHERTEXT_BASE64 = 8388608' (8 MB).

### Content types allowlist
- Text: '"text/plain"', '"text/markdown"'.
- Images: '"image/png"', '"image/jpeg"', '"image/gif"', '"image/webp"'.
- Video: '"video/mp4"', '"video/webm"', '"video/ogg"'.
- Documents: '"application/pdf"', Office formats (docx, xlsx, pptx), MS Office legacy.
- Generic: '"application/octet-stream"'.