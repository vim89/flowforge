# Q&A Seed and Hard Questions

## Likely questions (short answers)
- Does this replace tests?
  - No - types are exhaustive for shape/policy; tests validate behavior.
- How do I relax policies without breaking safety?
  - Use Backward/Forward during rollout; sunset to Exact. Gate with compile-fail tests.
- What about runtime failure cases?
  - Use DQ/lineage/metrics and idempotent edges. Compile-time prevents drift; runtime manages reality.
- Can this work with nested/collections and case differences?
  - Yes - add modifiers (Ordered, CI, By-Position) and enforce as needed.
- Scala 2 vs Scala 3?
  - Prefer Scala 3 inline/quotes; Scala 2 uses scala-reflect def macros (blackbox). Same ideas.

## Deeper probes
- How do you handle element optionality (List[Option[A]] vs List[A])?
  - Element optionality is part of the shape; mismatches fail at compile time under strict policies.
- Can we prove subsets/supersets statically for large models?
  - Yes - via a policy lattice encoded as types; compile-fail tests demonstrate drift.
- What’s the seam for engine portability?
  - Keep a small algebra/runner; two call-sites prove portability without touching pipeline code.
