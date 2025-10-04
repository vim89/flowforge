# Talk Outline — WHY → HOW → WHAT (Brand-agnostic)

## WHY (start here)
- Runtime schema drift burns nights. Make it a compile error so broken pipelines never launch.
- Side-effects inside transforms (audit/Slack/DB) duplicate under retries/speculation. Push all effects to edges and make them idempotent.
- Goal: protect on-call time and trading hours; shorter feedback loops for developers.

Story to anchor (true/related): partner team removed a nullable column Friday night; no rollback; both teams up all night before markets opened. If drift were a compile error, we’d have slept.

## HOW (principles)
- Compile-time contracts with policy lattice (Exact / Backward / Forward × Ordered/CI/ByPosition).
- Typestate pipeline builder: `build()` only from Complete state; incomplete pipelines can’t compile.
- Effect boundary: pure transforms (`A => B`); `F[_]` only at edges (`read/write/stream`).
- Engine abstraction: one algebra surface; Spark and Flink implementations behind it.
- Data quality: native checks by default; optional Deequ via reflection when present.

Boundaries slide
- Compile-time: structural compatibility and builder typestates.
- Runtime: file corruption, empties, SLA breaches → DQ, lineage, metrics.
- DX vs Process: DX = fast red→green locally; Process = CI gates (policy matrix + example diffs).

## WHAT (selected, minimal)
- 60‑second red→green demo: change contract field → fail compile; relax to Backward → compile.
- 6‑line typestate demo: show missing sink fails to compile.
- Spark read/write and a single native DQ rule; optional Deequ flip via `-Dff.quality.mode=deequ`.

## FAQ (seed answers)
- “Can we evolve schemas safely?” Yes, via policy lattice; Backward/Forward/Full encoded at types.
- “Scala 3?” Core aligns; engines depend on ecosystem readiness.
- “Why not just tests?” Tests are sampled; compile-time proofs are exhaustive for shapes.

