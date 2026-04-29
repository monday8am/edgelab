---
name: edgelab-commands
description: Build, test, format, install, and release commands for the EdgeLab multi-module Android project. Load this skill when you need to run Gradle commands or verify changes.
---

# EdgeLab Commands Reference

## Build

| Command | Description |
|---------|-------------|
| `./gradlew build` | All modules |
| `./gradlew :app:explorer:assembleDebug` | EdgeLab APK |
| `./gradlew :app:copilot:assembleDebug` | CyclingCopilot APK |
| `./gradlew clean` | Clean build artifacts |

## Test

| Command | Description |
|---------|-------------|
| `./gradlew test` | All unit tests |
| `./gradlew :presentation:test` | Presentation module tests |
| `./gradlew :agent:test` | Agent module tests |
| `./gradlew :data:test` | Data module tests |
| `./gradlew :core:testDebugUnitTest` | Core (Android) tests |

## Format

| Command | Description |
|---------|-------------|
| `./gradlew ktfmtFormat` | Auto-fix Kotlin formatting |
| `./gradlew ktfmtCheck` | Check formatting without fixing |

## Install

| Command | Description |
|---------|-------------|
| `./gradlew :app:explorer:installDebug` | Install EdgeLab on device |
| `./gradlew :app:copilot:installDebug` | Install CyclingCopilot on device |

## Verification Workflow

Run these **in order** before every commit:

1. `./gradlew ktfmtFormat`
2. `./gradlew test`
3. `./gradlew :app:explorer:assembleDebug :app:copilot:assembleDebug`

**Fail → fix → re-run from top.**

## Module Paths

- `:data` — Pure Kotlin
- `:agent` — Pure Kotlin/JVM
- `:presentation` — Pure Kotlin
- `:core` — Android library
- `:app:explorer` — EdgeLab app
- `:app:copilot` — CyclingCopilot app
