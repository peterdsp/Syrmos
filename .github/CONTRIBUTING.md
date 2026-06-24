# Contributing to Syrmos

Thank you for considering contributing to Syrmos.

Syrmos is a personal civic project maintained by Petros Dhespollari. Contributions are welcome but subject to a small set of rules so the project stays coherent.

## Scope

Syrmos is a transit companion for Athens (metro, tram, suburban). Contributions that fit the project:

- Bug fixes
- Schedule corrections backed by a public source you can link to
- Accessibility improvements
- New languages
- Performance / build improvements
- Documentation fixes
- Test additions

Contributions outside scope:

- Operator features Syrmos does not own (do not add ticket booking, payment processing, or account management — link to the operator's own site instead, see the Hellenic Train ticket button pattern)
- Trackers, analytics SDKs, ads, or any code that exfiltrates user data
- Soft-blocking dependencies (network calls on the cold-start path that don't gracefully fall through to offline mode)
- Schedule rules without an authoritative source link

## Process

1. Open an issue first for anything larger than a one-file change. Sketch the change in 100 words and we agree on the approach before code lands.
2. Branch from `main` with a descriptive name (`fix/m3-airport-23-cutoff`, `feat/widget-android-glance`).
3. Write or update tests for behavioural changes. The CI workflow in `.github/workflows/tests.yml` is the gate.
4. Run `./gradlew :core:domain:test :core:common:test` locally before pushing.
5. Open a pull request against `main`. Describe what changed, why, and how you tested it.

## Code style

- Kotlin: follow the existing module structure. New use cases go in `core/domain`. New repositories in `core/data`. Network services in `core/network`. Avoid adding direct dependencies between feature modules.
- Swift: SwiftUI-first, no third-party UI dependencies, MapKit for maps.
- Python (API service): standard library where possible; FastAPI + uvicorn are the only large dependencies.

## Licensing of contributions

By submitting a pull request, you agree that your contribution is licensed under:

1. The **BSD 3-Clause License** (for source code), and
2. The **Creative Commons Attribution-ShareAlike 4.0 International License** (for documentation, screenshots, design notes, and the icon pack).

If you cannot agree to both, please open an issue first so we can discuss.

You retain copyright on your contribution. The dual-licensing arrangement is identical to what is already in [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Reporting security issues

Do **not** open a public issue for security vulnerabilities. See [SECURITY.md](SECURITY.md).

## Code of Conduct

See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Treat collaborators with the same respect you would want shown to you. Disagreements about code are fine. Personal attacks are not.

## Contact

Project owner: Petros Dhespollari — info@peterdsp.dev — https://github.com/peterdsp/Syrmos
