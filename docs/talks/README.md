# Conference Talks - Index and Runbook

This folder contains two concept‑only talks designed around the concentric approach (WHY → HOW → WHAT), while keeping slide titles neutral. FlowForge is mentioned only on the last slide of each talk.

## Talks

- Talk A - Safe API/Schema Migration (concept‑only)
  - File: talk-a-api-migration.md
  - Abstract: Migrations often fail late. This talk shows how to encode migration intent with a small policy lattice (Exact/Backward/Forward), make incomplete jobs unbuildable with typestate, and prove compatibility at compile time. We demo a red→green migration (Exact → Backward), show PR‑friendly compile‑fail tests, and explain where runtime guardrails (DQ, lineage, idempotent edges) still apply.
  - Target audience: platform/data engineers, EMs, staff architects.

- Talk B - Types for Interface Representation & Validation (concept‑only)
  - File: talk-b-types-validation.md
  - Abstract: Tests are sampled; types can be exhaustive for shapes. This talk shows how to derive structural shapes, enforce policy compatibility at compile time, keep transforms pure and effects at the edges, and encode construction rules in types. We demo element‑optionality drift (List[Option[A]] ≠ List[A]) and typestate build(), then prove portability with a simple engine seam.
  - Target audience: engineers and architects who want stronger guarantees with types.

## Duration
- Both talks are prepared for 45 minutes including Q&A. Suggested pacing: 8–10 slides + 2 short demos + 5–8 minutes Q&A.

## Live Demo Prerequisites (for both talks)
- Laptop with JDK 17+ and sbt 1.9+
- Local clone of the repo (or a minimal demo repo) and an editor/REPL
- Prepared code snippets (copy/paste) and error screenshots as fallback
- Optional: network access if you plan to show a CI policy table screenshot from a PR

## Slide Assets (export to PNG before the event)
- Policy lattice mini‑table → export to PNG: docs/talks/assets/policy-lattice.md → policy-lattice.png
- Typestate builder diagram → export to PNG: docs/talks/assets/typestate-builder.md → typestate-builder.png
- Compile‑time flow/optionality diagrams: use repo SVGs from docs/diagrams/compile-time-contracts/

## Demo Runbooks
- Red → Green migration: docs/talks/demos/red-green-demo.md
- Typestate builder: docs/talks/demos/typestate-demo.md
- Optional quality flip: docs/talks/demos/dq-flip-demo.md

## Speaker Prep
- Use the per‑slide Speaker Notes included in each talk’s markdown.
- A condensed presenter‑cheatsheet is available at docs/talks/presenter-cheatsheet.md.

