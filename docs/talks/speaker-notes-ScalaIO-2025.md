# ScalaIO 2025 – Speaker Notes & Q&A Prep

> Deep prep guide for "Compile-Time Contracts & Fiber-Safe Data Pipelines". Slide references follow `docs/talks/ScalaIO-2025-Main-Talk.md` (S01a … S16). **NO IDE DEMOS—all code and diagrams embedded in slides.**

---

## 0. Opening Checklist (Updated: No IDE Demos Required)

**CRITICAL: All demos are now embedded code/diagram slides. No live IDE required.**

### Pre-Talk Verification
- **Mermaid diagrams render correctly:**
  - [ ] S01c: Data Engineering workflow diagram (TB flowchart)
  - [ ] S04: TypeRepr recursion diagram + Evidence flow
  - [ ] S05: Code blocks with syntax highlighting (red→green)
  - [ ] S06: Typestate builder diagram (LR flowchart)
  - [ ] S09b: Kleisli sequence diagram
  - [ ] S13: Architecture diagram (layered flowchart)
- **Projector settings:** 1080p, readable font sizes, dark mode for code blocks
- **Backup plan:** If Mermaid fails to render, have static PNG exports on USB drive
- **No dependencies:** No sbt, no JDK, no IDE—just slides

### Slide Deck Final Check
- [ ] S01a includes "I cook good Indian food"
- [ ] S04 bracket escaping fixed (`#91;` and `#93;` for `[` and `]`)
- [ ] S05 code blocks clearly show RED (error) vs GREEN (success)
- [ ] All diagrams use consistent styling and color scheme

---

## 1. Slide-by-Slide Notes

### S01a – About Me
<span style="color:#777;font-size:12px">Target end: 00:45</span>
- Quick introduction: Staff Data Engineer @ Walmart, 10+ years experience
- **Personal touch:** "I cook good Indian food" (humanizes, builds rapport)
- Keep crisp—transition to Walmart context quickly

### S01b – About Walmart (Context)
<span style="color:#777;font-size:12px">Target end: 01:30</span>
- Set the stage: global retail scale, strict SLAs, mixed batch/streaming
- Emphasize constraints that motivated compile-time + fiber-safe approach
- Transition: "Before diving into solutions, let's get everyone up to speed on data engineering reality."

### S01c – Data Engineering Reality (NEW: Getting Up to Speed)
<span style="color:#777;font-size:12px">Target end: 04:00</span>
- **WHY THIS SLIDE:** Many Scala devs aren't full-time data engineers—this brings them up to speed
- **Walk through the diagram slowly:**
  1. **Schema Design Phase:** Data Modelers + PMs define schemas (ER models, contracts)
  2. **Architecture & Design:** Data Architect checks feasibility across many sources
  3. **Source Validation:** Multiple varied sources (API, DB, files)—**compile-time safety helps here!**
  4. **Data Quality Design:** Technical DQ + Business DQ rules (stakeholder collaboration)
  5. **Pipeline Implementation:** Read → Transform (performance critical) → Side Effects (logging, notifications, audit) → Sink
  6. **CI/CD & Production:** Strong CI/CD gates deployments
- **Key callouts:**
  - **Sources:** "This is where compile-time prevents schema drift across many varied sources"
  - **Effects:** "Side effects (logging, notifications, audit) kept at edges—fiber-safe"
  - **CI/CD:** "Compile-fail tests gate deployments"
- **Transition:** "Now you've seen the data engineering workflow—let me tell you about the worst schema incident I've ever seen."

### S01 – Scars, Beliefs & Stakes
<span style="color:#777;font-size:12px">Target end: 06:30</span>
- **Deliver verbatim (60 seconds):**
  > "Three months ago, an upstream team renamed `amount` to `amt` in a microservice we consume. No one told us. Our Spark job didn't crash—it just wrote `null` for every transaction amount. For three weeks.
  >
  > We only caught it during month‑end reconciliation when Finance couldn't close the books. We burned a weekend backfilling 47 million rows. I had to present a postmortem to leadership explaining why a column rename cost us significant engineer time and delayed financial reporting by 5 days.
  >
  > **If the build had failed on that schema diff, none of that would have happened.**"
- **[Pause. Beat. Let it land.]**
- **Promise:** "By the end of this talk, you'll know how to move drift, effect leaks, and rollback risk to compile/build time—so you never present that postmortem."

