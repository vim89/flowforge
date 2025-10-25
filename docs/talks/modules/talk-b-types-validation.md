# Talk B - Types for Interface Representation & Validation (Concept‑Only)

> MODULE NOTE (not part of ScalaIO deck): This is a standalone, concept‑only talk. Do not merge into ScalaIO-2025-Main-Talk.md. See docs/talks/INDEX.md for scope.

> WHY → HOW → WHAT. Inside‑out. No product names until the last page.
> _Use this as a deep-dive module supporting the ScalaIO 2025 main deck (`ScalaIO-2025-Main-Talk.md`)._

## Title
- Types that make pipelines unbuildable in the wrong shape
- Subtitle: Exhaustive guarantees for structure; tests for behavior

## Audience
- Engineers and architects who want stronger guarantees with types

## S01 - Beliefs & Stakes (2 min)
Design tip: use opening-bumper.svg full-bleed as Slide 1.
Speaker notes [Category: WHY]: Connect types→exhaustive guarantees (for structure/policy) vs tests→behavior. Use the expanded optionality story so the audience feels the stakes.
- Tests are sampled; types can be exhaustive for shapes and policy compatibility.
- Compile‑time errors are cheaper than multi‑week backfills.
- Story (tell it verbatim):
  - “Upstream silently renamed `amount` to `amt`. Our transformation kept compiling and running—but wrote nulls for weeks. We discovered it at month‑end reconciliation and had to backfill millions of rows.”
  - “If the compiler had enforced the contract at build time, the drift would never have shipped.”

## S02 - Boundaries We Must Name (1 min)
- Compile‑time: structural compatibility/policies; builder typestate
- Runtime: corrupt/empty inputs, SLA breaches → quality checks, lineage, retries
- DX vs Process: local fast loop vs CI gate
Design tip: two-column contrast; icons for compile vs runtime.
Speaker notes [Category: HOW]: Set expectations. Say out loud: “Compile‑time prevents drift; runtime manages the world’s messiness: corrupt files, SLAs, retries.” Revisit once after demos.

## S03 - From Types to Shape (4 min)
- Derive a structural description (fields/types/defaults/optional)
- Compare Out vs Contract using policy lattice
- Visuals: flowchart.svg + optionality.svg (field vs element optionality)
Design tip: two pics side-by-side; annotate with 2–3 words each.
Speaker notes [Category: HOW]: Describe the idea of deriving field names/types/defaults/optional and comparing shapes under a policy. No macro internals.

## S04 - Construction with Types (3 min)
- Build() exists only when required parts are present
- Fewer illegal states; simpler error surfaces
Design tip: typestate-builder.svg; spotlight Complete.
Speaker notes [Category: HOW]: Explain that build() only exists from Complete; show how the compiler becomes a construction tutor.

## S05 - Pure Inside, Effects at the Edges (3 min)
- Left: pure transforms (`map`, `filter`)
- Right: effectful edges (`read`, `write`, `stream`)
- Benefits: fast unit tests, explicit resources, fewer flakes
Design tip: split slide (left pure ops; right effect ops).
Speaker notes [Category: HOW]: Emphasize fast tests, explicit resource lifecycles, fewer flakes. Call out idempotency for retries.

## S06 - Live Demo (8 min)
- Element optionality drift: `List[Option[Int]]` vs `List[Int]` → compile‑time error (“optional vs non‑optional”)
- Typestate compile‑error: build() before sink; add sink → success
- Optional: flip a validation mode flag to show policy flexibility
Design tip: code left, error/right outputs right; keep font large.
Speaker notes [Category: WHAT]: For optionality, read the mismatch (“optional Int vs Int”). For typestate, read the required state (“Complete”). Keep to 3 minutes each; show a screenshot if the projector blurs text.

## S06a - Boundary Checkpoint (1 min)
- Compile‑time blocks structural drift and illegal builder states.
- Runtime still covers corrupt payloads, SLAs, retries, and lineage obligations.
- DX = fast local loop. Process = CI gate + compile‑fail PR check.
Design tip: reuse icons from Talk A for consistency; bold the first line.
Speaker notes [Category: HOW → WHY bridge]: Say explicitly: “The compiler keeps us from re-living that optionality outage; runtime guardrails still catch production realities.”

