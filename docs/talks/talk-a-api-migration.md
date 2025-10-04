# Talk A — Safe API/Schema Migration (Concept‑Only)

> WHY → HOW → WHAT. Inside‑out. No product names until the last page.

## Title
- Safe API & Schema Migration with Compile‑Time Guarantees
- Subtitle: Move failures left. Sleep through Friday nights.

## Audience
- Platform/data engineers, EMs, staff architects migrating interfaces under load.

## S01 - Beliefs & Stakes (2 min)
Design tip: use opening-bumper.svg full-bleed as Slide 1.
Speaker notes [Category: WHY]: Open with beliefs, then the 20–30s Friday story. Frame the migration goal as “compile‑time proof or we don’t ship.”
- Runtime schema drift burns nights and weekends.
- The compiler should stop broken rollouts before they start.
- Story: “A partner team removed a nullable column late Friday. Without a safe rollback, both teams were up all night before markets opened.”
 

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

## S07 - Minimal Toolkit (4 min)
- Templates that encode the playbook.
- Compile‑fail tests as gates.
- Optional quality checks; programmatic toggles.
Design tip: checklist with 4–5 ticks; no more.
Speaker notes [Category: WHAT]: Keep this short; ideas already landed. Invite conversation during Q&A.

## S08 - Takeaways (1 min)
- If it compiles, contracts align.
- Types for migration intent; policies encode allowed change.
- Build guardrails into templates and CI.
Design tip: big one-liners; leave time for Q&A.
Speaker notes [Category: WHY]: Repeat belief bumper once; move to Q&A.

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

### Runtime guardrails (one slide)
- Quality checks, lineage, metrics; idempotent edges.

### WHAT (minimal)
- Templates encode the playbook; compile‑fail tests as PR gates; optional quality modes.

### Takeaways
- If it compiles, contracts align. Policies encode migration intent. Guardrails in CI.

### Last slide (product)
- One page only: try link + quickstart + contribute.

---

## Speaker Notes (brief prompts)
- WHY: open with beliefs; then the Friday night story (20–30s).
- Boundaries: 1 line each; avoid deep dives.
- Lattice: point to table; audience doesn’t need to memorize—playbook + demo will cement it.
- Demo: keep compiler error visible; read key line (“Missing attributes: …”).
- Runtime slide: stress idempotency at edges; compile‑time != runtime perfection.
- Takeaways: repeat ONE bumper line; then move to Q&A.
---

## Presenter Cheatsheet (condensed)
- Timing (45 min): Beliefs (3) + Boundaries (2) + Policies (6) + Playbook (4) + Typestate (3) + Demo (10) + Toolkit (4) + Takeaways+Q&A (13)
- One‑liners: If it compiles, contracts align. Pure inside, effects at the edges.
- Boundaries: Compile‑time prevents drift; runtime handles anomalies; DX vs Process separation.
- Demos: split code/error; read one error line; screenshot fallback.