### S02 – Boundaries We Must Name
<span style="color:#777;font-size:12px">Target end: 08:30</span>
- **Show decision matrix table**—let audience absorb it for 10 seconds
- **Read key rows aloud:**
  - "Schema drift within your repo? ✅ Compile-time macro evidence. ❌ Runtime is too late."
  - "Corrupt file (missing rows)? ❌ Compile-time can't help. ✅ Runtime DQ checks."
- **Emphasize:** "Compiler buys sleep; runtime guardrails deal with the world's messiness."
- **Transition:** "Keep these boundaries in mind—every section will point back here."

### S02a – Boundary Checkpoint (Mini)
<span style="color:#777;font-size:12px">Target end: 09:15</span>
- **Read aloud, slowly:**
  - "Compile‑time stops drift and illegal construction."
  - "Runtime handles corrupt files, SLA breaches, retries, lineage."
  - "DX = fast local red→green. Process = CI policy gates + compile‑fail PR checks."
- **Note:** Repeat this again at S13a before takeaways.

### S03 – Why Data Contracts Come First
<span style="color:#777;font-size:12px">Target end: 12:15</span>
- **Show policy comparison table**—let it breathe for 10 seconds
- **Highlight key policies:**
  - **Exact:** Rejects everything (missing, extra, type mismatch)
  - **Backward:** Producer can add optional fields (migration-friendly)
  - **Forward:** Consumer tolerates missing fields
- **Code snippet:** Show sealed trait structure (emphasize type-level enforcement)
- **Transition:** "Let's see how the compiler enforces that promise."

### S04 – Compile-Time Evidence Pipeline
<span style="color:#777;font-size:12px">Target end: 15:45</span>
- **NEW: Show TypeRepr recursion diagram first**
  - "The macro recursively walks your types: case class → extract fields → recurse on each field"
  - "Primitives become leaf nodes; Options/Collections wrap and recurse"
  - **Point at the recursion arrows:** "Field 1, Field 2, Field N—all recurse back to CheckType"
- **Then show Evidence Flow diagram:**
  - "Developer writes types → TypedSource/Sink require Evidence → SchemaConforms typeclass"
  - "Derivation compares schemas → match? Emit evidence. Diff? Abort with precise error."
- **Show example compile error:**
  - **Read aloud:** "Extra: segment. Missing: (none). Mismatched: (none)."
  - "Compiler errors pinpoint the exact field/path that drifted—no grepping logs at 2 AM."
- **Transition:** "Time to see this in action—red→green."

### S05 – Red → Green Migration Demo (MONEY SLIDE—NO IDE)
<span style="color:#777;font-size:12px">Target end: 18:15</span>
- **THIS IS THE MONEY SLIDE. "This is where we get Friday night back."**
- **Walk through code slowly (embedded in slides):**
  1. **Setup:** "Contract expects id, email. Producer has an EXTRA field: age."
  2. **❌ RED:** "We attempt Exact policy. Watch what happens."
     - **Show compiler error on screen**
     - **Keep error visible for 10 seconds—let audience read it**
     - **Point at the error:** "Extra: age: Int. Compiler refuses to proceed."
  3. **✅ GREEN:** "Now we relax to Backward policy."
     - **Show code:** `SchemaPolicy.Backward`
     - **Show success message:** "Compiles successfully! ✅"
- **Key takeaway:** "The compiler pinpoints the exact field (`age: Int`) that drifted—no log grepping at 2 AM. You choose when to relax the policy for safe migrations."
- **Transition:** "Let's see what you get when you scaffold a new project."

### S06 – Templates & Golden Path + Typestate Builder Safety
<span style="color:#777;font-size:12px">Target end: 20:45</span>
- **Show generated code first:** "This is what `sbt new flowforge.g8` scaffolds for you."
- **Then show typestate diagram:**
  - "Builder states: Empty → HasSource → HasTransform → Complete → Pipeline"
  - **Point at Complete node:** "Only when you reach Complete can you call `.build`"
- **Show compile error for incomplete pipeline:**
  - **Read aloud:** "Compile error: required BuilderState.Complete"
  - "Illegal states are unrepresentable—compiler refuses incomplete pipelines."
- **Transition:** "Scaffolding's only useful if configs can't betray you."

### S07 – Configuration Safety
<span style="color:#777;font-size:12px">Target end: 22:15</span>
- Mention `ValidatedNel` for config errors
- Refined types for config keys (eu.timepit.refined)
- CLI tool: `ff-validate-schema` catches drift before deploy
- **Keep short**—this can be condensed if running behind
- **Transition:** "Now let's talk about validation beyond compile time."

