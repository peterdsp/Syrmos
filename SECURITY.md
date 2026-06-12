# Security Policy

## Supported versions

| Version | Supported |
|---|---|
| iOS 1.0.x | Yes |
| Android 1.0.x | Yes |
| Web (`syrmos.peterdsp.dev`) | Yes (latest deployed `main`) |
| API (`api-syrmos.peterdsp.dev`) | Yes (latest deployed `main`) |
| Older releases | No |

Older mobile binaries on the App Store / Play Store remain functional but receive no further security fixes.

## Reporting a vulnerability

Please **do not** open a public GitHub issue. Report security issues privately to:

**info@peterdsp.dev**

Include:
- A clear description of the issue
- Steps to reproduce, including a minimal example if applicable
- Affected version(s)
- Any proof-of-concept code

You will receive an acknowledgement within 72 hours, a triage decision within 7 days, and a fix or mitigation within 30 days when validated.

## Scope

In scope:
- Authentication / authorization issues on `api-syrmos.peterdsp.dev`
- XSS, CSRF, injection in the admin web UI
- Data exfiltration paths in mobile or web clients
- Supply chain risks in our build (Gradle, npm, brew dependencies)
- Information disclosure in API responses
- Issues that allow bypassing the offline-first guarantee in a way that risks user privacy

Out of scope:
- Issues against operator websites (STASY, OASA, Hellenic Train) — report directly to those operators
- Cloudflare or GitHub Pages infrastructure — report to those vendors via their channels
- Theoretical concerns without a reproducible attack
- Best-practice suggestions (open a regular PR instead)

## Disclosure timeline

We follow a coordinated disclosure model. Once a fix is shipped, we will publish a brief advisory in the repository's Security Advisories tab crediting the reporter (unless they prefer anonymity).

## PGP

If you prefer encrypted email, a PGP key is available on request from info@peterdsp.dev.
