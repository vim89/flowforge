# Conference Organizer Packet

This packet helps CFP/organizers review and run the session smoothly. The talks are concept‑only (no product until the last slide).

## Talk A - Safe API/Schema Migration with Compile‑Time Guarantees
- Duration: 45 minutes (incl. Q&A)
- Abstract: Migrations often fail late. This talk shows how to encode migration intent with a small policy lattice (Exact/Backward/Forward), make incomplete jobs unbuildable with typestate, and prove compatibility at compile time. Live demo: red→green migration (Exact → Backward), compile‑fail tests in PR, and where runtime guardrails (DQ/lineage/idempotent edges) still apply.
- Learning objectives:
  - Understand compile‑time structure checks and policy migration
  - Recognize the separation of compile‑time vs runtime safety
  - Apply a 3‑step migration playbook in code and CI

## Talk B - Types for Interface Representation & Validation
- Duration: 45 minutes (incl. Q&A)
- Abstract: Tests are sampled; types can be exhaustive for shape/policy compatibility. We derive shapes, enforce policy at compile time, keep transforms pure and effects at the edges, and encode construction rules in types. Live demo: element‑optionality drift (List[Option[A]] vs List[A]) and typestate build().
- Learning objectives:
  - Build an intuition for inline/quotes (Scala 3) and structural derivation
  - Separate pure/effectful work for faster feedback & safer retries
  - Encode construction rules with types and prove shape compatibility

## Demo prerequisites
- Speaker laptop with JDK 17+, sbt 1.9+
- Local editor/REPL
- Prepared code snippets & screenshots in case of projector issues
- Optional: Wi‑Fi if showing a CI PR page (not required)

## AV requests
- 1080p+ projector, dark theme
- Code font at least 24–28 pt; error text large enough to read from the back
- Presenter display (timer preferred)

## Speaker bio (placeholder)
- One‑liner: [Your Name] builds reliable data systems and teaches type‑driven design.
- 100‑word: [Your Name] is a staff‑level engineer focused on type‑driven data platforms. They’ve led teams across schema migrations, contract governance, and runtime reliability. They speak about moving failures left, pragmatic FP, and developer ergonomics.

