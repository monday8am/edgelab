# EdgeLab Roadmap

> This roadmap tracks development of the **EdgeLab** app. The repository also contains **CyclingCopilot**, which has its own [separate roadmap](../cyclingcopilot/roadmap.md).

## Completed

### Core Prototype
- [x] Model selector UI with download management
- [x] Tool calling validation with MediaPipe and LiteRT-LM
- [x] Upload converted models to HuggingFace
- [x] Firebase Crashlytics integration

### HuggingFace Integration _(Dec 2025)_
- [x] Full HuggingFace sign-in / sign-out flow
- [x] Connect model list with authenticated HuggingFace account
- [x] Model info screen
- [x] Grouped model list (by family, access, author)

### Testing Framework _(Jan 2026)_
- [x] Agentic test examples
- [x] Tool trace interface in test runner
- [x] Unit tests for ModelFilenameParser

### Tool Calling & Remote Features _(Jan 2026)_
- [x] JSON-driven dynamic tools with defined mock responses
- [x] Per-test tool isolation (reset conversation between tests)
- [x] Test details screen with domain filtering
- [x] Token speed metrics (tokens/sec) in test results
- [x] Remote test definitions loaded from GitHub with local caching
- [x] Thinking tag parsing and state tracking
- [x] Microsite launched at [edgeagentlab.dev](https://edgeagentlab.dev)
- [x] Author manager screen (add/remove HuggingFace authors)

## Next Up

- [x] Upload app to Play Store (recreated as `com.monday8am.edgelab.explorer`)
- [x] Add FunctionGemma model support
- [ ] Add trained / not trained model filtering
- [ ] Show agentic tests only for supported models
- [ ] Match model names against compatible tests (agentic vs generic)
- [ ] Server-side model stats aggregation across developers

## Future

- [ ] Stats screen
- [ ] iOS support via Kotlin Multiplatform
