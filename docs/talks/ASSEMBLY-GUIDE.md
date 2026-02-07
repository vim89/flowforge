# ScalaIO 2025 – Talks Assembly Guide (Single Source of Truth)

Purpose: remove confusion when building the Google Slides deck and rehearsing. This guide explains what each file is for, how the pieces relate to the organizer pitch, and the exact steps to assemble the final deck.

## What Each File Is For

- ScalaIO-Paris-2025-Talk-Description-Pitch.md
  - Organizer-facing abstract/pitch. Frozen; do not edit.
- ScalaIO-2025-Main-Talk.md
  - Canonical slide outline for the ScalaIO talk (contracts + fiber-safe execution). This is the content you present.
- speaker-notes-ScalaIO-2025.md
  - What you say out loud. Detailed notes aligned to S01…S16.
- timed-outline.md
  - Minute-by-minute pacing for rehearsal; references slide IDs.
- modules/talk-a-api-migration.md
  - Standalone module (concept-only): schema/API migration with policies, CI gates.
- modules/talk-b-types-validation.md
  - Standalone module (concept-only): types for representation/validation, typestate, effect boundary.
- internal/review-2025-10-13.md
  - Internal brutal review + WHY-first addendum. Not for slides.
- google-slides-prep.md, demo-checklist.md, presenter-cheatsheet.md
  - Practical checklists for building the deck and demos.
- appendix-engine-portability.md, assets/
  - Optional appendix material and images.
- README.md, organizers.md, Managers-Summary.md
  - Misc meta/admin; not slide text.

## Answers to Common Questions

1) Why is there a “Main Talk” and separate Talk A/B?  
   - The ScalaIO presentation is the Main Talk. Talk A and Talk B are reusable, concept-only modules for other venues or deep dives. Keeping them separate prevents scope creep in the ScalaIO deck.

2) Where is “fiber-safe data pipelines” covered?  
   - Main Talk: S09 (effect boundary with Kleisli) and S10 (fiber-safe execution with Cats Effect + ZIO).

3) How does the Main Talk align with the organizer pitch?  
   - Mapping appears in the “Pitch ↔ Slides Mapping” section below.

## Pitch ↔ Slides Mapping

- Compile-time contracts / “compiler refuses broken migrations” → S03 (policy lattice), S04 (compile-time evidence), S05 (red→green demo)
- Fiber-safe execution (Cats Effect + ZIO) → S10
- Giter8 templates → S06
- Refined types for config validation → S07
- Cats ValidatedNel for multi-rule data quality → S08
- Type classes / pluggability → S11/S12 summary and appendix
- Kyo/Caprese mention → optional 30s callout in S10
- Trait-based runners (Spark/Flink) → S12
- Runtime DQ & lineage → S13

## Build the Single Google Slides Deck (Granular Steps)

1) Create slides in this order (one per section unless noted):
   - S01a About me (45s)
   - S01b About Walmart (45s)
   - Title → ScalaIO-2025-Main-Talk.md:5–8
   - S01 Beliefs & stakes (use amount→amt story) → :10–15; script: speaker-notes-ScalaIO-2025.md S01
   - S02 Boundaries → :17–21; script: S02
   - S02a Boundary Checkpoint (mini) → :23–26; script: S02a
   - S03 Policy lattice → :28–31
   - S04 Compile-time evidence → :33–36
   - S05 Red→Green demo (screenshots) → :38–44; demo playbook 2.1
   - S06 Templates & golden path → :46–50
   - S07 Config safety (refined + ValidatedNel) → :52–56
   - S08 Functional validation & DQ → :58–61
   - S09 Effect boundary with Kleisli → :63–67
   - S10 Fiber-safe execution & effect options → :69–73
   - S11 Batteries included (brisk) → :75–80
   - S12 Engine portability seam (appendix pointer) → :82–85
   - S13 Runtime guardrails → :87–91
   - S13a Boundary Checkpoint (recap) → :93–96; script: S13a
   - S14 Outcome takeaways (outcome-only) → :98–101
   - S15 Remember why → :103–107
   - S16 Invitation → :109–112

Important: Do not put the words “WHY”, “HOW”, or “WHAT” on slide text. Those are speaking cues only. The slide content should be self‑explanatory; the WHY/HOW/WHAT framing lives in speaker notes and the audit doc.

2) Insert visuals only where they reinforce the slide’s message (policy lattice, concentric WHY→HOW→WHAT circles). Avoid dense “noun piles”.

3) Prepare demos using demo-checklist.md and speaker-notes 2.x “Demo Playbook”. Keep a screenshot fallback slide hidden.

4) Rehearse with timed-outline.md. If time slips, trim S05 and S11 commentary, but never drop S02a/S13a or S14/S15.

## Why This Organization

- Single source of truth for the ScalaIO talk (Main Talk) prevents content drift.
- Separate concept-only decks (Talk A/B) protect the ScalaIO narrative from scope creep.
- Repeated boundary slides (S02a, S13a) intentionally reduce compile-time vs runtime and DX vs Process confusion in Q&A.

## Acceptance Criteria for “Deck Ready”

- Slides present in the order above; S02a and S13a included and rehearsed.
- Speaker notes aligned; story uses amount→amt incident.
- Outcome-only close (no feature nouns) present (S14) and “Remember Why” slide present (S15).
- Demo rehearsed ≤ 90s with screenshot fallback.