### S08 – Functional Validation & Data Quality
<span style="color:#777;font-size:12px">Target end: 23:45</span>
- `ValidatedNel` aggregates DQ errors
- Optional Deequ integration (mention only)
- Tie back to organizer promise: multi-rule DQ with meaningful error accumulation
- **Keep short**—condense if behind schedule
- **Transition:** "We keep pure transforms fast by drawing an effect boundary."

### S09a – Effect Boundary (Pure Inside, Effects at Edges)
<span style="color:#777;font-size:12px">Target end: 25:15</span>
- **Connection to contracts:** "Contracts prove the schema *before* you run. Now let's talk about keeping pipelines *composable* and *testable* while you run."
- **Core principle:**
  - Pure inside: `A => B` (map/filter)—no side effects
  - Effects at edges: `A => F[B]` where `F[_]` captures IO
- **Why it matters:** Retries are safe; tests don't need real databases; logic is portable.
- **Transition:** "But how do we compose effectful functions?"

### S09b – Kleisli Composition (Effectful Glue)
<span style="color:#777;font-size:12px">Target end: 27:45</span>
- **The problem:** "`A => F[B]` functions don't compose like regular functions."
- **The solution:** "`Kleisli[F, A, B]` wraps `A => F[B]` and makes it composable."
- **Show sequence diagram:**
  - "App → PipelineBuilder → build stages → Pipeline[F, In, Out]"
  - "App → execute(input) → read → validate → transform → write → F[Out]"
- **Effect-polymorphic design:** "Use `F[_]: Async` so the same code runs on Cats Effect or ZIO."
- **Key insight:** "Kleisli turns `A => F[B]` into composable pipelines—write once, run on any effect system."
- **Transition:** "Which brings us to fiber-safe orchestration."

### S10 – Fiber-Safe Execution & Effect Options
<span style="color:#777;font-size:12px">Target end: 30:15</span>
- **Translate "fiber" for data engineers:**
  > "Fibers are lightweight threads. When a pipeline stage fails, you want **retries to be safe** (no duplicate writes) and **cancellation to be clean** (no leaked resources). Cats Effect and ZIO give you this via structured concurrency."
- **Dual effect support:** Cats Effect + ZIO (mention doc: `bring-your-own-effect.md`)
- **Fiber-aware orchestration:** Cancellation propagates cleanly, resource safety guaranteed, retries don't duplicate side effects
- **Transition:** "Now, let's open the toolbox we ship with FlowForge."

### S11 – Batteries Included
<span style="color:#777;font-size:12px">Target end: 31:45</span>
- **Pick ONE to show:** Compile-fail test matrix (CI gate that saves you)
- **Show code example:** Policy matrix test (assertDoesNotCompile)
- **Runs in CI:** "If this test passes (schema drift compiles), CI fails the PR."
- **Other batteries (mention only):**
  - Contract macros emit human diff
  - `ff-validate-schema` CLI
  - GCS connector
  - OpenLineage emitter
- **Avro/Protobuf integration:** "This works *on top of* Avro/Proto—it checks that your case classes match the wire schema."
- **Transition:** "All of this should run wherever you need it to."

### S12 – Engine Portability Seam
<span style="color:#777;font-size:12px">Target end: 32:45</span>
- **Audience question:** "Wait, does this only work on Spark?"
- **Answer (30 seconds):**
  > "The algebra separates *what* you do (read/transform/write) from *how* you run it. Swap `SparkRunner` for `FlinkRunner` and the same pipeline code runs on Flink. The appendix has the proof if you're curious."
- **Key insight:** Pipeline code stays the same; choose the interpreter at wiring time.
- **Can skip entirely if pressed for time**
- **Transition:** "Runtime still matters."

### S13 – Runtime Guardrails
<span style="color:#777;font-size:12px">Target end: 34:15</span>
- **Show architecture diagram—let it breathe for 15 seconds**
- **Walk through layers:**
  - User App → Core (PipelineBuilder, Kleisli, DataAlgebra)
  - Engines (Spark, Flink)
  - Connectors (S3, GCS, JDBC, Kafka)
  - Quality (Native Checks, Deequ)
  - Lineage (OpenLineage)
- **Runtime capabilities:**
  - Infrastructure layer: resource safety, typed config, metrics, tracing
  - Data Quality: native checks + optional Deequ
  - Lineage: OpenLineage emitter
  - Idempotent edges: retry-safe operations
- **Reinforce boundary:** "Compiler blocks drift; runtime manages corrupt files & SLA breaches."
- **Transition:** "Let's close with what attendees should remember."

