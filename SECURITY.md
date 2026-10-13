# Security Policy

## Project status

PaG Action Planner is currently early-alpha software. The security architecture is actively tested and documented, but the project has **not yet undergone an independent external security audit**.

Until such a review has been completed, the current alpha should not be treated as a guarantee that all security-sensitive failure modes have been identified.

## Reporting a vulnerability

Please report potential security vulnerabilities privately to:

**julian@gk-koeln.de**

Please do not open a public GitHub issue containing exploit details, sensitive logs, real personal data, credentials, keys, or other information that could increase risk for users.

A useful report includes the affected version or commit, Android version/device where relevant, a concise description of the issue, reproduction steps using synthetic data, and the security impact you believe it may have.

## Scope

The product application and the separate PaG Demo have intentionally different security properties. The demo uses synthetic, non-persistent state and deliberately does not reproduce the complete product authentication and sensitive-data protection workflow.

The project's threat model and current boundaries are documented in [docs/threat-model.md](docs/threat-model.md) and [docs/security.md](docs/security.md).

## Supported versions

During early alpha, only the latest published pre-release and the current `main` branch are actively maintained.
