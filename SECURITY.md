# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in HAFMessenger, **please do not open a public issue.**

Instead, please report it privately by emailing the maintainer or using GitHub's
[private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing/privately-reporting-a-security-vulnerability) feature on this repository.

### What to include

- A clear description of the vulnerability
- Steps to reproduce the issue
- The impact (what an attacker could achieve)
- Any suggested fix or mitigation

### Response timeline

- **Acknowledgement**: within 48 hours
- **Initial assessment**: within 7 days
- **Fix or mitigation**: best effort, typically within 30 days

## Scope

This policy applies to the code in this repository. It does **not** cover:

- Third-party dependencies (report those upstream)
- Infrastructure or deployment issues outside this repository

## Security Architecture Notes

- The server **never** decrypts message payloads — end-to-end encryption is enforced.
- TLS is restricted to **TLS 1.3** with hardened cipher suites.
- Ed25519 signature verification happens before any message processing.
- All secrets and certificates are generated locally and must never be committed.
- Rate limiting and audit logging are server-side enforcement points.

## Supported Versions

| Version | Supported |
| ------- | --------- |
| main    | ✅        |
| others  | ❌        |
