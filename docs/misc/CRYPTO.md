# Encryption and Security (Implementation Guide)

### Core objective
- End-to-end encryption between clients with AES-256-GCM for content and per-message IVs, including integrity verification (GCM tag).
- Key exchange/wrapping with RSA-OAEP (default 4096-bit keys, minimum 2048-bit), with no server-side decryption of content.
- TLS 1.3 with certificate pinning at the transport layer, separate from E2E.
- Storage of ciphertext blobs and metadata only, with TTL/automatic deletion.

***

### E2E architecture

- Each message:
    - Generates a new random 12-byte IV (96-bit, per NIST recommendation for GCM).
    - Encrypts the payload with AES-256-GCM and stores a 128-bit tag.
    - Uses a session key (symmetric encryption key) that changes per conversation/session for session-level forward secrecy.
    - Wraps the session key with the recipient’s RSA public key (or multi-recipient envelope for group chats).
- The server:
    - Does not decrypt content, only routes and stores ciphertext with TTL metadata.
- The transport:
    - TLS 1.3, certificate pinning, mutual authentication where required.

Logical flow diagram:
- Client A: Plaintext → AES-256-GCM(IV, SessionKey) → Ciphertext+Tag → Envelope RSA(pubB, SessionKey) → Packet(header, envelope, ciphertext, tag, meta) → TLS → Server (store/route) → TLS → Client B → Unwrap RSA(privB) → Decrypt AES-GCM → Plaintext.

***

### Message packet format

- Header: version, algorithm id, sender, recipient, timestamp, TTL.
- EncryptedKey: RSA-OAEP wrapping of the session key to the recipient.
- EncryptedPayload: AES-256-GCM ciphertext.
- Tag: GCM authentication tag.
- Meta: IV, content-type, size, E2E=true flag.

Database (server) stores:
- messages(id, senderId, receiverId, contentBlob, contentMeta, timestamp, ttl). Always encrypted.

***

### Key and storage policies

- Client private keys: stored in the OS keystore where available.
- Key rotation: periodic/forced renewal of session keys per session and re-key for large streams/files.
- No permanent storage of sensitive data on the client unless required by policy.
- Test/Dev: separate test keystores, never production secrets in the repo.

***

### TLS and pinning

- TLS 1.3 mandatory, certificate pinning on the client, handshake reachability check at bootstrap.
- Pin failure → block connection and show clear error/diagnostic.
- In testing, boot IT server with test CA and pinned cert.

***

### Authentication and 2FA

- Username/password with salted hashing (Argon2/bcrypt/PBKDF2) on the server.
- TOTP and optional WebAuthn/FIDO2 where required.
- Rate limiting, lockout, and auditing.

***

### File load

- Client-side file encryption with AES-256-GCM, chunking, transfer references/progress.
- Server-side anti-malware policy and filename sanitization.
- TTL and automatic deletion of encrypted blobs.

***

### Best practices

- Server never decrypts: explicit negative test in IT.
- Per-message IVs and GCM tag verification before display.
- Session-level forward secrecy: new session key per conversation/cycle.
- Certificate pinning on all network calls and background services.
- Minimal metadata transfer, no sensitive fields in logs.

***

## Implementation – Step by step

### 1) Prepare crypto module (client)

- Enable JCA/BouncyCastle providers (where required) at bootstrap.
- Create CryptoManager with:
    - AES-256-GCM encrypt/decrypt.
    - RSA-OAEP wrap/unwrap of session keys.
    - SecureRandom for IV/nonce.

Code (simplified):

