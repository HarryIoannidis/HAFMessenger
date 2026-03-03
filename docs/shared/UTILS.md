# UTILS

## ClockProvider

### Purpose
- Interface for system time dependency injection (testing).

### Method
- `long currentTimeMillis()`: returns current time to ms.

### Implementations
- `SystemClockProvider`: `System.currentTimeMillis()` (production).
- `FixedClockProvider`: fixed timestamp (testing).

---

## FingerprintUtil

### Purpose
- SHA-256 fingerprint for public key verification.

### Method
- `static String sha256Hex(byte[] derPublicKey)`: hash DER-encoded key, returns HEX uppercase.

### Usage
- Trust verification: checking if public key fingerprint matches expected.
- `KeyMetadata.fingerprint` storage.