# WORKFLOW

## Core Workflows

### 1. Encryption Workflow (Message Sending)

```
                     ┌─────────────┐
                     │   Sender    │
                     └──────┬──────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ DefaultMessageSender.sendMessage()                      │
│ - Input: plaintext payload, recipientId, contentType    │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ MessageEncryptor.encrypt()                              │
│ 1. Generate random 256-bit AES key (ephemeral)          │
│ 2. Generate random 12-byte IV                           │
│ 3. Build AAD from metadata (version, algorithm, IDs, etc.)   │
│ 4. Encrypt payload with AES-256-GCM                     │
│    → produces: ciphertext + 128-bit auth tag            │
│ 5. Wrap AES key with recipient's RSA public key         │
│    (RSA-OAEP SHA-256/MGF1)                              │ 
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ EncryptedMessage DTO                                    │
│ - version, algorithm, senderId, recipientId             │
│ - ciphertextB64, ivB64, tagB64, ephemeralPublicB64           │
│ - contentType, contentLength, timestamp, ttl            │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ JsonCodec.toJson()                                      │
│ - Strict Jackson serialization (UTF-8, canonical)       │
└───────────────────────────┬─────────────────────────────┘     
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ WebSocketAdapter.send()                                 │
│ - Sends to server over TLS 1.3                          │
└─────────────────────────────────────────────────────────┘
```

### 2. Server Ingress & Routing (Phase 5)

```
┌─────────────────────────────────────────────────────────┐
│ HttpIngressServer / WebSocketIngressServer              │
│ - TLS 1.3 termination                                   │
│ - Security headers (HSTS, CSP, etc.)                    │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ EncryptedMessageValidator.validate()                    │
│ - Check version, algorithm, field lengths               │
│ - Validate Base64 encoding                              │
│ - Check TTL bounds, timestamp, contentType allowlist    │
│ - NO DECRYPTION (envelope-only validation)              │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ RateLimiterService.checkAndConsume()                    │
│ - Database-backed quota enforcement                     │
│ - MAX_DAILY_MESSAGES, MAX_MESSAGE_BYTES                 │
└───────────────────────────┬─────────────────────────────┘ 
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ MailboxRouter.ingress()                                 │
│ - Routes to recipient's mailbox                         │
│ - Notifies via WebSocket if online                      │
└───────────────────────────┬─────────────────────────────┘     
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ EnvelopeDAO.insert()                                    │
│ - Stores encrypted envelope in MySQL (HikariCP)         │
│ - Computes AAD hash for integrity                       │
│ - Sets expiration timestamp                             │
│ - NO PAYLOAD DECRYPTION (zero-knowledge storage)        │
└─────────────────────────────────────────────────────────┘ 
```

### 3. Decryption Workflow (Message Receiving)

```
┌─────────────────────────────────────────────────────────┐
│ DefaultMessageReceiver.start()                          │
│ - WebSocket listener for incoming messages              │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ JsonCodec.fromJson()                                    │
│ - Deserialize to EncryptedMessage DTO                   │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ MessageValidator.validateRecipientOrThrow()             │
│ - Verify recipientId matches current user               │
│ - Check TTL expiration with ClockProvider               │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ MessageDecryptor.decrypt()                              │
│ 1. Unwrap AES key with recipient's RSA private key      │
│    (RSA-OAEP SHA-256/MGF1)                              │
│ 2. Rebuild AAD from message metadata                    │
│ 3. Decrypt ciphertext with AES-256-GCM                  │
│    - Uses IV, auth tag, AAD                             │ 
│    - Throws AEADBadTagException if tampered             │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ MessageListener.onMessage()                             │
│ - Routes to UI based on contentType                     │
│ - text/media → MessageViewModel                         │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
                     ┌─────────────┐
                     │  Receiver   │
                     └──────┬──────┘
                            │
                            ▼
                     ┌─────────────┐
                     │     UI      │
                     └─────────────┘

```

### 4. Key Management Workflow (Phase 3)

#### Key Generation & Provisioning
```
┌─────────────────────────────────────────────────────────┐
│ KeystoreBootstrap.run()                                 │
│ - Prompts for user password                             │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ RsaKeyIO.generateKeyPair()                              │
│ - RSA 2048/3072 bit key generation                      │
└───────────────────────────┬─────────────────────────────┘ 
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ KeystoreSealing.seal()                                  │
│ - Derive key from password: PBKDF2-SHA256               │
│   (100,000 iterations, 256-bit salt)                    │ 
│ - Encrypt private key with AES-256-GCM                  │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ UserKeyStore.saveKeypair()                              │
│ - Save to: <keystore_root>/<userId>/<keyId>/            │
│   - public.pem (SubjectPublicKeyInfo)                   │
│   - private.enc (sealed PKCS#8)                         │
│   - metadata.json (fingerprint, status, timestamps)     │
│ - Set permissions: 700 (dir), 600 (files)               │
└───────────────────────────┬─────────────────────────────┘       
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ FingerprintUtil.computeFingerprint()                    │
│ - SHA-256 hash of public key DER encoding               │
│ - Used for trust verification                           │
└─────────────────────────────────────────────────────────┘
```

#### Key Loading

```
┌─────────────────────────────────────────────────────────┐
│ UserKeyStore.loadCurrentPrivate()                       │
│ - Locate CURRENT key directory                          │
│ - Read private.enc + metadata.json                      │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ KeystoreSealing.unseal()                                │
│ - Derive key from password (PBKDF2-SHA256)              │
│ - Decrypt with AES-256-GCM                              │
│ - Verify auth tag (tamper detection)                    │
└───────────────────────────┬─────────────────────────────┘     
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│ FingerprintUtil.verify()                                │
│ - Recompute fingerprint from loaded key                 │
│ - Compare with metadata.json                            │
│ - Reject if mismatch                                    │
└─────────────────────────────────────────────────────────┘

```

---

## Security Highlights
- End-to-End Encryption: Server never sees plaintext
- Zero-Knowledge Storage: Only encrypted envelopes stored
- Forward Secrecy: Ephemeral AES keys per message
- Tamper Detection: GCM auth tags + AAD binding
- Key Protection: PBKDF2 + AES-GCM sealed private keys
- TLS 1.3: All network traffic encrypted
- Rate Limiting: Database-backed quota enforcement
