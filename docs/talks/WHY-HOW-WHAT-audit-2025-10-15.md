# WHY–HOW–WHAT Audit – ScalaIO 2025 Main Talk

Date: 2025-10-15
Scope: docs/talks/ScalaIO-2025-Main-Talk.md (S01–S16). Cross-references module talks for reuse. Primary sources: FlowForge code/docs, POC code (`/Users/vim/IdeaProjects/compile-time-data-contracts`), blogs on compile-time contracts and Kleisli.

## Summary
- Strengths: Strong WHY for schema drift via realistic month‑end reconciliation story; repeated boundary checkpoints (compile‑time vs runtime; DX vs Process); outcome‑only close.
- Risks: A few slides lean on HOW/WHAT without an explicit WHY line (S06, S11, S12). This audit adds one‑line WHYs and copy‑paste snippets to insert.

---

## Concept-by-Concept Audit

### S01 – Beliefs & Stakes
- WHY: Silent schema drift (amount→amt) causes weeks of nulls and costly backfills.
- HOW: Make drift a compile error before shipping; codify intent with policies.
- WHAT: 60‑second story + promise statement the compiler will block the drift.
- Status: Complete.

### S02 – Boundaries (Compile-time vs Runtime; DX vs Process)
- WHY: Audience confuses compile‑time guarantees with runtime quality; we must separate concerns to set expectations.
- HOW: Name boundaries explicitly; show two‑column contrast; repeat later.
- WHAT: One slide with the four bullets and icons.
- Status: Complete.

### S02a – Boundary Checkpoint (mini)
- WHY: Reinforce mental model to prevent Q&A confusion later.
- HOW: Read three lines verbatim; anchor the room.
- WHAT: Text‑only slide; repeated again at S13a.
- Status: Complete.

### S03 – Why Contracts Come First (Policy Lattice)
- WHY: Teams need controlled ways to evolve schemas without shipping drift.
- HOW: Policy lattice (Exact/Backward/Forward/Full + modifiers) encodes intent.
- WHAT: Lattice visual + 3‑step migration playbook (Exact → Backward/Forward → Exact).
- Status: Complete.

### S04 – Compile-Time Evidence Pipeline (Derivation → Compare → Error)
- WHY: Developers trust repeatable evidence, not slogans.
- HOW: Derive structure (Magnolia/Mirror) → build Schema AST → compare under policy → emit precise errors.
- WHAT: Mini diagram + one code snippet from ContractMacros + example error paths.
- Status: Complete.

### S05 – Red → Green Migration Demo
- WHY: Show failure moved left (compile/build time), not runtime.
- HOW: Start strict (Exact) → show compile error → relax to Backward → compile.
- WHAT: 60–90s demo or screenshots.
- Status: Complete.

### S06 – Templates & Golden Path (Giter8)
- WHY (ADD): Reduce time‑to‑first‑pipeline and eliminate configuration drift between teams.
- HOW: Generate a project with policy defaults + compile‑fail tests wired.
- WHAT: Show g8 structure + the generated compile‑fail test.
- Slide copy to add (one line): “WHY: keep new projects consistent and safe by default; cut setup time from hours to minutes.”
- Status: Add the single WHY bullet in the slide notes.

### S07 – Configuration Safety (Refined + ValidatedNel)
- WHY: Config errors discovered at runtime waste deploy windows.
- HOW: Typed decoders + refined constraints + aggregated validation.
- WHAT: One minimal example or bullet list of constraints.
- Status: Complete.

### S08 – Functional Validation & DQ
- WHY: Business rules are sampled; aggregate violations without exploding code complexity.
- HOW: `ValidatedNel` to collect multiple rule failures; optional Deequ module.
- WHAT: One slide, one schematic.
- Status: Complete.

### S09 – Effect Boundary with Kleisli
- WHY: Effectful glue (`A => F[B]`) doesn’t compose; ad‑hoc glue causes flakes and unreadable code.
- HOW: Lift edges into `Kleisli`; keep transforms pure; effect polymorphism for CE/ZIO.
- WHAT: Split diagram + concise code showing `read andThen transform andThen write`.
- Status: Complete.

### S10 – Fiber-Safe Execution & Effect Options
- WHY: Safe cancellation, resource safety, and parity across CE/ZIO avoid vendor lock and brittle lifecycles.
- HOW: Minimal capability algebra; interpreter per effect system; bracketing and cancelation modeled once.
- WHAT: Tiny diagram; optional Kyo/Caprese 30s mention.
- Status: Complete.

