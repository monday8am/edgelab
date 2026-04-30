---
name: edgelab-release
description: Release build workflow, signing configuration, and GitHub Actions release process for EdgeLab and CyclingCopilot. Load this skill when preparing a release.
---

# EdgeLab Release Reference

## Release Build Commands

| Command | Description |
|---------|-------------|
| `./gradlew :app:explorer:bundleRelease` | EdgeLab AAB (requires upload keystore) |
| `./gradlew :app:copilot:bundleRelease` | CyclingCopilot AAB (requires upload keystore) |

## GitHub Actions

- **Release Build** workflow (manual trigger)
- Select app + version when triggering

## Signing

- Uses **Google Play App Signing** — Google holds the app signing key, we hold upload keys.
- Each app has its own upload keystore and properties file:
  - `signing/explorer-upload.keystore` + `signing/explorer-upload.properties`
  - `signing/copilot-upload.keystore` + `signing/copilot-upload.properties`
- All signing files are gitignored.
- CI uses per-app GitHub secrets:
  - `EDGELAB_UPLOAD_*` for EdgeLab
  - `COPILOT_UPLOAD_*` for CyclingCopilot
