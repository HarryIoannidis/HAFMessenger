## AadCodec

### Purpose
- Build canonical AAD bytes from 'EncryptedMessage' metadata for AES-GCM authentication.

### Method
- 'static byte[] buildAAD(EncryptedMessage m)': construct AAD with length-prefixed strings and big-endian numbers.

### Encoding
- Strings: 4-byte length prefix (big-endian) + UTF-8 bytes.
- Longs: 8-byte big-endian.
- Null strings → empty array (length=0).

### Field Row
1. `version` (string).
2. `algorithm` (string).
3. `senderId` (string).
4. `recipientId` (string).
5. `timestampEpochMs` (long).
6. `ttlSeconds` (long).
7. `contentType` (string).
8. `contentLength` (long).

### Rules
- Same metadata → same AAD bytes (byte-wise).
- Change any field → GCM mismatch tag → 'MessageTamperedException'.

***

## JsonCodec

### Purpose
- Strict JSON serialization/deserialization for DTOs.

### Methods
- 'static String toJson(Object value)': serialize, throws 'RuntimeException' if failed.
- 'static <T> T fromJson(String json, Class<T> type)': deserialize, throws 'RuntimeException' if failed.

### ObjectMapper configuration
- 'JsonInclude.Include.NON_NULL': does not serialize null fields.
- 'DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = true': reject JSON with extra fields.

### Usage
- Wire protocol: `EncryptedMessage` serialization.
- Keystore: 'KeyMetadata' storage in 'metadata.json'.

***

## PemCodec

### Purpose
- PEM encoding/decoding for RSA public keys.

### Methods
- 'static String publicKeyToPem(PublicKey key)': encode in PEM format.
- 'static PublicKey pemToPublicKey(String pem)': decode from PEM.

### Format
```
-----BEGIN PUBLIC KEY-----
<Base64-encoded DER>
-----END PUBLIC KEY-----
```

---

## RsaKeyIO

### Purpose
- Save/load RSA keypairs from filesystem.

### Methods
- `static void savePublicKey(Path file, PublicKey key)`: save `public.pem`.
- 'static PublicKey loadPublicKey(Path file)': load from 'public.pem'.
- 'static void savePrivateKeyEncrypted(Path file, PrivateKey key, char[] password)': seal and save 'private.enc'.
- 'static PrivateKey loadPrivateKeyEncrypted(Path file, char[] password)': unseal from 'private.enc'.