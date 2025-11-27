## CryptoService

### Purpose
- Static utility for AES-256-GCM operations and key/IV generation.

### Methods
- 'static SecretKey generateAesKey()': generate AES-256 key with 'SecureRandom.getInstanceStrong()'.
- 'static byte[] generateIv()': generate 12-byte IV (96-bit) per GCM recommendations.
- 'static byte[] encryptAesGcm(byte[] plaintext, SecretKey key, byte[] iv, byte[] aad)': encrypt, returns ciphertext+tag.
- 'static byte[] decryptAesGcm(byte[] ciphertext, SecretKey key, byte[] iv, byte[] aad)': decrypt, throws 'AEADBadTagException' on tampering.

***

## MessageEncryptor

### Purpose
- High-level API for encryption of the message payload.

### Constructor
- `MessageEncryptor(PublicKey recipientPublicKey, String senderId, String recipientId, ClockProvider clockProvider)`.

### Method
- `EncryptedMessage encrypt(byte[] payload, String contentType, long ttlSeconds)`:
    1. Validate inputs (payload not null, contentType not blank, TTL in range).
    2. Generate AES key and IV.
    3. Wrap AES key with RSA-OAEP.
    4. Build 'EncryptedMessage' DTO with metadata.
    5. Build AAD from DTO fields('AadCodec.buildAAD(m)').
    6. Encrypt payload with AES-GCM.
    7. Split ciphertext+tag, Base64 encode, populate DTO.
    8. Return `EncryptedMessage`.

***

## MessageDecryptor

### Purpose
- High-level API for decryption of the message payload.

### Constructor
- `MessageDecryptor(PrivateKey recipientPrivateKey, ClockProvider clockProvider)`.

### Method
- `byte[] decryptMessage(EncryptedMessage m)`:
    1. `MessageValidator.validate(m)` → structural validation.
    2. Check expiry: `now > timestampEpochMs + ttlSeconds*1000` → throws `MessageExpiredException`.
    3. Base64 decode: wrappedKey, IV, ciphertext, tag.
    4. Combine ciphertext+tag.
    5. Unwrap AES key with RSA-OAEP.
    6. Rebuild AAD from DTO fields('AadCodec.buildAAD(m)').
    7. Decrypt with AES-GCM.
    8. Catch `AEADBadTagException` → throws `MessageTamperedException`.

***

## CryptoRSA

### Purpose
- RSA-OAEP for key wrapping/unwrapping.

### Methods
- 'static byte[] wrapKey(SecretKey sessionKey, PublicKey recipientPub)': encrypt session key with RSA-OAEP.
- `static SecretKey unwrapKey(byte[] wrappedKey, PrivateKey recipientPriv)`: decrypt session key.

### Transformation
- `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`.

***

## KeyProvider

### Purpose
- Interface for fetch public keys.

### Methods
- 'PublicKey fetchKey(String userId)': returns public key or throws 'KeyNotFoundException'.

***

## UserKeyStore

### Purpose
- Access to the user's sealed keystore.

### Methods
- 'PrivateKey loadPrivateKey(String userId, char[] password)': unseal private key from 'private.enc'.
- 'void saveKeypair(KeyPair pair, KeyMetadata meta, char[] password)': seal and save.

### Structure
```
~/.haf-messenger/keystore/
  key20250114/
    public.pem
    private.enc
    metadata.json
```