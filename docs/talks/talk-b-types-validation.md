# Talk B - Types for Interface Representation & Validation (Concept‑Only)

> WHY → HOW → WHAT. Inside‑out. No product names until the last page.

## Title
- Types that make pipelines unbuildable in the wrong shape
- Subtitle: Exhaustive guarantees for structure; tests for behavior

## Audience
- Engineers and architects who want stronger guarantees with types

## S01 - Beliefs & Stakes (2 min)
Design tip: use opening-bumper.svg full-bleed as Slide 1.
Speaker notes [Category: WHY]: Connect types→exhaustive guarantees (for structure/policy) vs tests→behavior. Use an example (“changing an element from optional to required should not slip through CI”).
- Tests are sampled; types can be exhaustive for shapes and policy compatibility.
- Compile‑time errors are cheaper than nighttime pages.
 

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

## S07 - Minimal Toolkit (4 min)
- Batteries: contracts, builders, engine abstraction, optional quality
- Templates & compile‑fail tests for teams
Design tip: minimal checklist; no dense bullets.
Speaker notes [Category: WHAT]: Keep it short and move to takeaways; this is reinforcement.

## S08 - Takeaways (1 min)
- Let the compiler prove shape compatibility
- Keep transforms pure; push effects to the edges
- Encode construction rules in types

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

### Engine abstraction (proof)
- One interface, two engines; two call‑sites without changing job code.

### WHAT (minimal)
- Batteries: contracts, builder, engine seam, optional quality. Templates + compile‑fail tests for teams.

### Takeaways
- Let the compiler prove shape compatibility; keep transforms pure; encode construction rules.

### Last slide (product)
- One page only: try link + quickstart + contribute.

---

## Speaker Notes (brief prompts)
- WHY: “exhaustive for structures, sampled for behavior”-repeat once later.
- Optionality: call out List[Option[A]] ≠ List[A]; people remember this example.
- Typestate: name the state you need (“Complete”) when reading the error.
- Effect boundary: say “pure inside; effects at edges” verbatim; it sticks.
- Engine abstraction: no deep dive-just the seam exists; portability proven.
---

## Presenter Cheatsheet (condensed)
- Timing (45 min): Beliefs (3) + Boundaries (2) + HOW (12–15) + Demo (10–12) + Toolkit (3–4) + Takeaways+Q&A (8–10)
- One‑liners: If it compiles, contracts align. Pure inside, effects at the edges.
- Boundaries: Compile‑time prevents drift; runtime handles anomalies; DX vs Process separation.
- Demos: keep error text large; screenshot fallback; under 6 minutes total if compressed.
