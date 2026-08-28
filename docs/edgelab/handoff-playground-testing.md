# Handoff — EdgeLab Playground v1

> **Status (2026-08-27): resolved.** The cloud blocker below was cleared — Firebase project `edge-agent-lab` is live, App Check installed, and the first real Gemini turn succeeded (model `gemini-3.5-flash-lite`, `role = "user"` for function responses). Findings: `docs/edgelab/research-cloud-models-interactions-api.md`. Kept as a historical record.

**Next session's job: get this code actually running and correct on a real device.**

Repo: `/Users/anton/Projects/edgelab`
Branch: `feature/playground-plan-1` — committed, clean, **not pushed**
Commit: `d5057b1` (read its message first; it explains the architecture and won't be repeated here)

## Read these, don't re-derive them

| What | Where |
|---|---|
| The plan + Phase 1 checkboxes + resolved binding decision | `docs/edgelab/plan.md` |
| Why cloud is permitted at all / why v1 is cloud-only | `docs/adr/0002`, `docs/adr/0003` |
| Architecture, DI pattern, Compose screen pattern, anti-patterns | `docs/architecture.md`, `docs/patterns.md`, `AGENTS.md` |
| What changed and why | `git show d5057b1` |

## The one thing blocking "make it work"

**The Cloud path has never executed. Not once. No real Gemini call has ever been made from this code.**

It cannot run until the maintainer (Anton) provides, in his own Firebase account:

1. A Firebase project with the **Gemini backend enabled** (Firebase AI Logic, Google AI backend).
2. `google-services.json` dropped into `app/explorer/`.
3. `alias(libs.plugins.google.services)` uncommented in `app/explorer/build.gradle.kts:7-11`.

Until then `FirebaseAiChatFactory.open()` throws with `SETUP_REQUIRED_MESSAGE`, which surfaces in the Trace as a readable error. That is by design, not a bug.

**Do not attempt to work around this by mocking Firebase at runtime or committing a fake `google-services.json`.** If Anton can't supply the project this session, the honest move is to test everything else and say plainly that the cloud leg is unverified.

## Verified vs unverified — be precise about this

**Verified (JVM unit tests, all green, 0 failures):**
- `CloudPlaygroundBackendTest` (13 tests) — the tool loop: chained calls, multiple calls per round, session reuse across turns, fresh session when the Probe set changes, runaway-loop cap, unregistered tool, transport failure.
- `PlaygroundViewModelTest` (13 tests) — state/trace wiring, cloud default, zero-download run, target switching.
- `LocalPlaygroundBackendTest`, `AssetsProbeRepositoryTest`.

Run everything: `./gradlew :agent:test :presentation:test :data:test`

**Unverified — this is the next session's real work:**
- Any real network call to Gemini. The `CloudChatSession` implementation in `core/.../FirebaseAiChatFactory.kt` is the *only* untested class, deliberately (it's the thin adapter).
- **The OpenAI-JSON-Schema → Gemini `Schema` converter** (`toDeclaration()` / `toGeminiSchema()` in the same file). Compiler-checked only. It has never seen a real Gemini response. Highest-risk code in the change — test it against all three seeded probes in `data/src/main/resources/com/monday8am/edgelab/data/testing/tool_tests.json` (`get_location` with empty properties, `get_weather` with two required numbers, `get_meal_history` with one required string).
- `PlaygroundScreen` on a device or emulator. Never launched. Previews exist but were not rendered.
- The model name `"gemini-2.5-flash"` (`FirebaseAiChatFactory.DEFAULT_CLOUD_MODEL`). Firebase's own getting-started docs currently show `gemini-3.7-flash`. **Verify the model id resolves before assuming the integration is broken** — a bad model name will look like an auth or network failure.

## Traps that will cost you an hour

- **Firebase AI Logic Kotlin names differ from the JVM names.** `Schema.str`/`numLong`/`numDouble` are `@JvmName` aliases; in Kotlin they are `Schema.string()`, `Schema.long()`, `Schema.double()`. `javap` shows the JVM names and will mislead you. Verified API surface: `firebase-ai:17.12.1` via `firebase-bom:34.14.1`.
- **Don't trust the Firebase reference docs pages via WebFetch** — they render as SPA shells with no content. Introspect the artifact instead (`javap -classpath <the -api.jar in ~/.gradle/caches/.../transforms/>`), or use named arguments and let the Kotlin compiler verify them.
- `HEAD == main` was true at the start of the previous session; it is not now. Diff against `main` to see the whole feature.

## Known-incomplete, deliberately (from a spec review against `plan.md`)

Not bugs — unbuilt scope. Don't "fix" them as part of testing without deciding they're in scope:

1. **`[used/ignored tool output]` tags** (`plan.md:39,57`) — the highest-signal part of the Trace contract. Not built. `TraceEntry.ModelText` carries only `text`.
2. **Args not typed** (`plan.md:39` "args pretty-printed with types") — `ArgValue(name, value)` drops the type.
3. **Tweak-after-add** (`plan.md:38`) — `ProbeLibrary` only toggles chips; no editor.
4. **`LocalPlaygroundBackend` resets the conversation every turn** — defeats follow-up prompts probing reaction-to-tool-output. The cloud backend does *not* have this problem (it reuses the session). Worth fixing if you touch the local path.

Also left alone: a handful of code-review judgement calls (duplicated test fakes across `:agent`/`:presentation` — justified by the classpath split; a `Pair<handler, probe>` data clump in `LocalPlaygroundBackend`; a swallowed `setToolsAndResetConversation` Result).

## Suggested skills

- **`/diagnosing-bugs`** — the primary one. Use it the moment the cloud leg misbehaves: it refuses to theorise until there's a tight feedback loop (one command that goes red on *this* bug), which is exactly right for a schema-conversion or wire-format mismatch you can't see from the outside.
- **`/tdd`** — for anything you build to close the gaps above, and for turning a real Gemini failure into a regression test against a fake `CloudChatSession`.
- **`/run`** — to launch the app and actually see `PlaygroundScreen`. It has never been on a screen.
- **`/code-review`** against `main` before opening a PR, once it works.
- Skip `/grill-with-docs` and `/to-spec` — the thinking is already done and lives in `plan.md` and the ADRs.

## Open question for Anton

Whether to keep the local-model path in v1 at all. `plan.md:60` lists local download as out of scope for v1, but it was already built and working, so the previous session **kept it** rather than deleting working code, demoting it to a switchable target behind the Cloud default. Removing it is his call, not the agent's.