```java
package com.haf.client.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.util.Base64;

public final class CryptoManager {
  private static final String AES = "AES";
  private static final String AES_GCM = "AES/GCM/NoPadding";
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_BYTES = 12;

  private static final String RSA = "RSA";
  private static final String RSA_OAEP = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
  private static final SecureRandom RNG = new SecureRandom();

  public static SecretKey newSessionKey() throws Exception {
    KeyGenerator kg = KeyGenerator.getInstance(AES);
    kg.init(256, RNG);
    return kg.generateKey();
  }

  public static byte[] newIv() {
    byte[] iv = new byte[IV_BYTES];
    RNG.nextBytes(iv);
    return iv;
  }

  public static class GcmOut {
    public final byte[] ciphertext;
    public final byte[] tag; // included in GCM output if using consolidated buffer
    public final byte[] iv;
    public GcmOut(byte[] c, byte[] t, byte[] i) { this.ciphertext = c; this.tag = t; this.iv = i; }
  }

  public static GcmOut aesGcmEncrypt(byte[] plaintext, SecretKey key, byte[] iv) throws Exception {
    Cipher c = Cipher.getInstance(AES_GCM);
    GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
    c.init(Cipher.ENCRYPT_MODE, key, spec, RNG);
    byte[] out = c.doFinal(plaintext);
    // If the provider returns a single buffer [ciphertext||tag], split by length.
    int tagBytes = GCM_TAG_BITS / 8;
    int ctLen = out.length - tagBytes;
    byte[] ct = new byte[ctLen];
    byte[] tag = new byte[tagBytes];
    System.arraycopy(out, 0, ct, 0, ctLen);
    System.arraycopy(out, ctLen, tag, 0, tagBytes);
    return new GcmOut(ct, tag, iv);
  }

  public static byte[] aesGcmDecrypt(byte[] ciphertext, byte[] tag, SecretKey key, byte[] iv) throws Exception {
    Cipher c = Cipher.getInstance(AES_GCM);
    GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
    c.init(Cipher.DECRYPT_MODE, key, spec);
    byte[] in = new byte[ciphertext.length + tag.length];
    System.arraycopy(ciphertext, 0, in, 0, ciphertext.length);
    System.arraycopy(tag, 0, in, ciphertext.length, tag.length);
    return c.doFinal(in);
  }

  public static byte[] rsaWrapSessionKey(PublicKey recipientPub, SecretKey sessionKey) throws Exception {
    Cipher c = Cipher.getInstance(RSA_OAEP);
    OAEPParameterSpec oaep = new OAEPParameterSpec(
      "SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT
    );
    c.init(Cipher.ENCRYPT_MODE, recipientPub, oaep);
    return c.doFinal(sessionKey.getEncoded());
  }

  public static SecretKey rsaUnwrapSessionKey(PrivateKey recipientPriv, byte[] wrapped) throws Exception {
    Cipher c = Cipher.getInstance(RSA_OAEP);
    OAEPParameterSpec oaep = new OAEPParameterSpec(
      "SHA-256", "MGF1", new MGF1ParameterSpec("SHA-256"), PSource.PSpecified.DEFAULT
    );
    c.init(Cipher.DECRYPT_MODE, recipientPriv, oaep);
    byte[] raw = c.doFinal(wrapped);
    return new SecretKeySpec(raw, AES);
  }

  public static String b64(byte[] in) { return Base64.getEncoder().encodeToString(in); }
  public static byte[] unb64(String s) { return Base64.getDecoder().decode(s); }
}
```

Implementation best practices:
- Verify that the provider returns consolidated output for GCM and split the tag correctly.
- Use SecureRandom for IV, never counters.
- OAEP with SHA-256/MGF1 for RSA wrap.

***

### 2) Define DTO/Packet (shared)

- Align the wire format in the shared module before client/server.

DTO code:

```java
package com.haf.shared.dto;
import java.io.Serializable;

public class EncryptedMessageDTO implements Serializable {
  public String version;           // e.g. "1"
  public String senderId;
  public String recipientId;
  public long timestampEpochMs;
  public long ttlSeconds;
  public String algorithm;         // "AES-256-GCM+RSA-OAEP"
  public String ivB64;             // 12 bytes
  public String ephemeralPublicB64;     // RSA-OAEP
  public String ciphertextB64;     // AES-GCM ciphertext (without tag)
  public String tagB64;            // GCM tag 16 bytes
  public String contentType;       // "text/plain", "file/chunk"
  public long contentLength;
  public boolean e2e = true;
}
```

***

### 3) Send pipeline (client ViewModel → network)

Steps:
- Create a session key if there is no active one for the conversation.
- Fill a new 12-byte IV.
- AES-256-GCM encrypt the payload, splitting out the tag.
- RSA-OAEP wrap the session key with the recipient’s public key.
- Populate EncryptedMessageDTO and send via TLS WebSocket/TCP.

Code (brief):

```java
SecretKey sess = sessionKeyCache.getOrCreate(chatId, CryptoManager::newSessionKey);
byte[] iv = CryptoManager.newIv();
var out = CryptoManager.aesGcmEncrypt(plaintextBytes, sess, iv);
byte[] wrapped = CryptoManager.rsaWrapSessionKey(recipientPubKey, sess);

EncryptedMessageDTO dto = new EncryptedMessageDTO();
dto.version = "1";
dto.senderId = myId;
dto.recipientId = peerId;
dto.timestampEpochMs = System.currentTimeMillis();
dto.ttlSeconds = 7 * 24 * 3600;
dto.algorithm = "AES-256-GCM+RSA-OAEP";
dto.ivB64 = CryptoManager.b64(iv);
dto.ephemeralPublicB64 = CryptoManager.b64(wrapped);
dto.ciphertextB64 = CryptoManager.b64(out.ciphertext);
dto.tagB64 = CryptoManager.b64(out.tag);
dto.contentType = "text/plain";
dto.contentLength = plaintextBytes.length;
dto.e2e = true;

network.send(dto); // over TLS with pinning
```