### S13a – Boundary Checkpoint (Interactive Recap)
<span style="color:#777;font-size:12px">Target end: 35:15</span>
- **Pop quiz for the audience:**
  > "Does compile-time catch corrupt CSV files?"
  > **[Pause. Let them answer.]**
  > "No—that's runtime."
  >
  > "Does it catch schema drift in your repo?"
  > **[Pause. Let them answer.]**
  > "Yes—that's compile-time."
- **Repeat the boundary:**
  - Compile‑time stops drift and illegal construction
  - Runtime handles corrupt files, SLA breaches, retries, lineage
  - DX = fast local red→green | Process = CI policy gates + compile‑fail PR checks

### S14 – Outcome Takeaways
<span style="color:#777;font-size:12px">Target end: 37:15</span>
- **Lead with feelings, then mechanisms:**
  1. **Stop getting paged at 2 AM** because schema drift dies before deploy
     - *How:* Drift fails at compile time, not in production
  2. **Go from hours of log-grepping to seconds of compiler errors**
     - *How:* Compiler errors point at the exact field/path that drifted
  3. **Keep traders, analysts, and finance teams out of war rooms**
     - *How:* Retries are fiber-safe and idempotent at the edges; SLAs protected

### S15 – Remember WHY?
<span style="color:#777;font-size:12px">Target end: 38:15</span>
- **Show WHY/HOW/WHAT visuals** (if available: `why-01-protect-sleep.svg`, `how-01-contracts.svg`, `what-06-quality.svg`)
- **Bring story back:** "We do this so no one re-lives that 04:00 backfill."
- **Invite personal reflection:** "Think about your worst schema incident—this is how we stop repeating it."
- **Transition:** "Here's how you can start today."

### S16 – Invitation (Not Pitch)
<span style="color:#777;font-size:12px">Target end: 40:00</span>
- **Frame as proof, not product:**
  > "We built [FlowForge](https://github.com/vim89/flowforge) to prove these ideas work at scale. The repo is public—try `sbt new flowforge.g8`, run the red→green demo, and share your stories. **The ideas belong to you; the code is just a reference.**"
- **Incremental adoption:**
  - Start with one pipeline
  - Add evidence at the sink
  - Run compile-fail tests in CI
  - You don't need to convert everything at once
- **Callback to S01 (close the loop):**
  > "No more 2 AM pages. No more postmortems to leadership. No more burned weekends backfilling data. **Just pipelines that refuse to break in the first place.**"
- **Transition to Q&A:** "I'd love to hear your questions."

---

## 2. Presentation Tips (No IDE Demos)

### Key Slides with Diagrams
- **S01c:** Data Engineering workflow (TB flowchart)—**NEW, brings audience up to speed**
- **S04:** TypeRepr recursion + Evidence flow (2 diagrams)—**NEW recursion diagram**
- **S05:** Red→Green code example (Exact fails, Backward succeeds)—**MONEY SLIDE**
- **S06:** Typestate builder diagram + compile error example
- **S09b:** Kleisli composition sequence diagram
- **S13:** FlowForge architecture diagram (layered flowchart)

### Delivery Tips
1. **S01c (DE workflow):** Walk through slowly—many Scala devs aren't full-time DEs
2. **S04 (TypeRepr recursion):** Point at recursion arrows—emphasize "Field 1, Field 2, Field N all recurse"
3. **S05 (Red→Green):** Keep error on screen for 10 seconds; point at exact field that drifted
4. **S04 & S13:** Let diagrams breathe; don't rush the visuals
5. **S06:** Emphasize "compiler refuses `.build`" for incomplete pipelines
6. **Timing:** Target 35 min for S01a-S16, leaving buffer for Q&A

### No Live Coding Required
- Everything is in slides with precise error messages and working examples
- Focus on delivery, not demo execution
- If asked about live demos: "All code is embedded in slides for clarity—no risk of live demo failures"

---

## 3. Anticipated Q&A

### Q1: "How do compile-time contracts work under Scala 2 vs Scala 3?"
- **Answer:** Scala 3 uses inline+quotes macros. Scala 2 module retains def-macro pathway, but the canonical implementation lives in `modules/core/src/main/scala/com/flowforge/core/contracts/internal/ContractMacros.scala`. We ship both while Spark/Flink remain on Scala 2.13/2.12. Adoption path documented in `docs/evidence/scala3-alignment.md`.