### S11 – Batteries Included (tests/CLI/connectors/lineage)
- WHY (ADD): Prevent teams from reinventing common guardrails; make the golden path easy.
- HOW: Compile‑fail matrix, human‑readable diff errors, schema‑validate CLI, GCS connector, lineage emitter.
- WHAT: Checklist slide; keep brisk.
- Slide copy to add (one line): “WHY: standard guardrails out‑of‑the‑box so teams stop rebuilding the basics.”
- Status: Add the single WHY bullet in the slide notes.

### S12 – Engine Portability Seam
- WHY (ADD): Avoid rewrites when engine choices change; preserve the same user code.
- HOW: Algebra + interpreters per engine.
- WHAT: One diagram; appendix pointer.
- Slide copy to add (one line): “WHY: hedge engine choices without rewiring pipelines.”
- Status: Add the single WHY bullet in the slide notes.

### S13 – Runtime Guardrails
- WHY: The world is messy—corrupt files, SLA breaches, and lineage obligations remain runtime concerns.
- HOW: Infrastructure wiring for DQ, metrics, tracing, lineage; idempotent edges.
- WHAT: One summary slide.
- Status: Complete.

### S13a – Boundary Checkpoint (recap)
- WHY: Re‑anchor boundaries before closing to steer Q&A.
- HOW/WHAT: Read the three lines again.
- Status: Complete.

### S14 – Outcome Takeaways (Outcome‑Only)
- WHY: Land business results, not features.
- HOW: Use three outcome bullets.
- WHAT: No tech nouns.
- Status: Complete.

### S15 – Remember Why
- WHY: Belief before tooling; make it personal and memorable.
- HOW: One‑liner: keep humans out of midnight war rooms.
- WHAT: Concentric WHY visual.
- Status: Complete.

### S16 – Invitation / Product Note
- WHY: Lower friction to try, capture stories, and recruit contributors.
- HOW: Single slide with repo + quickstart.
- WHAT: QR code optional.
- Status: Complete.

---

## Cross-Check Against Sources

- FlowForge code
  - Policies: `modules/core/src/main/scala/com/flowforge/core/contracts/SchemaPolicy.scala`
  - Macros: `modules/core/src/main/scala/com/flowforge/core/contracts/internal/ContractMacros.scala`
  - Builder: `modules/core/src/main/scala/com/flowforge/core/PipelineBuilder.scala`
  - CLI: `modules/validation-cli/src/main/scala/com/flowforge/validation/SchemaValidateCli.scala`

- POC code (compile-time contracts)
  - `/Users/vim/IdeaProjects/compile-time-data-contracts` – mirror of the derivation + policy compare pattern (use for extra screenshots if needed).

- Blogs
  - Compile-time data contracts: `/Users/vim/IdeaProjects/vitthalmirji.com/content/posts/2025/09/compile-time-data-contracts/index.md` (story and error messaging examples)
  - Kleisli for Data Engineering: `/Users/vim/IdeaProjects/vitthalmirji.com/content/posts/2025/10/kleisli-data-engineering/index.md` (WHY for effectful composition)

---

## Edits to Apply (copy/paste)

1) S06 – Add slide note line at top:
   - “WHY: keep new projects consistent and safe by default; cut setup time from hours to minutes.”

2) S11 – Add slide note line at top:
   - “WHY: standard guardrails out‑of‑the‑box so teams stop rebuilding the basics.”

3) S12 – Add slide note line at top:
   - “WHY: hedge engine choices without rewiring pipelines.”

Optional: copy these lines into `speaker-notes-ScalaIO-2025.md` sections S06, S11, S12.

---

## Module Decks (for reuse)

Talk A – API/Schema Migration (modules/talk-a-api-migration.md)
- WHY: Migrations fail when drift ships; move failures left; reversible playbook.
- HOW: Policy lattice + compile‑fail tests + CI policy gates.
- WHAT: Red→Green demo + PR gate screenshot.
- Status: Complete after story and boundary recap were added.

Talk B – Types for Interface Representation & Validation (modules/talk-b-types-validation.md)
- WHY: Tests are sampled; types are exhaustive for structure; prevent illegal construction and optionality bugs.
- HOW: Derive shape; compare under policy; typestate builder; pure vs effectful boundary.
- WHAT: Optionality mismatch demo + typestate compile‑error + policy toggle example.
- Status: Complete after boundary recap and outcome‑only close.

