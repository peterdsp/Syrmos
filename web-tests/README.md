# Web client tests

Zero-dependency Node tests for the static web client, using the built-in
`node:test` runner (Node 18+). Run from this directory:

```bash
node --test
```

- **guardrails.test.js** — static-source guards encoding bug classes that have
  shipped and been fixed: the Athens-time rule (no wall-clock from a bare
  `new Date()`), the `.live-train-marker` position:absolute rule (the "live
  trains marching into the sea" fix), and no hls.js CDN reintroduction.
- **seed-contract.test.js** — invariants on the bundled seed the web client
  loads offline: station coordinates finite and inside Greece, unique station and
  line ids, and a known line-status enum.

These run in CI as the `web-tests` job in `.github/workflows/ci.yml`.
