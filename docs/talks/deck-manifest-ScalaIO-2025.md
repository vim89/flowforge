# Deck Manifest – ScalaIO 2025 (Slide-by-Slide Source Map)

Use this manifest to build or audit the Google Slides deck. Each entry shows: slide id, title, purpose, source text, speaker notes, and typical assets.

## S00 – Title
- Title: Compile-Time Contracts & Fiber-Safe Data Pipelines
- Purpose: Set belief and scope in one line
- Source: ScalaIO-2025-Main-Talk.md:5–8
- Notes: speaker-notes-ScalaIO-2025.md (Title preamble)
- Assets: concentric WHY→HOW→WHAT circles (optional)

## S01 – Beliefs & Stakes
- Purpose: WHY via “amount → amt” incident; define stakes
- Source: ScalaIO-2025-Main-Talk.md:10–15
- Notes: speaker-notes-ScalaIO-2025.md: S01 block
- Assets: none (full-bleed minimal)

## S02 – Boundaries (Compile-time vs Runtime, DX vs Process)
- Purpose: Name the axes early to avoid confusion later
- Source: ScalaIO-2025-Main-Talk.md:17–21
- Notes: speaker-notes-ScalaIO-2025.md: S02 block
- Assets: two-column contrast graphic (shield vs heartbeat)

## S02a – Boundary Checkpoint (mini)
- Purpose: Verbatim checkpoint you read aloud
- Source: ScalaIO-2025-Main-Talk.md:23–26
- Notes: speaker-notes-ScalaIO-2025.md: S02a block
- Assets: none (text only)

## S03 – Why Contracts Come First (Policy Lattice)
- Purpose: Introduce migration policies; intent encoded in types
- Source: ScalaIO-2025-Main-Talk.md:28–31
- Notes: speaker-notes-ScalaIO-2025.md: S03
- Assets: assets/policy-lattice.svg

## S04 – Compile-Time Evidence Pipeline
- Purpose: Show derivation → schema AST → compare → error
- Source: ScalaIO-2025-Main-Talk.md:33–36
- Notes: speaker-notes-ScalaIO-2025.md: S04
- Assets: small flow diagram

## S05 – Red → Green Migration Demo
- Purpose: Prove the policy playbook
- Source: ScalaIO-2025-Main-Talk.md:38–44
- Notes: speaker-notes-ScalaIO-2025.md: S05 + Demo Playbook 2.1
- Assets: screenshot of compiler error + success

## S06 – Templates & Golden Path
- Purpose: Show how teams start correctly
- Source: ScalaIO-2025-Main-Talk.md:46–50
- Notes: speaker-notes-ScalaIO-2025.md: S06
- Assets: g8 tree screenshot

## S07 – Configuration Safety
- Purpose: Refined + ValidatedNel; fail fast configs
- Source: ScalaIO-2025-Main-Talk.md:52–56
- Notes: speaker-notes-ScalaIO-2025.md: S07
- Assets: tiny code block (optional)

## S08 – Functional Validation & Data Quality
- Purpose: Aggregate errors with ValidatedNel
- Source: ScalaIO-2025-Main-Talk.md:58–61
- Notes: speaker-notes-ScalaIO-2025.md: S08
- Assets: none

## S09 – Effect Boundary with Kleisli
- Purpose: Pure inside; effects at edges; composable glue
- Source: ScalaIO-2025-Main-Talk.md:63–67
- Notes: speaker-notes-ScalaIO-2025.md: S09
- Assets: simple left/right split diagram

## S10 – Fiber-Safe Execution & Effect Options
- Purpose: Dual support (Cats Effect + ZIO); cancellation; resources
- Source: ScalaIO-2025-Main-Talk.md:69–73
- Notes: speaker-notes-ScalaIO-2025.md: S10
- Assets: tiny capability→interpreter diagram (assets/fiber-safe.svg)

## S11 – Batteries Included
- Purpose: Summarize proof/tests/CLI/connectors/lineage
- Source: ScalaIO-2025-Main-Talk.md:75–80
- Notes: speaker-notes-ScalaIO-2025.md: S11 (keep brisk)
- Assets: none or checklist icons

## S12 – Engine Portability Seam
- Purpose: Acknowledge portability; push deep dive to appendix
- Source: ScalaIO-2025-Main-Talk.md:82–85
- Notes: speaker-notes-ScalaIO-2025.md: S12
- Assets: appendix-engine-portability.md diagram

## S13 – Runtime Guardrails
- Purpose: Runtime DQ, lineage, idempotent edges
- Source: ScalaIO-2025-Main-Talk.md:87–91
- Notes: speaker-notes-ScalaIO-2025.md: S13
- Assets: none

## S13a – Boundary Checkpoint (recap)
- Purpose: Re-anchor boundaries before close
- Source: ScalaIO-2025-Main-Talk.md:93–96
- Notes: speaker-notes-ScalaIO-2025.md: S13a
- Assets: none (text only)

## S14 – Outcome Takeaways (WHY)
- Purpose: Close with outcomes; no feature nouns
- Source: ScalaIO-2025-Main-Talk.md:98–101
- Notes: speaker-notes-ScalaIO-2025.md: S14
- Assets: none

## S15 – Remember Why (WHY)
- Purpose: Emotional close; belief before tooling
- Source: ScalaIO-2025-Main-Talk.md:103–107
- Notes: speaker-notes-ScalaIO-2025.md: S15
- Assets: concentric WHY circle

## S16 – Invitation / Product Note
- Purpose: Call to try and contribute
- Source: ScalaIO-2025-Main-Talk.md:109–112
- Notes: speaker-notes-ScalaIO-2025.md: S16
- Assets: repo link QR (optional)

---

Checklist
- Include S02a and S13a verbatim slides (text-only) and read them aloud.
- Keep S05 and S11 concise if time slips; never drop S14/S15.
- Keep demo screenshots on hidden backup slides.
## S01a – About Me
- Purpose: quick personal context; why you care about this topic
- Source: ScalaIO-2025-Main-Talk.md: S01a
- Notes: speaker-notes-ScalaIO-2025.md: S01a
- Assets: none

## S01b – About Walmart (context)
- Purpose: establish real-world constraints motivating this approach
- Source: ScalaIO-2025-Main-Talk.md: S01b
- Notes: speaker-notes-ScalaIO-2025.md: S01b
- Assets: none (no logos)