***

### 4) Receive pipeline (client)

Steps:
- Verify pinning and DTO integrity.
- RSA-OAEP unwrap wrappedKey with the recipient’s private key (OS keystore).
- AES-GCM decrypt using iv, ciphertext, tag.
- Reject if decryption fails (GCM AuthFail).

Code:

```java
SecretKey sess = CryptoManager.rsaUnwrapSessionKey(myPrivateKey, CryptoManager.unb64(dto.ephemeralPublicB64));
byte[] pt = CryptoManager.aesGcmDecrypt(
  CryptoManager.unb64(dto.ciphertextB64),
  CryptoManager.unb64(dto.tagB64),
  sess,
  CryptoManager.unb64(dto.ivB64)
);
// Forward to UI
```

***

### 5) Server handlers

- Accepts EncryptedMessageDTO over TLS, validates schema, sizes, TTL.
- Stores only ciphertext/tag/iv/wrappedKey and meta, with E2E flag and TTL policy.
- Never decrypts content. Adds audit event with no plaintext.

***

### 6) Password and key security

- Password hashing: Argon2/bcrypt/PBKDF2 with salt.
- Private keys: OS keystore where possible, export-protected.
- Memory clearing where feasible, no logging of sensitive byte arrays.

***

## Testing and verification

### Test types
- Unit: AES-GCM vectors, DTO roundtrip, RSA unwrap.
- Integration: TLS 1.3 IT server, pinned cert, DB TTL, “server never decrypts” negative test.
- UI/Headless: Bindings and “Send disabled until valid crypto envelope”.

Ready AES-GCM test example (from TESTING.md, adapted):

```java
@Test
void aes256Gcm_known_answer() throws Exception {
  byte[] key = HexFormat.of().parseHex("603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4");
  byte[] iv  = HexFormat.of().parseHex("f0f1f2f3f4f5f6f7f8f9fafb");
  byte[] pt  = HexFormat.of().parseHex("6bc1bee22e409f96e93d7e117393172a");
  SecretKey sk = new SecretKeySpec(key, "AES");
  var out = CryptoManager.aesGcmEncrypt(pt, sk, iv);
  byte[] dec = CryptoManager.aesGcmDecrypt(out.ciphertext, out.tag, sk, iv);
  assertArrayEquals(pt, dec);
}
```

IT negative test (server never decrypts): checks that the DB contains only ciphertext and that there are no plaintext traces.

***

## Alternative options

- Instead of RSA-2048: RSA-3072 for higher margin or adoption of X25519 + AEAD (hybrid) in a later phase for better forward secrecy.
- Instead of only GCM: AES-256-GCM is preferred, but future support for ChaCha20-Poly1305 is allowed for devices without AES-NI.
- Server-assisted key agreement: possibility for server-signed key exchange metadata without access to content, while remaining E2E.

***

## Operational policies

- TTL: set per content category (e.g. messages 7 days, files 72 hours).
- Self-destructing messages: client UI timer + server TTL.
- Audit logging: encrypted events, no plaintext.

***

## Integration into Workflow/Scenes

- Bootstrap: Crypto.initProviders, TLS reachability, pinning checks in Splash.
- Login: TLS 1.3 with pinning, TOTP second factor.
- Chat send: CryptoPipeline.encryptAndSend with GCM, per-message IV, session key cache.
- File send: encrypt-and-upload with chunking and reference forwarding.

***

## Common mistakes and mitigation

- IV reuse: forbidden in GCM, causes catastrophic security loss.
- Incorrect handling of GCM tag: must be separated and verified before UI delivery.
- Logging sensitive data: forbidden, use redaction.
- Pinning not enabled: fix in network stack, block handshake.

***

## Checklists

- Client
    - JCA providers loaded at bootstrap.
    - AES-256-GCM OK, RSA-OAEP OK, SecureRandom OK.
    - OS keystore for private keys.
    - TLS pinning on all endpoints.

- Server
    - TLS 1.3 with pinned certs (for test) and strong cipher suite.
    - Storage of ciphertext/tag/iv/wrappedKey/meta only.
    - TTL and deletion scheduler.

- Tests
    - AES-GCM vectors pass.
    - DTO schema contracts locked.
    - “Never decrypt server” IT passes.

***

## Immediate implementation items

