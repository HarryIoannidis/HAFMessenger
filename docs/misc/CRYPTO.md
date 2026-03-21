# CRYPTO

### Core objective
- End-to-end encryption between clients with AES-256-GCM for content and per-message IVs, including integrity verification (GCM tag).
- Key exchange/derivation with X25519 ECDH, with no server-side decryption of content.
- TLS 1.3 with certificate pinning at the transport layer, separate from E2E.
- Storage of ciphertext blobs and metadata only, with TTL/automatic deletion.

---

### E2E architecture

- Each message:
    - Generates a new random 12-byte IV (96-bit, per NIST recommendation for GCM).
    - Generates a fresh ephemeral X25519 keypair for the sender.
    - Derives a session key via ECDH (ephemeral private + recipient public) + SHA-256 KDF.
    - Encrypts the payload with AES-256-GCM and stores a 128-bit tag.
- The server:
    - Does not decrypt content, only routes and stores ciphertext with TTL metadata.
- The transport:
    - TLS 1.3, certificate pinning, mutual authentication where required.

Logical flow diagram:
- Client A: Generate ephemeral X25519 → ECDH derive AES key → Plaintext → AES-256-GCM(IV, Key) → Ciphertext+Tag → Packet(header, ephemeral pubKey, ciphertext, tag, meta) → TLS → Server (store/route) → TLS → Client B → ECDH derive AES key (own private + ephemeral pubKey) → Decrypt AES-GCM → Plaintext.

---

### Message packet format

- Header: version, algorithm id, sender, recipient, timestamp, TTL.
- Key agreement: X25519 ephemeral public key.
- EncryptedPayload: AES-256-GCM ciphertext.
- Tag: GCM authentication tag.
- Meta: IV, content-type, size, E2E=true flag.

Database (server) stores:
- `message_envelopes`: senderId, recipientId, opaque payload blob, ttl. Always encrypted.

---

### Key and storage policies

- Client private keys: E2E X25519 private keys stored securely using PBKDF2 + AES-GCM envelope in local keystore.
- Perfect Forward Secrecy: ephemeral keys generated per message for encryption.
- No permanent storage of sensitive plaintext on the client unless required by policy.
- Test/Dev: separate test keystores, never production secrets in the repo.

---

### TLS and pinning

- TLS 1.3 mandatory, certificate pinning on the client, handshake reachability check at bootstrap.
- Pin failure → block connection and show clear error/diagnostic.

---

### Authentication and 2FA

- Username/password with salted hashing (Argon2id) on the server.
- Optional 2FA/WebAuthn can be layered in future phases.
- Rate limiting, lockout, and auditing.

---

### File load

- Client-side file encryption with AES-256-GCM and ephemeral X25519 keys (same as messages).
- Server-side anti-malware policy and filename sanitization.
- TTL and automatic deletion of encrypted blobs.

---

### Best practices

- Server never decrypts: explicit negative test in IT.
- Per-message IVs, ephemeral keys, and GCM tag verification before display.
- Session-level forward secrecy: new ephemeral key generated for each payload.
- Certificate pinning on all network calls and background services.
- Minimal metadata transfer, no sensitive fields in logs.

---

## Implementation – Step by step

### 1) Prepare crypto module (shared/client)

- Initialize crypto configuration.
- Use `CryptoECC` for X25519 generation and ECDH + SHA-256 key derivation.
- Use `CryptoService` for AES-256-GCM encryption/decryption with canonical AAD.
- SecureRandom for IVs.

### 2) Define DTO/Packet (shared)

- Base `EncryptedMessage` format for all E2E payloads.

```java
public class EncryptedMessage implements Serializable {
  public String version;
  public String senderId;
  public String recipientId;
  public long timestampEpochMs;
  public long ttlSeconds;
  public String algorithm;
  public String ivB64;
  public String ephemeralPublicB64;     // X25519 public key
  public String ciphertextB64;
  public String tagB64;
  public String contentType;
  public long contentLength;
  public boolean e2e = true;
}
```

### 3) Send pipeline (`MessageEncryptor`)

Steps:
- Generate ephemeral X25519 keypair.
- Derive AES key using sender ephemeral private + recipient public.
- Fill a new 12-byte IV.
- AES-256-GCM encrypt the payload with AAD.
- Populate EncryptedMessage DTO and send via TLS/WebSocket.

### 4) Receive pipeline (`MessageDecryptor`)

Steps:
- Verify message schema (MessageValidator).
- Reconstruct ephemeral public key from DTO.
- Derive AES key using recipient private + sender ephemeral public.
- AES-GCM decrypt using iv, ciphertext, tag, AAD.
- Reject if decryption fails (AEADBadTagException).

### 5) Server handlers

- Accepts EncryptedMessage over TLS, validates schema, sizes, TTL.
- Stores opaque envelope payloads.
- Never decrypts content.

---

## Testing and verification

### Test types
- Unit: AES-GCM vectors, ECDH key agreement roundtrips, canonical AAD validation.
- Integration: TLS 1.3 IT server, DB TTL, “server never decrypts” negative test.

### Tips
- Never reuse IVs in GCM.
- Validate ephemeral keys correctly.
- Ensure strict AAD bindings so ciphertext cannot be transplanted.
