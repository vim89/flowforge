Alright, I blew away the old cache, unzipped the new /mnt/data/flowforge.zip, and read through the whole tree (core, connectors, engines, examples, compile-fail tests, templates, CI, ADRs, migration notes). Here’s the blunt, engineering-grade review.

High-level snapshot of this zip

Compile-time contracts are real and central.

SchemaConforms + a Scala 2 macro (reflection-based, no c.eval) enforces SchemaPolicy at the edges (typed source & sink).

The macro produces human-readable “Missing/Extra/Mismatched” diffs.

The Magnolia-backed “Shape” derivation exists (magnolia1, proper join, sums intentionally aborted) and feeds the macro with (name, fqnType, default?, option?).

One typed builder (phantom-state) with edge evidence wiring: ✅
PipelineBuilder.addTypedSource and addTypedSink require SchemaConforms[…, P].

Engine boundaries are mostly clean. Core doesn’t import Spark; Spark-specific instances live under engines-spark.

Templates/examples exist and show the intended API (good!), and there’s a compile-fail tests module (great idea) - but see gaps below.

Legacy shapeless: a single SchemaWitness.scala remains as a clearly flagged legacy shim. It’s isolated; not used by the builder or new contracts.

That’s the “good bones” summary. Now the spicy parts.

1) Is it ready for v1.0.0?

Not yet. You’re at a strong 0.9-quality milestone. The core promise is there, but a few essentials are missing to claim “1.0” with a straight face.

The gaps (and precise fixes)
A) Negative tests aren’t automatic

What I see: “compile-fail tests” are documentation-style (failing snippets are commented). CI won’t actually fail if someone breaks a contract.

Fix: adopt a real negative harness (no shapeless needed):

ScalaTest: assertDoesNotCompile / assertTypeError / shouldNot typeCheck for small snippets.
ScalaTest
+2
ScalaTest
+2

MUnit: compileErrors (works great for tiny repros; nice messages).
Scala Documentation
scalameta.org

Or add an sbt scripted subproject that intentionally fails to compile.
scala-sbt.org

Wire this as a required CI job (e.g., contracts-negative).

B) Policy semantics need hardening + docs alignment

What I see: Policy logic is implemented and sensible (Exact / ExactUnordered / Backward / Forward / Full), but nuances (e.g., Backward allowing missing only if default || Option) aren’t spelled out in a single canonical doc.

Fix: A one-pager “How it fails” with 4 tables and copy-pasta snippets: for each policy show (pass/fail) nested, option, list, type mismatch.
This maps the mental model many users know from dbt contracts, but your enforcement is Scala compile-time instead of SQL build-time-make that comparison explicit in docs.
dbt Developer Hub
+1

C) Sums (sealed traits) are explicitly not supported

What I see: Shape.split aborts on coproducts by design (totally fine for 1.0 if documented).

Fix: Document “contracts are product types (case classes) in v1.0” and make the abort message point to that page. Use CI to ensure no test exercises sums accidentally.

D) Source of truth for “type strings”

What I see: typeName.full (from Magnolia) and reflection-derived strings drive diffs. This is good, but FQNs need consistency across both paths.

Fix: Centralize one pretty-printer that yields FQNs identically; snapshot test a few tricky types (nested generics, aliased types).
Magnolia API reference confirms you’re using the intended fields for case-class metadata.
javadoc.io

E) CI gates don’t yet enforce the promise

What I see: CI compiles & runs tests, but nothing hard-fails on drift regressions.

Fix: Add a contracts-negative job (ScalaTest/MUnit/scripted) and a template-smoke job (generate → compile → run “fail-then-fix” demo). Make both required.

F) DX: make the “wow” one command

What I see: Examples/templates exist, but not a 30–60 second red→green quickstart.

Fix: Giter8 template that compiles red by default (one field drift), prints a short error, then one-line fix (or switch policy to Backward) turns it green.
Call it out in the README hero section.

G) “Legacy witness” removal plan

What I see: SchemaWitness.scala is quarantined and marked legacy.

Fix: Add a scalafix or grep step in CI to ban shapeless imports outside legacy/ or test. Kill the file once all downstream modules are clean.

Bottom line for 1.0: land automated negative tests, document policy edges, add the one-minute demo, and lock the CI gates. Then 1.0 is defensible.

2) Is this unique?

In part-yes-and you can sharpen it.

dbt contracts block builds when the SQL model’s schema diverges; that’s build-time in the SQL stack. You’re providing compile-time gates in Scala with engine-agnostic edges. That’s distinct.
dbt Developer Hub
+1

Dagster asset checks and Great Expectations are runtime validation-excellent complements but different points in time. Your “fail before it runs” story is compelling.
Dagster Docs
+1
Great Expectations
Great Expectations