### Q2: "What about large, nested schemas?"
- **Answer:** Magnolia-derived shapes traverse nested case classes and collections. The TypeRepr recursion diagram (S04) shows this visually—each field recursively inspects nested types. Compile-fail tests cover nested optionality. Error messages include full JSON paths.

### Q3: "How do you prevent runtime-only services from breaking pipelines?"
- **Answer:** We split responsibilities (see S02 decision matrix). Compile-time ensures contract alignment; runtime guardrails still run DQ + lineage via infrastructure module. Idempotent edge patterns and `ResourceSafety` handle retries.

### Q4: "Can teams opt out of compile-fail tests if they need flexibility?"
- **Answer:** We allow `SchemaPolicy.Full` (escape hatch), but it's discouraged. Templates ship with CI gates that require explicit policy downgrades documented in review. We highlight the optionality but frame it as an escape hatch with scrutiny.

### Q5: "What's the performance overhead of the effect abstraction?"
- **Answer:** `EffectSystem[F]` is a thin typeclass. For Cats Effect we delegate to native APIs; there's no wrapping on hot paths. Compose pipelines into `Kleisli`, so runtime overhead matches pure CE/ZIO constructs.

### Q6: "How do you validate configuration in environments without compile access?"
- **Answer:** Use the CLI preflight (`ff-validate-schema`) and type-safe config loader. In runtime-only environments, we run the CLI in CD pipelines to fail before deployment.

### Q7: "Does OpenLineage integration require extra instrumentation?"
- **Answer:** No. Pipeline builder accepts `withLineageEmitter`. We provide noop/http/async out of the box. Async variant batches events with backpressure.

### Q8: "What's the path to adding a new connector or engine?"
- **Answer:** Implement `FileSystemConnector[F]`/`DataAlgebra[F]` traits and add module entry in `build.sbt`. Use connector tests as blueprint. Engine swap proven via Spark + Flink; appendix slide documents architecture.

### Q9: "How do you migrate existing pipelines?"
- **Answer:** Start with template scaffolding; wrap existing transformations in typed builder stages; add compile-fail tests before switching policies. Documented in `docs/migration/from-dbt.md` and `docs/plan/v1.0-readiness.md`.

### Q10: "What happens when schemas evolve beyond case classes (e.g., Avro/JSON)?"
- **Answer:** We support JSON/Avro descriptors via CLI and plan to auto-generate contracts. Current macros expect case-class shapes; roadmap includes typed DSL integration (see `docs/plan/v1.0-readiness.md` tasks). The compile-time evidence still applies—you just generate the case classes from Avro/Proto schemas.

### Q11: "Can FlowForge run on streaming engines beyond Spark/Flink?"
- **Answer:** Architecture isolates pipeline from engine (see S13 diagram). Spark + Flink modules exist today; hooks for Kafka/Delta Live Tables outlined in roadmap. No technical blocker—requires implementing `DataAlgebra` interpreter.

### Q12: "What about capturing business logic validation?"
- **Answer:** Compile-time catches structural drift. Business rules remain runtime or compile-fail property tests. We provide `PipelineTypes.DataValidation` alias for `ValidatedNel` to aggregate those results (see S08).

### Q13: "Why not just use dbt or Airflow?"
- **Answer:** dbt is SQL-first (no type safety in transformations); Airflow is orchestration (no compile-time guarantees). FlowForge gives you type-safe transformations + compile-time contracts + fiber-safe execution in one framework. You can integrate with Airflow for scheduling, but the pipeline itself has stronger guarantees.

### Q14: "What's the learning curve for data engineers new to Scala?"
- **Answer:** The Data Engineering workflow diagram (S01c) helps bridge the gap. Templates (`sbt new flowforge.g8`) scaffold everything. Most DEs will write transforms as `A => B` functions—simple Scala. The macro magic and effect abstractions are hidden. We have `docs/getting-started-quick.md` for onboarding.

---

## 4. Closing Reminders

- **Rehearse transitions** between WHY and HOW sections; always tie back to sleep/MTTR outcomes
- **Let diagrams breathe:** S01c, S04, S13 especially—don't rush visuals
- **Keep a visible timer:** Trim commentary from S07/S08 if time slips; S12 can be skipped entirely
- **After Q&A:** Remind attendees of repo + template and invite them to share failure stories post-session
- **No live coding stress:** Everything is embedded—focus on storytelling and delivery

---

**Presentation Mode: Story First, Code Second, Diagrams Reinforce**

_Last updated: 2025-10-17 (embedded all demos, added S01c DE workflow, added S04 TypeRepr recursion)_
