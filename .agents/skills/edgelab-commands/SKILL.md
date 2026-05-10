---
name: edgelab-commands
description: Build, test, format, install, lint, and model export commands for the EdgeLab multi-module Android project. Load this skill when you need to run Gradle commands or verify changes.
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
| `./gradlew :app:explorer:connectedAndroidTest` | Instrumented tests (EdgeLab) |
| `./gradlew :app:copilot:connectedAndroidTest` | Instrumented tests (Copilot) |

> Instrumented tests require a connected device or emulator (`adb devices`).

## Format & Lint

| Command | Description |
|---------|-------------|
| `./gradlew ktfmtFormat` | Auto-fix Kotlin formatting |
| `./gradlew ktfmtCheck` | Check formatting without fixing |
| `./gradlew :app:explorer:lintDebug` | Compose lint report (EdgeLab) |
| `./gradlew :app:copilot:lintDebug` | Compose lint report (Copilot) |

> Pre-commit hook runs ktfmt check. Pre-push runs Compose lint (if app module has changes).

## Install

| Command | Description |
|---------|-------------|
| `./gradlew :app:explorer:installDebug` | Install EdgeLab on device |
| `./gradlew :app:copilot:installDebug` | Install CyclingCopilot on device |

## Model Export

Export HuggingFace models to LiteRT-LM format:

```bash
pip install ai-edge-torch torch transformers
python export_to_litert.py \
  --model_id Qwen/Qwen3-0.6B-Instruct \
  --output_path qwen3_0.6b_q8_ekv4096.litertlm \
  --max_seq_len 4096 \
  --quantize int8
```

Context size recommendations: 1024 (basic), 2048 (multi-turn), 4096 (tool calling, recommended), 8192+ (long conversations).

> Only dynamic-8bits quantization supported on GPU L4 processors. Runtime context cannot exceed compiled context window.

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
