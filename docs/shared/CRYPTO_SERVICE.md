# CRYPTO_SERVICE

## CryptoService

### Purpose
- Static utility for AES-256-GCM operations and key/IV generation.

### Methods
- `static SecretKey generateAesKey()`: generate AES-256 key with `SecureRandom.getInstanceStrong()`.
- `static byte[] generateIv()`: generate 12-byte IV (96-bit) per GCM recommendations.
- `static byte[] encryptAesGcm(byte[] plaintext, SecretKey key, byte[] iv, byte[] aad)`: encrypt, returns ciphertext+tag.
- `static byte[] decryptAesGcm(byte[] ciphertext, SecretKey key, byte[] iv, byte[] aad)`: decrypt, throws `AEADBadTagException` on tampering.

---

## CryptoECC

### Purpose
- Elliptic Curve Cryptography operations using X25519 for key agreement (ECDH) and SHA-256 for key derivation.

### Methods
- `static byte[] generateSharedSecret(PrivateKey myPrivate, PublicKey theirPublic)`: performs X25519 ECDH key agreement, returns raw shared secret bytes.
- `static SecretKeySpec deriveAesKey(byte[] sharedSecret)`: derives 256-bit AES key from shared secret using SHA-256 KDF.
- `static SecretKeySpec generateAndDeriveAesKey(PrivateKey myPrivate, PublicKey theirPublic)`: convenience method — ECDH + derive in one call.

### Key Agreement Flow
1. `KeyAgreement.getInstance("XDH")` → X25519 ECDH.
2. Init with local private key, do phase with remote public key.
3. `generateSecret()` → raw shared secret bytes.
4. `SHA-256(sharedSecret)` → 256-bit AES key.

---

## MessageEncryptor

### Purpose
- High-level API for encryption of the message payload using X25519 ECDH + AES-256-GCM.

### Constructor
- `MessageEncryptor(PublicKey recipientPublicKey, String senderId, String recipientId, ClockProvider clockProvider)`.
- `recipientPublicKey`: the recipient's X25519 public key.

### Method
- `EncryptedMessage encrypt(byte[] payload, String contentType, long ttlSeconds)`:
    1. Validate inputs (payload not null, contentType not blank, TTL in range).
    2. Generate ephemeral X25519 keypair via `EccKeyIO.generate()`.
    3. Derive AES session key via `CryptoECC.generateAndDeriveAesKey(ephemeralPrivate, recipientPublic)`.
    4. Generate AES-GCM IV via `CryptoService.generateIv()`.
    5. Capture ephemeral public key DER via `EccKeyIO.publicDer()`.
    6. Build `EncryptedMessage` DTO with metadata.
    7. Build AAD from DTO fields (`AadCodec.buildAAD(m)`).
    8. Encrypt payload with AES-GCM.
    9. Split ciphertext+tag, Base64 encode, populate DTO.
    10. Return `EncryptedMessage`.

---

## MessageDecryptor

### Purpose
- High-level API for decryption of the message payload using X25519 ECDH + AES-256-GCM.

### Constructor
- `MessageDecryptor(PrivateKey recipientPrivateKey, ClockProvider clockProvider)`.
- `recipientPrivateKey`: the recipient's X25519 private key.

### Method
- `byte[] decryptMessage(EncryptedMessage m)`:
    1. `MessageValidator.validate(m)` → structural validation.
    2. Check expiry: `now > timestampEpochMs + ttlSeconds*1000` → throws `MessageExpiredException`.
    3. Base64 decode: ephemeral public key, IV, ciphertext, tag.
    4. Combine ciphertext+tag.
    5. Reconstruct ephemeral public key from DER via `KeyFactory.getInstance("XDH")`.
    6. Derive AES session key via `CryptoECC.generateAndDeriveAesKey(recipientPrivate, ephemeralPublic)`.
    7. Rebuild AAD from DTO fields (`AadCodec.buildAAD(m)`).
    8. Decrypt with AES-GCM.
    9. Catch `AEADBadTagException` → throws `MessageTamperedException`.

---

## UserKeystore

### Purpose
- Access to the user's sealed keystore for X25519 keypairs.

### Methods
- `PrivateKey loadPrivateKey(String userId, char[] password)`: unseal X25519 private key from `private.enc`.
- `void saveKeypair(KeyPair pair, KeyMetadata meta, char[] password)`: seal and save X25519 keypair.
- `PublicKey loadPublicKey(Path keyDir)`: load X25519 public key from `public.pem`.

### Structure
```
~/.haf-messenger/keystore/
  key20250114/
    public.pem      (X25519 public key in PEM)
    private.enc     (sealed X25519 private key)
    metadata.json
```