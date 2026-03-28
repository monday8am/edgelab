# CyclingCopilot — 2-Week Sprint Plan

> **Goal:** Finish the MVP (AI-powered cycling copilot with voice) in Week 1, then add polish and nice-to-have features in Week 2.
>
> **How to use:** Open Claude Code each day, reference this plan, and work on that day's deliverables. Each day is self-contained and testable.

---

## Current State (as of 2026-03-27)

The app has three screens fully built with UI, navigation, and state management:

| Screen | Status | What works | What's mocked/missing |
|--------|--------|------------|----------------------|
| **Onboard & Download** | Complete | HuggingFace OAuth, model download with progress, cancel/retry | Nothing — fully functional |
| **Ride Setup** | Complete | GPS mode selector (simulation only), route catalog, speed control, advanced settings | Device GPS option disabled ("coming soon") |
| **Live Ride** | UI complete, brain missing | Map with route polyline, rider dot, HUD metrics, chat panel, playback controls, GPS simulation | Chat replies are fake (hardcoded 1.5s delay), no AI inference, no voice input, no POI markers, no TTS |

**Key infrastructure that exists but isn't connected:**
- `LocalInferenceEngine` — LiteRT-LM inference interface, ready to use
- Bundled route data — `segments.json` (20 gravel/named sectors), `weather.json` (hourly forecast), `route.json` (3207 GPS coords with timestamps)
- `Dependencies.inferenceEngine` — initialized but never called from LiveRide

---

## WEEK 1: MVP — Make the Copilot Actually Think

### Day 1: Teach the App About Segments and Weather

**What:** The copilot needs to know about the route's terrain segments (gravel sectors, climbs) and weather conditions. Today we create the data plumbing so the AI tools can query this information.

**Functionality:**
- Load and parse `segments.json` — 5 named sectors (e.g., "Bagnaia Gravel Sector") and 15 auto-detected gravel sections, each with distance, elevation gain, surface type, and position on route
- Load and parse `weather.json` — hourly forecast with temperature, wind, humidity, precipitation, UV index for the ride day in Tuscany
- Both available as reactive streams (Flow) so the ViewModel can observe changes

**Why it matters:** Without segment and weather data, the copilot can't answer "what's the next climb like?" or "will it rain in 2 hours?" — the two most common cyclist questions.

**Deliverables:**
- `SegmentRepository` and `WeatherRepository` interfaces in `:data`
- Android implementations reading from bundled JSON assets in `:core`
- Wired into the copilot app's dependency graph

---

### Day 2: Build the Copilot's Toolbox

**What:** Define 6 tools the AI can call to answer cyclist questions, and build the executor that runs them against real data.

**The 6 tools:**

| Tool | What it does | Example question it answers |
|------|-------------|---------------------------|
| `get_ride_status` | Returns current speed, distance, elapsed time, power | "How am I doing?" |
| `get_segment_ahead` | Finds the next terrain segment relative to current position | "What's coming up?" / "Is there gravel ahead?" |
| `get_weather_forecast` | Returns weather for current and upcoming hours | "Will it rain?" / "What's the wind like?" |
| `get_route_alternatives` | Suggests alternative route segments | "Is there a paved alternative?" |
| `find_nearby_poi` | Lists cafes, water fountains, bike shops within range | "Where's the nearest cafe?" |
| `get_rider_profile` | Returns rider's baseline metrics (FTP, weight, zones) | "What zone should I ride this climb in?" |

**How it works:** Each tool receives the current `RideContext` (where the rider is, how fast, how far) and queries the segment/weather/route repositories to build a JSON response. The AI model reads this JSON and formulates a natural language answer.

**Deliverables:**
- Tool definitions with schemas the AI model can understand
- `CyclingToolExecutor` that dispatches tool names to the right data source
- Tests with fake repositories verifying each tool returns correct data

---

### Day 3: The AI Brain — CopilotAgent

**What:** Build the core intelligence pipeline that takes a rider's question and produces a helpful, context-aware response.

**How the pipeline works:**
1. Rider asks a question (text or voice) → e.g., "What's the next gravel section like?"
2. **FunctionGemma** (tool-calling model) analyzes the question + available tools → decides to call `get_segment_ahead`
3. **Tool executor** runs the call → returns JSON with segment name, distance, elevation, surface
4. **Gemma 550M** (response model) takes the tool result + original question → generates natural language: "The Bagnaia gravel sector starts in 3km. It's 3.3km long with 159m of climbing on unpaved surface. Consider dropping to an easier gear before the entrance."

**Design decisions:**
- Start with **single-model mode** (one model handles both tool-calling and response) to avoid memory pressure and model-swap latency
- The `CopilotAgent` is designed to support two-engine mode later — just pass a second engine
- If the model decides no tool is needed (e.g., "thanks!"), it responds directly without tool calls
- All tool calls and results are logged as `ToolCallDebug` chat messages for transparency