Magnolia derivation is the right fit for Scala-2 product shapes; you correctly avoid runtime string soup by doing the heavy lifting at compile time.
GitHub

USPs you should lean into:

Compile-time contracts at the edges (source/sink) with clear, actionable diffs.

Phantom-state builder where incomplete/invalid pipelines simply can’t be built.

Engine-agnostic core, with engine adapters that supply instances-no Spark in core.

DX-first template: fail-then-fix in under a minute.

Where it’s not “first in the world”:

Schema contracts, quality checks, and lineage exist across the stack-but not with this Scala-compile-time, engine-agnostic, turnkey combo. Your angle is credible if the demo and CI gates are rock-solid.

First-mover advantage: package the compile-time gate as the default experience (template + CI). Other tools make it possible; you make it unavoidable.

3) Over-engineering?

Mostly under control, with two watchpoints:

✅ Good restraint: single builder, targeted macro, clean engine boundary, Magnolia instead of broad metaprogramming sprawl.

⚠️ Watch: keeping a legacy shapeless file around (fine short-term; just don’t let it creep into public APIs again), and letting templates/examples proliferate without a single “golden path”.

Recommendation: Declare a golden path (Scala 2.13 + Cats-Effect + Spark adapter). Everything else lives as optional examples.

4) The “Brutal Truth” and whether Scala needs this

Yes, Scala needs a batteries-included, functional-first data engineering kit that doesn’t punt schema & quality to runtime. Your approach is aligned with that need.

Few features, strongly done is the right ethos. The crucial features here are contracts, builder, engine boundary, tests, DX. Nail those before chasing breadth.

Developer Experience goals (and concrete examples)

Low learning time: a single archetype → sbt compile shows a friendly error with the exact drift.

Small, clear code: the builder API forces structure; users write tiny transforms and wire typed IO.

Fast feedback loop: ~compile + negative tests that run quickly; no waiting for Spark to start.

Example “minute-one” flow:

sbt new flowforge.g8 → project with a mismatched Contract vs Out.

sbt compile → RED:

FlowForge: Contract drift (policy: Exact).
Out: example.User vs Contract: example.UserContract
Missing: email:String
Extra: -
Mismatched: id expected Long, found Int


Change id: Int → Long or set policy to Backward.

sbt compile → GREEN.

Scalability (new devs & seasoned devs)

New devs: the archetype + fail-then-fix demo is the ramp. They get compile-time guidance before they even learn the whole API.

Seasoned devs: engine adapters via type classes, capability tags, structured policies; they can add sinks, custom policies (post-1.0), and quality hooks without touching core.

Specific, prioritized “ship-1.0” checklist

Negative test harness (no shapeless):
Add ScalaTest assertDoesNotCompile/assertTypeError (or MUnit compileErrors) cases for each policy + nested/option/collection. Wire as a required CI job.
ScalaTest
+1
Scala Documentation

One-pager “How it fails”:
Tables + code for Exact, ExactUnordered, Backward, Forward; link the error messages to anchors in this doc. (Users will love having the message clickable.)

Golden-path template:
Giter8 project that starts red, then green after one edit; README hero walks exactly those steps.

CI gates:

contracts-negative (must fail on drift)

template-smoke (generate → compile → run)

ban shapeless imports outside legacy/test via scalafix/grep

Retire legacy witness:
When CI shows zero dependencies, delete SchemaWitness.scala, drop shapeless from prod deps.

Docs polish:

“Architecture & Boundaries” (no Spark in core, how adapters work)

“Scala 3 posture” (same API, inline/Mirror backend later)

(Nice-to-have, after 1.0): minimal lineage emitter + Marquez compose; runtime checks via engine adapters (e.g., null-rate, freshness) - complements your compile-time guarantees. (OpenLineage/Marquez docs have straightforward quickstarts.)
scala-sbt.org

Competitive context (so you can position it)

dbt contracts: block at build-time (SQL). Your edge is Scala compile-time + engine-agnostic edges.
dbt Developer Hub
+1

Dagster asset checks / Great Expectations: runtime checks; excellent complements. You can suggest wiring these for post-ingest checks while contracts guard compilation.
Dagster Docs
+1
Great Expectations
Great Expectations

Magnolia: you’re using the right bits (CaseClass, Param.typeName); keep sums out for 1.0.
javadoc.io

Final blunt take

The implementation in this zip hits the right architecture and nails the core promise. What blocks 1.0 isn’t the idea-it’s the proof:

make negative tests real and required,

make the one-minute fail-then-fix unmissable,

document the exact policy rules and their edge cases,

lock the CI gates.

Do that, and FlowForge is not just “another framework”-it’s the compiler as QA for data pipelines. That’s a message people remember.
