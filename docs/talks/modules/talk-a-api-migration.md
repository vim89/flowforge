# Talk A - Safe API/Schema Migration (Concept‑Only)

> MODULE NOTE (not part of ScalaIO deck): This is a standalone, concept‑only talk. Do not merge into ScalaIO-2025-Main-Talk.md. See docs/talks/INDEX.md for scope.

> WHY → HOW → WHAT. Inside‑out. No product names until the last page.
> _Use this as a deep-dive module supporting the ScalaIO 2025 main deck (`ScalaIO-2025-Main-Talk.md`)._

## Title
- Safe API & Schema Migration with Compile‑Time Guarantees
- Subtitle: Move failures left. Sleep through Friday nights.

## Audience
- Platform/data engineers, EMs, staff architects migrating interfaces under load.

## S01 - Beliefs & Stakes (2 min)
Design tip: use opening-bumper.svg full-bleed as Slide 1.
Speaker notes [Category: WHY]: Open with beliefs, then the 45-second Friday story. Frame the migration goal as “compile‑time proof or we don’t ship.”
- Schema drift often hides silently; fix it before it ships.
- The compiler should stop broken rollouts before they start.
- Story (tell it verbatim):
  - “Upstream silently renamed `amount` to `amt`. The job didn’t crash—it wrote nulls for weeks. We found it at month‑end close and backfilled millions of rows.”
  - “If the build had failed on that schema diff, the change wouldn’t have shipped and none of that rework would exist.”

## S02 - Boundaries We Must Name (1 min)
- Compile‑time vs Runtime: shapes/policies vs corrupt files/SLA breaches.
- DX vs Process: fast local red→green vs CI gates/policy checks.
Design tip: two columns; one-line contrast each; use icons.
Speaker notes [Category: HOW]: Define terms to prevent confusion during Q&A. Say: compile‑time prevents drift; runtime mitigates reality.

## S03 - Migration Policies on One Slide (4 min)
- Exact | Backward | Forward | Full.
- Modifiers: Ordered, Case‑Insensitive, By‑Position.
- Visual: policy lattice mini‑table (see assets/policy-lattice.svg).
Design tip: embed small table PNG; use ✅/⚠/❌ glyphs.
Speaker notes [Category: HOW]: Don’t read the grid; demonstrate later. Mention Ordered/CI/By‑Position briefly.

## S04 - Migration Playbook (3 min)
- Critical paths: Exact.
- Rollouts: Backward (producer adds optional/default) or Forward (consumer tolerates extras).
- Sunset: policy tighten back to Exact.
Design tip: 3-step arrows (Exact → Backward/Forward → Exact).
Speaker notes [Category: HOW]: Emphasize “sunset back to Exact.” Mention contracts as code; version and generate types.

## S05 - Construction with Types (2 min)
- Build() only from Complete state.
- Incomplete pipelines/jobs are unbuildable by construction.
Design tip: embed typestate-builder.svg; green highlight on Complete.
Speaker notes [Category: HOW]: Name the required state (“Complete”) when reading the error. Illegal states unrepresentable.

## S06 - Live Demo (7 min)
- Red→Green: Exact fails on drift → relax to Backward → compiles.
- Show compiler error: Missing/Extra/Mismatch with path rendering.
- Show CI policy table and a compile‑fail test in PR.
Design tip: split screen code (left) and compiler output (right).
Speaker notes [Category: WHAT]: Read one line from the error (“Missing attributes: …”). Keep to ~4 min total; show CI policy table screenshot.

## S06a - Boundary Checkpoint (1 min)
- Compile‑time stops schema drift and incomplete builds.
- Runtime still handles corrupt files, SLA breaches, retries, and lineage.
- DX = fast local red→green. Process = CI policy gate + compile‑fail PR check.
Design tip: bold the first sentence; use contrasting icons (shield vs heartbeat).
Speaker notes [Category: HOW → WHY bridge]: Say explicitly: “The compiler keeps us out of Friday night incidents; runtime guardrails catch the world’s messiness.”

## S08a - Boundary Checkpoint (Recap, 1 min)
- Compile‑time stops drift and illegal construction.
- Runtime handles corrupt files, SLA breaches, retries, lineage.
- DX = fast local red→green. Process = CI policy gates + compile‑fail PR checks.
Design tip: reuse icons from S06a to signal reinforcement.
Speaker notes: Read the three bullets aloud before advancing to S08b.