**Fallback behavior:**
- If model isn't loaded → "Model not ready, please complete onboarding first"
- If tool call fails → respond with partial info, note what's unavailable
- If model output can't be parsed → fall back to a generic helpful response

**Deliverables:**
- `CopilotAgent` orchestrating the full pipeline
- `ToolCallParser` handling various model output formats
- System prompts tuned for cyclist-friendly responses
- Tests with fake inference engines verifying the full flow

---

### Day 4: Connect the Brain to the UI

**What:** Replace the fake "Got it! Keeping an eye on the route ahead." mock response with the real CopilotAgent pipeline.

**User-visible changes:**
- Typing a question in chat now triggers real AI inference
- While the AI is thinking, a processing indicator appears
- Tool call debug messages show what data the AI looked up (e.g., "[Tool] get_segment_ahead → Bagnaia Gravel Sector")
- The copilot's reply is contextual and based on actual route/weather data
- Error states are handled gracefully (model not loaded, timeout, inference failure)

**What happens behind the scenes:**
- `LiveRideViewModel` builds a `RideContext` from the current GPS position every time a message is sent
- The agent receives the question + ride context, runs the pipeline, returns structured response
- Each tool call becomes a `ChatMessage.ToolCallDebug` in the chat history
- The final reply becomes a `ChatMessage.Copilot`

**Deliverables:**
- LiveRideViewModel wired to CopilotAgent
- Processing state management (isProcessing flag)
- Error handling with user-friendly messages
- Updated tests covering the agent integration

---

### Day 5: Realistic GPS Playback + End-to-End Testing

**What:** Make the simulated ride feel realistic by using the route's actual timestamps instead of fixed-speed advancement.

**Current behavior:** SimulatedGpsSource advances a fixed number of route points per tick (1-8 depending on speed multiplier). This means the rider moves at constant speed regardless of terrain — unrealistic.

**New behavior:** Use the `t` (elapsed time) field from route coordinates to advance proportionally. On a steep climb, the rider slows down naturally. On a descent, they speed up. The speed multiplier scales the time, not the point count.

**Example:** If route point 100 has `t=3600` (1 hour) and point 101 has `t=3620` (20 seconds later), at 1x speed the rider takes 20 seconds to traverse that segment. At 4x speed, it takes 5 seconds. This makes the HUD speed readout match what a real cyclist would experience.

**Also adds:** `elapsedMs` to `GpsPosition` so the AI tools can answer time-relative questions ("how long until the next segment?").

