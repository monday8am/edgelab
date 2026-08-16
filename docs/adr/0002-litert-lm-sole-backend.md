# LiteRT-LM is the sole on-device inference backend for EdgeLab

"Sole" means **on-device only**: no llama.cpp, MediaPipe LLM, MNN, or other local runtime. LiteRT-LM keeps its place because it makes function-calling testing easy and that is the app's entire purpose.

A **cloud** inference backend is permitted — separately — for the Playground's onboarding leg: let a dev reach the Playground with zero download (online model), learn the game, then switch to a local `.litertlm` once they understand what the app does and want the on-device experience. This is *not* a second on-device engine; it's a different category (cloud), used in service of the on-device thesis, not against it.

This is an accepted constraint, not a deficiency on the backlog. Only `.litertlm` artifacts run on-device; non-litert models are out of scope *for this app* — producing a `.litertlm` artifact from them is a different part of the project's job.

Considered and rejected:
- **Multi on-device backend abstraction now.** Would trade the thing that makes tool-calling easy for generality we don't need. Re-evaluate only if litert-lm tool-calling support regresses or a second *on-device* backend clearly wins on the function-calling job.
- **Harvesting all HF `.litertlm` models at runtime.** Surfaces 7B/9B artifacts that saturate a phone and refutes the "models too big" problem. The catalog stays curated + size-bounded; bring-your-own via HF auth covers the dev who wants what isn't curated.

Open sub-decision (grill Q9.5): which cloud provider and whose key funds the onboarding traffic. BYOK is a later iteration; day-one leans keyless/free-tier or a maintainer-funded proxy because expected traffic is very low.

Consequences: the Model Catalog is gated to `.litertlm` for on-device play; "add Gemma 4 on-device" waits on a `.litertlm` artifact existing; P5 ("only one engine") stays retired — it was always about on-device engines.