## S08a - Boundary Checkpoint (Recap, 1 min)
- Compile‑time stops drift and illegal construction.
- Runtime handles corrupt payloads, SLA breaches, retries, lineage.
- DX = fast local red→green. Process = CI policy gates + compile‑fail PR checks.
Design tip: reuse icons from S06a to signal reinforcement.
Speaker notes: Read the three bullets aloud before advancing to S08b.

## S07 - Minimal Toolkit (4 min)
- Batteries: contracts, builders, policy toggles, typed DQ seams.
- Templates & compile‑fail tests teams can clone.
Design tip: minimal checklist; no dense bullets.
Speaker notes [Category: WHAT]: Keep it short and move to takeaways; this is reinforcement.

## S08 - Outcome Takeaways (1 min)
- Kill schema drift before runtime by proving shapes exhaustively.
- Protect SLAs: retries are fiber‑safe and idempotent at the edges.
- Shorten MTTR: compiler errors pinpoint the exact field/path that changed.
Design tip: outcomes only; no noun piles.
Speaker notes [Category: WHY]: Repeat “This is how we keep incident response daylight hours” before advancing.

## S08b - Remember Why (45 sec)
- We believe pipelines should fail in IDEs, not at 02:00.
- Compile gates protect sleep; explicit effect boundaries keep reruns safe.
- Join us if you refuse to debug optionality bugs in prod again.
Design tip: display concentric circles with WHY highlighted; fade HOW/WHAT.
Speaker notes [Category: WHY]: Invite the room to recall their worst schema surprise.

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
- Tests are sampled; types can be exhaustive for structures and policies.
- Compile‑time errors are cheaper than nighttime pages.

### Boundaries (compile‑time vs runtime; DX vs process)
- Compile‑time: structures, policies, typestate. Runtime: DQ, lineage, retries.
- DX: fast local loop. Process: CI policy gate + compile‑fail PR checks.

### From types to shape (visual)
- Derive structural shape (fields/types/defaults/optional); compare Out vs Contract.
- Use flowchart.svg and optionality.svg.

### Typestate for construction
- build() only when complete; illegal states unrepresentable.

### Pure vs effectful
- Pure transforms (map/filter) vs effectful edges (read/write/stream, DQ, lineage).
- Benefits: fast tests; explicit resources; fewer flakes.

### Demo (Option drift + typestate)
- List[Option[Int]] vs List[Int] → compile‑time error; then show build() before/after sink.

### Boundary checkpoint
- Compile‑time blocks drift + illegal builders. Runtime covers corruption, SLAs, retries. DX = local loop; Process = CI gate + compile‑fail PR.

### WHAT (minimal)
- Batteries: contracts, builder, policy toggles, typed DQ seam. Templates + compile‑fail tests for teams.

### Outcome takeaways
- Kill schema drift pre-runtime. Keep retries safe. Shorten incident response with precise compiler errors.

### Remember why
- We protect sleep and customer trust by making pipelines fail in the IDE, not prod.

### Last slide (product)
- One page only: try link + quickstart + contribute.

---

## Speaker Notes (brief prompts)
- WHY: “exhaustive for structures, sampled for behavior”-repeat once later.
- Optionality: call out List[Option[A]] ≠ List[A]; people remember this example.
- Typestate: name the state you need (“Complete”) when reading the error.
- Effect boundary: say “pure inside; effects at edges” verbatim; it sticks.
- Boundary checkpoint slide: restate compile-time vs runtime vs process to defuse Q&A confusion.
---

## Presenter Cheatsheet (condensed)
- Timing (45 min): Beliefs (3) + Boundaries (2) + HOW (12–15) + Demo (10–12) + Toolkit (3–4) + Takeaways+Q&A (8–10)
- One‑liners: If it compiles, contracts align. Pure inside, effects at the edges.
- Boundaries: Compile‑time prevents drift; runtime handles anomalies; DX vs Process separation.
- Demos: keep error text large; screenshot fallback; under 6 minutes total if compressed.
