# EVIDENCE - Typed Contract Pipelines Example

## 1) Problem & Constraints
- **Goal**: Ensure a canonical typed example exists that fails to compile on schema drift and shows runtime DQ.
- **Non-goals**: Full-blown app.

## 2) Codebase Recon
- **Modules involved**: examples (module exists but minimal), core/types.
- **Key files**:
  - `modules/examples` (declared; minimal)
  - `modules/core/types/*` (typed path primitives)
- **Findings**: No canonical typed example + test currently maintained.

## 2.1) Detailed Findings
- Core typed path (PipelineBuilder2, TypedSource/Sink, SchemaEq) is present and ready to demonstrate compile-time gating.
- No example + test exists to enforce behavior; onboarding and regression guard are missing.

## 3) Prior Art & Sources
- ADR-019; archived examples/TYPED_CONTRACT_PIPELINES.md.

## 4) Options & Trade-offs
| Option | Pros | Cons | Why |
|---|---|---|---|
| Add small typed example + test | Clear DX; regression guard | Some effort | Accepted
| Leave in doc only | No work | Goes stale; unmet ADR | Rejected

**Decision sketch**: Add minimal example under examples with a focused test.

## 5) Edge Cases & Invariants
- Keep it independent of engines; use core-only.

## 6) Success Criteria
- Example compiles; test enforces typed-gate behavior.

## 7) Recommendations (Production-grade)
- Provide a tiny typed example (core-only) that sets up a TypedSource and TypedSink with matching schema evidence and a single transform.
- Add a test that verifies witness resolution and fails when field mismatch occurs (use a negative compilation test pattern or a runtime assertion on evidence resolution if needed).

## 8) Next Steps (Concrete)
- Add `modules/examples/src/main/scala/.../TypedContractsDemo.scala` with a 3–5 line pipeline.
- Add `modules/core/src/test/scala/.../TypedContractsSpec.scala` to exercise the typed path and a mismatch case (document how to run negative test manually if compiler-plugin infra isn’t present).