## S07 - Minimal Toolkit (4 min)
- Templates that encode the playbook.
- Compile‑fail tests as gates.
- Optional quality checks; programmatic toggles.
Design tip: checklist with 4–5 ticks; no more.
Speaker notes [Category: WHAT]: Keep this short; ideas already landed. Invite conversation during Q&A.

## S08 - Outcome Takeaways (1 min)
- Sleep through Friday deploys because drift dies at compile time.
- Shorten MTTR: compiler errors pinpoint the exact field/path that changed.
- Protect SLAs: retries are fiber‑safe and idempotent at the edges.
Design tip: big outcome statements; no jargon nouns.
Speaker notes [Category: WHY]: Repeat “We protect on-call sleep and customer trust” before advancing.

## S08b - Remember Why (45 sec)
- We do this to keep traders, analysts, and on-call engineers out of midnight war rooms.
- Compile gates buy confidence; CI policies formalise hand-offs.
- The framework exists because migrations should be boring and safe.
Design tip: reuse concentric-circle graphic; highlight inner circle.
Speaker notes [Category: WHY]: Invite the audience to map this to their worst rollout before Q&A.

## S09 - Q&A (3 min)

---

## One‑Slide Product Note (last page)
- We’ve built a framework that embodies these ideas.
- Try it, share feedback, and contribute:
  - Website/Repo link
  - Quickstart (template, red→green demo)
  - Community channel
---

## Slides (copy/paste ready, one line per bullet)

### WHY
- We lose sleep to drift discovered after deploy; migrations should fail at compile‑time.
- Effects at the edges; retries don’t duplicate side‑effects.
- Fast local feedback over expensive midnight pages.

### Boundaries (compile‑time vs runtime; DX vs process)
- Compile‑time: structures, policies, construction rules. Runtime: DQ, lineage, retries.
- DX: red→green locally. Process: CI policy gates + compile‑fail in PR.

### Policy lattice (tiny table on slide)
- Exact | Backward | Forward | Full; modifiers: Ordered, CI, By‑Position.
- Missing/Extra/Type/Order: encoded as ✅/⚠/❌ in mini‑table.

### Migration playbook
- Critical paths = Exact; rollout = Backward/Forward; sunset back to Exact.
- Contracts as code; version and generate SDK/shapes as needed.

### Typestate builder
- build() only from Complete; illegal states unrepresentable.

### Demo (Red→Green)
- Strict evidence → compile error; relax to migration policy → compiles; show CI gate + compile‑fail test.

### Boundary checkpoint
- Compile‑time blocks drift/incomplete builds. Runtime covers corruption, SLAs, retries. DX = local red→green; Process = CI policy gate.

### WHAT (minimal)
- Templates encode the playbook; compile‑fail tests as PR gates; optional quality modes.

### Outcome takeaways
- Sleep through Friday deploys. Compiler diffs slash MTTR. Policy-driven rollouts stay reversible.

### Remember why
- Protect sleep, trading hours, and stakeholder trust. Compile gates + CI policies keep migrations boring.

### Last slide (product)
- One page only: try link + quickstart + contribute.

---

## Speaker Notes (brief prompts)
- WHY: open with beliefs; then the Friday night story (≈45s).
- Boundaries: 1 line each; avoid deep dives.
- Lattice: point to table; audience doesn’t need to memorize-playbook + demo will cement it.
- Demo: keep compiler error visible; read key line (“Missing attributes: …”).
- Runtime slide: stress idempotency at edges; compile‑time != runtime perfection.
- Outcome slides: tie bullets to sleep saved, MTTR cut, risk avoided; finish with “Remember Why” before Q&A.
---

## Presenter Cheatsheet (condensed)
- Timing (45 min): Beliefs (3) + Boundaries (2) + Policies (6) + Playbook (4) + Typestate (3) + Demo (10) + Toolkit (4) + Takeaways+Q&A (13)
- One‑liners: If it compiles, contracts align. Pure inside, effects at the edges.
- Boundaries: Compile‑time prevents drift; runtime handles anomalies; DX vs Process separation.
- Demos: split code/error; read one error line; screenshot fallback.
