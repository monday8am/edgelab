# Dependencies

Build dependency constraints and gotchas. Read this before modifying any `build.gradle.kts`.

---

## LiteRT-LM Dual Artifact

LiteRT-LM ships two artifacts that contain overlapping classes:

- `litertlm-jvm` — pure JVM, used in `:agent`
- `litertlm-android` — Android native, used in `:core`

Both share the same version (defined as `litertlm` in `libs.versions.toml`).

`:agent` depends on `litertlm-jvm`:
```kotlin
// agent/build.gradle.kts
@Suppress("UnstableApiUsage") implementation(libs.litertlm.jvm)
```

`:core` depends on `litertlm-android` and MUST exclude `litertlm-jvm` from transitive deps:
```kotlin
// core/build.gradle.kts
api(project(":agent")) {
    exclude(group = "com.google.ai.edge.litertlm", module = "litertlm-jvm")
}
api(project(":presentation")) {
    exclude(group = "com.google.ai.edge.litertlm", module = "litertlm-jvm")
}
```

**If you forget these exclusions:** duplicate class errors at compile time.

---

## Koog MCP Exclusion

`:agent` excludes `kotlin-sdk-core-jvm` from Koog to avoid duplicate classes:

```kotlin
// agent/build.gradle.kts
api(libs.koog.agents) {
    exclude(group = "io.modelcontextprotocol", module = "kotlin-sdk-core-jvm")
}
```

---

## api() vs implementation()

| Module | Dependency | Type | Why |
|--------|-----------|------|-----|
| `:core` | `:data` | `api` | Apps need data types |
| `:core` | `:agent` | `api` + exclude | Apps need agent interfaces |
| `:core` | `:presentation` | `api` + exclude | Apps need ViewModels |
| `:presentation` | `:data`, `:agent` | `implementation` | Internal only |
| `:agent` | `:data` | `implementation` | Internal only |
| `:agent` | `koog-agents` | `api` | Types used by consumers |
| `:agent` | `kermit` | `api` | Logging available downstream |
| `:data` | `okhttp`, `json` | `api` | Types used by consumers |

Rule: `api()` when downstream modules need the dependency's types. `implementation()` for internal-only.

---

## Version Catalog

All versions are defined in `gradle/libs.versions.toml`. Never hardcode versions in `build.gradle.kts`.

To add a new dependency:
1. Add version to `[versions]`: `newLib = "1.2.3"`
2. Add library to `[libraries]`: `new-lib = { group = "com.example", name = "lib", version.ref = "newLib" }`
3. Reference in build file: `implementation(libs.new.lib)`

**Known issue:** `:agent/build.gradle.kts` hardcodes the serialization plugin version instead of using the catalog:
```kotlin
// Current (inconsistent):
id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
// Should be:
alias(libs.plugins.kotlin.serialization)
```

---

## Java Toolchain

All modules use Java 17:
```kotlin
kotlin { jvmToolchain(17) }
```

Pure Kotlin modules also set `sourceCompatibility` / `targetCompatibility` to `VERSION_17`.

---

## META-INF Exclusion

App modules must exclude META-INF files to avoid duplicates from Koog/Ktor transitive deps:
```kotlin
// app/explorer/build.gradle.kts, app/copilot/build.gradle.kts
packaging { resources { excludes += "META-INF/*" } }
```

---

## Compose BOM

Compose dependencies are declared WITHOUT explicit versions. The BOM manages all versions:
```kotlin
implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.compose.ui)         // no version
implementation(libs.androidx.compose.material3)   // no version
```

Never add a version to a Compose library managed by the BOM.

---

## Compose Lint

App modules include Slack's Compose lint checks:
```kotlin
lintChecks(libs.compose.lint.checks)
```

Some rules are disabled for prototype stage — see lint block in `app/explorer/build.gradle.kts`.

---

## ktfmt

Defined in root `build.gradle.kts`, applied to all subprojects:
```kotlin
subprojects {
    apply(plugin = "com.ncorti.ktfmt.gradle")
    extensions.configure<KtfmtExtension> { kotlinLangStyle() }
}
```

Plugin version defined in `libs.versions.toml`. Style: `kotlinLangStyle()` (Kotlin official).
