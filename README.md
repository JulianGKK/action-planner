# PaG Action Planner

> **Working title · Early alpha**

PaG Action Planner is an offline-first Android application for preparing and coordinating collective actions.

It is designed to help groups organize people, roles and tasks during preparation and maintain a clear operational situation overview while an action is taking place.

Privacy, data minimization and local control are core design principles. The project is under active development; features, UI and internal formats may still change significantly.

## Current state

The current alpha includes:

- action preparation and lifecycle
- action-bound persons and pseudonyms
- planned and current roles
- operational tasks, priorities and task state
- a preparation workspace
- an ACTIVE situation hub (`Lagebild`)
- current person status and current functions
- additional protection for sensitive person information
- a separate interactive demo using synthetic example data

Planned areas include action protocols (`Protokoll`), factual observations/incidents, documents and materials, post-action review and retention, and further UX refinement.

## Direct camera import (P10d working branch)

The documents workspace adds an internal JPEG camera route alongside the SAF
file picker. Camera permission is requested only on “Foto aufnehmen”. A captured
photo stays in bounded RAM until explicit confirmation and uses the existing
encrypted document import. No gallery autosave or document viewer is included.
This is a local development change, not a new published release.
See the [P10d contract and test record](docs/product-p10d-camera-import.md) and
[document troubleshooting](docs/troubleshooting-documents.md).

## Try the demo

A separate **PaG Demo** APK is provided with selected pre-releases. It uses its own Android application ID and can be installed alongside the product app.

The demo contains synthetic example data only and is intended for exploring and practising workflows such as:

- preparing an action
- working with persons and planned roles
- creating and updating tasks
- starting an action
- updating operational person status
- working with current functions
- using the ACTIVE situation overview

Demo state is intentionally non-persistent and the demo deliberately simplifies the product security workflow.

**Please do not enter real personal or operational information into the demo.**

## Privacy and security

PaG follows an offline-first and security-first approach. The product application is designed to:

- keep operational data locally on the device
- encrypt locally stored operational data
- work without cloud synchronization
- contain no analytics or advertising
- contain no telemetry
- require no `INTERNET` permission for its core functionality
- minimize Android permissions and collected data

The product security model uses Android platform security facilities and established cryptographic libraries; it does not rely on secrecy of the implementation.

PaG is experimental software and has **not yet undergone an independent external security audit**. No software can guarantee protection on a compromised device or operating system. See [SECURITY.md](SECURITY.md), [the threat model](docs/threat-model.md), [security requirements](docs/security.md), and [privacy notes](docs/privacy.md).

## What PaG is not

PaG is not intended to replace secure messengers, collaborative document editors or general-purpose cloud platforms. It focuses on local preparation and operational coordination of an action. Communication and collaborative editing are better handled by specialized tools.

## Build from source

Requirements:

- JDK 17
- Android SDK with API 37
- the included Gradle wrapper

Product debug build:

```sh
./gradlew :app:assembleDebug
```

Demo build:

```sh
./gradlew :app:assembleDemo
```

Demo APK output:

```text
app/build/outputs/apk/demo/app-demo.apk
```

The demo application ID is `org.pag.prototype.a.demo`; the product application ID is `org.pag.prototype.a`.

## Project documentation

- [Product definition](PRODUCT.md)
- [Architecture](docs/architecture.md)
- [Threat model](docs/threat-model.md)
- [Security requirements](docs/security.md)
- [Data model](docs/data-model.md)
- [Privacy](docs/privacy.md)
- [Retention](docs/data-retention.md)
- [UX philosophy](docs/ux-philosophy.md)
- [UX roadmap](docs/ux-roadmap.md)
- [Demo design](docs/ux4-demo.md)

## Development status

This repository is **early alpha / work in progress**. There are no stability or migration guarantees yet. Use synthetic data while evaluating the project.

A current known test limitation is that the automated Galaxy/API33 UI probe at font scale 2 is not yet fully green for the demo hub. Large-font behavior remains under active testing.

## Security reports

Please do not publish vulnerability details in a public issue. See [SECURITY.md](SECURITY.md) for the reporting address.

## License

PaG Action Planner is licensed under the **GNU General Public License v3.0 or later (`GPL-3.0-or-later`)**. See [LICENSE](LICENSE).
