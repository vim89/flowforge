# Run-of-Show – ScalaIO 2025 (One Page)

Talk: Compile-Time Contracts & Fiber-Safe Data Pipelines
Duration: 45 min talk + 10 min Q&A

## Schedule (target end times)

| Slide | Segment                                 | Start → End |
|-------|------------------------------------------|-------------|
| S01   | Beliefs & stakes (amount→amt story)      | 00:00 → 03:00 |
| S02   | Boundaries (CT vs RT; DX vs Process)     | 03:00 → 04:30 |
| S02a  | Boundary checkpoint (read verbatim)      | 04:30 → 05:15 |
| S03   | Policies: Why contracts first            | 05:15 → 08:15 |
| S04   | Compile-time evidence                    | 08:15 → 11:45 |
| S05   | Demo: Red→Green (cap 90s)                | 11:45 → 15:15 |
| S06   | Templates & golden path                  | 15:15 → 17:15 |
| S07   | Config safety (Refined + ValidatedNel)   | 17:15 → 19:00 |
| S08   | Functional DQ (ValidatedNel)             | 19:00 → 21:00 |
| S09   | Effect boundary with Kleisli             | 21:00 → 24:00 |
| S10   | Fiber-safe execution (CE/ZIO)            | 24:00 → 27:00 |
| S11   | Batteries included (checklist)           | 27:00 → 28:30 |
| S12   | Engine portability seam                  | 28:30 → 29:30 |
| S13   | Runtime guardrails                       | 29:30 → 30:30 |
| S13a  | Boundary checkpoint (read verbatim)      | 30:30 → 31:15 |
| S14   | Outcome takeaways                        | 31:15 → 32:15 |
| S15   | Remember why                             | 32:15 → 33:00 |
| S16   | Invitation + Q&A transition              | 33:00 → 35:00 |
| —     | Buffer                                   | 35:00 → 45:00 |
| —     | Q&A                                      | 45:00 → 55:00 |

## Anchor Phrases (use verbatim)
- After S02a: “Keep these two boundaries in mind; everything maps back to them.”
- Before S05: “We’ll break it on purpose and show the compiler stop it.”
- After S05: “That failure just moved left—from runtime to compile/build time.”
- Before S10: “Pure inside; effects at the edges—now let’s make retries fiber‑safe.”
- Before S14: “Let’s land the outcomes—what you actually get back.”

## Cut List (apply in order if behind)
- Cut A (−60s): Drop Deequ mention in S08; keep ValidatedNel.
- Cut B (−60–90s): S11 → one sentence: “tests, CLI, connectors, lineage—batteries included.”
- Cut C (−60s): Remove Kyo/Caprese callout in S10.
- Cut D (−45–60s): S12 → title + one sentence + appendix pointer.
- Cut E (−60s): S07 → no code; say “typed decoders + refined + aggregated validation”.
- Never cut: S02a, S13a, S14, S15. If tight, shorten S16 invite to one sentence.

## Demo Checklist (S05)
- Cap at 90 seconds; if hiccup, switch to screenshot fallback immediately.
- Show failure with Exact → relax to Backward → show success; narrate “moved left”.

## Boundary Checkpoint Lines (read aloud)
- “Compile‑time stops drift and illegal construction.”
- “Runtime handles corrupt files, SLA breaches, retries, lineage.”
- “DX = fast local red→green. Process = CI policy gates + compile‑fail PR checks.”