- Complete CryptoManager and session key cache on the client.
- Consolidate EncryptedMessageDTO in shared and serialization paths.
- Pinning in Network.quickTlsCheck and all WebSocket/TCP flows.
- IT tests for TLS+pinning+TTL.

***

# EXPLANATION
### What we are doing in simple terms
- We lock every message before it leaves the computer, so the server only sees locked data.
- To achieve this, we create a “secret key” for the message and use it to lock it. Then we lock this key with the recipient’s “public key” so only they can open it.
- The connection to the server is additionally inside an armored channel (TLS) and there is a check that we are talking to the correct server (pinning).

### Basic terms, simply
- Secret key (AES): A random “key” that locks/unlocks the message content quickly and securely.
- Public/Private key (RSA): Like padlock/key. The public key is for locking towards you, the private key only you have to unlock.
- IV: A random number used once so that two encryptions never look the same.
- TLS pinning: Check that we are talking to our own server and not an intermediary.

### What you must do practically (execution steps)
- On the client:
    - Enable the crypto module at application startup (Bootstrap).
    - When sending a message:
        - Create a new random secret key and IV.
        - Lock the text with it (AES).
        - Lock the secret key with the recipient’s public key (RSA).
        - Send packet: header + locked key + locked message + integrity tag.
    - When receiving a message:
        - First unlock the secret key with your private key (RSA).
        - Unlock the message with AES and display it.
- On the transport:
    - Use TLS 1.3 and pinning for the connection. If the check fails, block.

### Preset configurations (do not change them)
- Algorithms: AES-256-GCM for messages, RSA-OAEP for the “message key”, TLS 1.3 for the connection.
- IV: always a new random 12 bytes for each message.
- Server: never unlocks content, stores only locked blobs with lifetime (TTL).

### Security checks that will be ready
- If an IV is reused by mistake, the message is rejected.
- If the integrity tag is broken, nothing is shown in the UI.
- If TLS pinning fails, the connection is cut.

### Where you will hook in the code
- CryptoManager: ready encrypt/decrypt and wrap/unwrap functions. You will call them from the send/receive ViewModel.
- EncryptedMessageDTO: the common “packet schema” that flows from client → server → client.
- Network layer: enabled TLS pinning at bootstrap and in sends.

***

# ENCRYPTION FLOW

***

### 1. Basic principles

- End-to-end encryption: Only the sender and intended recipient can read the message; no one else, not even the server, can decrypt the content.
- Main algorithms:
    - Symmetric encryption (AES-256-GCM): the message itself is encrypted with a random, unique key.
    - Asymmetric encryption (RSA-OAEP): that key is locked (wrapped) for each recipient with their public key.

***

### 2. Prerequisites (before sending)

- Each user has:
    - A long-term RSA key pair: (private key kept secret, public key shareable).
    - The device stores the private key securely.
- The sender’s application has the recipient’s public key.

***

### 3. Composition and encryption (Sender)

1. The user types a message or attaches a file.
2. The application prepares metadata (senderId, recipientId, timestamp, TTL, contentType, length).
3. The application generates a random AES-256 ‘session’ key only for this message.
4. A random 12-byte IV is generated.
5. Encryption with AES-256-GCM: (text, key, IV, metadata as AAD).
6. Wrap the session key with the recipient’s RSA public key.
7. Create the packet (EncryptedMessage DTO) with all fields Base64 encoded.
8. Execute validity check (MessageValidator).
9. Convert to JSON and send over secure TLS channel.

***

### 4. Handling by the server

- The server does not decrypt or see the content, only metadata.
- It performs basic validation and routing.
- It applies policies, limits, and stores as-is.

***

### 5. Reception and decryption (Recipient)

1. The client receives the JSON packet.
2. Performs checks and reads fields.
3. Decodes IV, wrappedKey, ciphertext, tag, and metadata.
4. Unwraps the session key with the RSA private key.
5. Decrypts the message with AES-256-GCM, confirms integrity.
6. Checks contentType and length to display correctly.
7. On decryption error, the message becomes unreadable.

***

### 6. Flow diagram

Sender → Generates keys, encrypts, wraps keys, sends JSON.  
Server → Routes, stores, does not read.  
Recipient → Receives, decrypts, displays.

***

### 7. Security guarantees

- Neither the server nor any third party can read or silently alter the content.
- Errors, tampering, or interception are rejected because of the AEAD tag.

***

### 8. Tips for newcomers

- If the private key is lost, access to messages is lost.
- Each message has a unique key and IV.
- Use SecureRandom for all randomness.
- Base64 fields must have correct length or be rejected.
- For multiple recipients, wrap the key separately.

***

This flow ensures military-grade confidentiality and integrity, with only the end users having access to the content.