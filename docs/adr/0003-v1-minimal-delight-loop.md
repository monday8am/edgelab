# v1 = Minimal Delight Loop; full plan preserved

The `/grill-with-docs` session produced a whole plan for reorienting EdgeLab into a function-calling Playground (cloud onboarding leg, Gemini proxy, preset library, paste-import, N-Probe authoring, annotated Trace, per-upload Benchmark, Test-Suite demotion). That's a lot for a "small plan." We resolve the tension by separating *plan completeness* from *execution start*: the whole plan is the handoff artifact (`docs/edgelab/plan.md`); execution starts with the Minimal Delight Loop.

v1 ships one screen (Playground) + one backend (cloud Gemini via a Firebase proxy) + one authoring path (preset library, seeded from `data/.../tool_tests.json`) + one result (annotated Trace). Local-model download, paste-import, Benchmark, and Test-Suite reskin are later phases that compose on top without rewriting v1.

Chosen to (1) keep the plan honestly "small" — one increment, fully demonstrable end-to-end; (2) de-risk the cloud onboarding backend (the riskiest unknown, deferred-impl) in isolation, before layering local-model complexity, so a hard/costly proxy surfaces at v1.0.0 not after also wiring local download; and (3) get the Trace contract in front of real devs ASAP, since "results aren't clear" feedback is the highest-signal input on whether annotations actually work.

Rejected:
- **Full Playground prime path as v1** (cloud + local download + paste-import in one release) — front-loads two large unknowns into one ship.
- **Everything grilled as v1** — no longer small; Benchmark is a side-effect the dev doesn't enter, so it shouldn't gate the primary mode's ship.