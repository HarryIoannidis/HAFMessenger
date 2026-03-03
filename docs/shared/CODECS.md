# CODECS

## AadCodec

### Purpose
- Build canonical AAD bytes from `EncryptedMessage` metadata for AES-GCM authentication.

### Method
- `static byte[] buildAAD(EncryptedMessage m)`: construct AAD with length-prefixed strings and big-endian numbers.

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
- Change any field → GCM mismatch tag → `MessageTamperedException`.

---

## JsonCodec

### Purpose
- Strict JSON serialization/deserialization for DTOs.

### Methods
- `static String toJson(Object value)`: serialize, throws `RuntimeException` if failed.
- `static <T> T fromJson(String json, Class<T> type)`: deserialize, throws `RuntimeException` if failed.

### ObjectMapper configuration
- `JsonInclude.Include.NON_NULL`: does not serialize null fields.
- `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = true`: reject JSON with extra fields.

### Usage
- Wire protocol: `EncryptedMessage` serialization.
- Keystore: `KeyMetadata` storage in `metadata.json`.
- Auth: `LoginRequest/Response`, `RegisterRequest/Response` serialization.

---

## PemCodec

### Purpose
- PEM encoding/decoding for X25519 keys.

### Methods
- `static String toPem(String type, byte[] der)`: encode DER bytes to PEM format.
- `static byte[] fromPem(String pem)`: decode PEM to DER bytes.

### Format
```
-----BEGIN PUBLIC KEY-----
<Base64-encoded DER>
-----END PUBLIC KEY-----
```

---

## EccKeyIO

### Purpose
- X25519 key generation, PEM/DER encoding/decoding for Elliptic Curve keys.

### Methods
- `static KeyPair generate()`: generate new X25519 keypair via `KeyPairGenerator("XDH")`.
- `static byte[] publicDer(PublicKey pub)`: return DER (X.509 SPKI) of public key.
- `static byte[] privateDer(PrivateKey prv)`: return DER (PKCS#8) of private key.
- `static String publicPem(PublicKey pub)`: export public key to PEM.
- `static String privatePem(PrivateKey prv)`: export private key to PEM.
- `static PublicKey publicFromPem(String pem)`: import X25519 public key from PEM.
- `static PrivateKey privateFromPem(String pem)`: import X25519 private key from PEM.

### Key Format
- Algorithm: `XDH` with `X25519` named parameter.
- Public key: X.509 SPKI DER encoding.
- Private key: PKCS#8 DER encoding.
- PEM headers: `BEGIN PUBLIC KEY` / `BEGIN PRIVATE KEY`.