**End-to-end test:** Install on device, complete the full Onboard → RideSetup → LiveRide flow. Verify:
- Map shows route and rider position advances realistically
- Chat produces real AI responses (or meaningful errors if models aren't downloaded)
- HUD metrics update with realistic values
- Playback speed multiplier affects simulation speed

**Deliverables:**
- Timestamp-based GPS playback
- `elapsedMs` in ride context for AI tools
- Manual integration test on device

---

## WEEK 2: Voice, Polish, and Nice-to-Haves

*Days 6-9 are independent — reorder based on what feels most impactful.*

### Day 6: Voice Input — Talk to Your Copilot

**What:** Let the rider ask questions by voice instead of typing (much more practical while cycling).

**User experience:**
1. Rider taps the mic button on the chat panel
2. Button shows recording indicator (pulsing animation)
3. Rider speaks: "What's the weather looking like?"
4. Speech is transcribed to text and sent as a chat message automatically
5. Copilot responds as usual (text + optional TTS on Day 7)

**Edge cases:**
- Device doesn't have speech recognition → mic button hidden
- Recognition fails (noise, unclear speech) → show error toast, let rider retry or type
- Rider taps mic while already recording → stop recording

**Deliverables:**
- `SpeechInput` interface (pure Kotlin) + Android SpeechRecognizer implementation
- `StartVoice` / `StopVoice` actions in LiveRideViewModel
- Mic button wired in ChatPanel with recording state
- `RECORD_AUDIO` permission handling

---

### Day 7: POI Markers on Map + Text-to-Speech

**What:** Show points of interest on the map and let the copilot speak its responses aloud.

**POI markers:**
- Display 10-15 relevant POIs along the Strade Bianche route: cafes, water fountains, bike shops, shelters
- Each POI appears as a colored marker on the map with an emoji label (e.g., ☕ for cafe)
- POIs are filtered by proximity — only show markers within ~5km of current position to avoid map clutter
- Tapping a POI marker could show a tooltip with the name (stretch goal)

**Text-to-Speech:**
- When the advanced setting "Auto Voice" is enabled, the copilot reads its responses aloud
- Uses Android's built-in TextToSpeech engine (no additional models needed)
- Speaks at a comfortable pace, can be interrupted by new input
- Respects the toggle — off by default, rider enables it in RideSetup advanced settings

**Deliverables:**
- `PoiRepository` with hardcoded Strade Bianche POIs
- POI filtering and mapping in LiveRideViewModel
- `TtsOutput` interface + Android implementation
- Auto-voice behavior tied to advanced settings

---

### Day 8: Real Device GPS

**What:** Enable the "Device GPS" option in RideSetup so the app works with real location data outdoors.

**User experience:**
- In RideSetup, the "Device GPS" button becomes active (currently shows "coming soon")
- When selected, the app requests location permissions on ride start
- The rider's real position is shown on the map, matched to the nearest point on the route
- All HUD metrics (speed, distance) come from real GPS data
- The copilot's tools see real position data, so answers are accurate to where the rider actually is

**How route matching works:**
- Each GPS fix is compared to route points to find the nearest one
- The `routePointIndex` advances monotonically (no backtracking) to handle GPS noise
- Distance is computed incrementally from GPS fixes, not from route points
- If the rider goes off-route by >500m, show a "off route" indicator

**Deliverables:**
- `DeviceGpsSource` using Android FusedLocationProvider
- GPS mode passed from RideSetup through navigation to LiveRide
- Location permissions handling
- RideSetup enables Device GPS option

---

### Day 9: Ride Summary Screen + Settings Persistence

**What:** Add a post-ride screen and remember user preferences between sessions.

**Ride Summary — what the rider sees after ending a ride:**
- Route name and date
- Total distance, duration, average speed
- Average power (if available from simulation)
- Number of AI interactions (questions asked, tools called)
- Chat highlights — the 3-5 most interesting copilot exchanges
- "Back to Setup" button to start another ride

**Settings Persistence — what gets remembered:**
- Last selected GPS mode (Simulation vs Device)
- Last selected playback speed
- Advanced settings toggles (Remote LLM, Developer HUD, Auto Voice)
- Stored locally using Android DataStore — survives app restarts

**Deliverables:**
- `RideSummaryViewModel` (3-layer pattern) + `RideSummaryScreen` with previews
- Navigation from LiveRide's "End Ride" to Summary
- `SettingsRepository` interface + DataStore implementation
- RideSetup loads saved settings on init

---

### Day 10: Polish, Error Handling, and Documentation

**What:** Harden the app for real-world use and update project docs.

**Error handling:**
- Agent call timeout (30s max) — if the AI takes too long, show "Taking longer than expected..." and allow cancellation
- Model not loaded gracefully — if rider skipped onboarding, show a helpful message instead of crashing
- Speech recognizer unavailable — hide mic button, no error
- Network timeout for remote LLM mode — fall back to local with a notice

**UI polish:**
- Processing indicator while AI is thinking (the `isProcessing` state already exists in UiState)
- Chat auto-scrolls to newest message
- Tool call debug messages are visually distinct (smaller font, monospace, muted color)
- Smooth transition when chat panel expands/collapses

**Documentation:**
- Update `roadmap.md` — move completed items, add new future items discovered during the sprint
- Update `architecture.md` — document new classes (CopilotAgent, tool executor, repositories)
- Update patterns if new patterns emerged

**Final verification:**
- `./gradlew test` — all unit tests pass
- `./gradlew :app:copilot:assembleRelease` — release build succeeds
- `./gradlew ktfmtFormat` — formatting compliance
- Full manual walkthrough on device

---

## Risks and Fallback Plans

| Risk | Impact | Fallback |
|------|--------|----------|
| Two-model pipeline too slow or OOM | AI responses take >10s or crash | Use single-model mode (CopilotAgent already supports it) |
| FunctionGemma output format unpredictable | Tool calls fail to parse | ToolCallParser has regex fallback; log raw output for debugging |
| SpeechRecognizer unavailable on test device | Can't demo voice input | Voice is optional; text input always works |
| Day 3 (CopilotAgent) takes longer than expected | Delays Day 4-5 | Day 4 can ship with mock agent; wire real one when ready |
| Bundled route data insufficient for good AI answers | Copilot gives shallow responses | Enrich segments.json / weather.json with more detail; adjust system prompts |

---

## Success Criteria

**MVP (end of Week 1):**
- [ ] Rider can ask a question in chat and get a contextual AI response based on actual route/weather data
- [ ] Tool calls are visible in chat debug messages
- [ ] GPS simulation advances at realistic speeds using route timestamps
- [ ] App doesn't crash during a full onboard → setup → ride → chat flow

**Complete (end of Week 2):**
- [ ] Voice input works for hands-free questioning
- [ ] POI markers visible on map
- [ ] Copilot speaks responses aloud (when enabled)
- [ ] Real GPS mode works outdoors
- [ ] Ride summary screen shows after ending a ride
- [ ] Settings persist between app launches
- [ ] All unit tests pass, release build succeeds
