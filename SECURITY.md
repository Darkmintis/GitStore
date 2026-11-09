# Security Policy

## Supported Versions

Currently supported versions for security updates:

| Version | Supported          |
| ------- | ------------------ |
| latest  | :white_check_mark: |
| older   | :x:                |

## Reporting a Vulnerability

We take security seriously. If you discover a security vulnerability, please follow these steps:

### DO NOT create a public GitHub issue

Instead:

1. **GitHub Security Advisory**: Use private reporting at [GitHub Security Advisories](https://github.com/Darkmintis/GitStore/security/advisories/new)
2. **Email fallback**: If advisory reporting is unavailable, open a private maintainer contact request through [GitHub Issues](https://github.com/Darkmintis/GitStore/issues/new)

3. **Include**:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

4. **Response Time**:
   - Initial response: Within 48 hours
   - Status update: Within 7 days
   - Fix timeline: Depends on severity

## Security Best Practices

### For Users:
- Download APKs only from official GitHub releases
- Verify APK signatures match our certificate fingerprint
- Keep the app updated to the latest version
- Review permissions before installing

### For Developers:
- Never commit sensitive data (API keys, tokens)
- Use environment variables for secrets
- Follow secure coding practices
- Keep dependencies updated

## Disclosure Policy

- Security fixes are released as soon as possible
- We credit researchers who report vulnerabilities (if desired)
- Details are disclosed after a fix is available

## Security Features

Git Store implements:
- HTTPS-only connections
- Secure token storage on device via encrypted storage wrappers
- No tracking or analytics
- Open source code for transparency

---

Thank you for helping keep Git Store secure! 🔒
