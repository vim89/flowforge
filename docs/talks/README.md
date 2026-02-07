# Conference Talks – Index & Runbook

This directory now centres on the single ScalaIO Paris 2025 deck described in `docs/talks/ScalaIO-2025-Main-Talk.md`. The main talk we deliver publicly follows the concentric WHY → HOW → WHAT approach and aligns with the organizer brief (`docs/talks/ScalaIO-Paris-2025-Talk-Description-Pitch.md`). FlowForge is mentioned only on the closing invitation slide.

## Primary Deck

- **ScalaIO 2025 – Compile-Time Contracts & Fiber-Safe Pipelines**
  - File: `docs/talks/ScalaIO-2025-Main-Talk.md`
  - Focus: Compile-time contracts, policy-driven migrations, effect boundaries, configuration safety, effect polymorphism (Cats Effect + ZIO), engine portability, and runtime guardrails.
  - Duration: 45 minutes including Q&A. Suggested pacing: WHY (5) → HOW demos (28–30) → Outcome & Remember Why (3) → Q&A (7–9).

## Concept Modules (Internal Reference Only)

- **Talk A – Safe API/Schema Migration** (`modules/talk-a-api-migration.md`)
- **Talk B – Types for Interface Representation & Validation** (`modules/talk-b-types-validation.md`)

Use these as deep-dive modules when we need to expand on migrations or structural typing. They are not standalone conference decks anymore; reference them when custom workshops require additional content.

## Live Demo Prerequisites
- Laptop with JDK 17+, sbt 1.9+
- Local clone of FlowForge (or prepared demo repo) and editor/REPL
- Pre-copied code snippets and compiler-error screenshots
- Optional: network access to show CI policy snapshots

## Slide Assets (export before event)
- Policy lattice: `docs/talks/assets/policy-lattice.md` → `policy-lattice.png`
- Typestate builder: `docs/talks/assets/typestate-builder.md` → `typestate-builder.png`
- Compile-time flow/optionality diagrams: reuse SVGs in `docs/diagrams/compile-time-contracts/`

## Demo Runbooks
- Red → Green migration: `docs/talks/demos/red-green-demo.md`
- Typestate builder: `docs/talks/demos/typestate-demo.md`
- Optional quality flip: `docs/talks/demos/dq-flip-demo.md`

## Speaker Prep
- Use slide-level notes in `ScalaIO-2025-Main-Talk.md`.
- Presenter cheatsheet: `docs/talks/presenter-cheatsheet.md`.
