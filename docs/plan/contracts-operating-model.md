# PLAN - Contracts Authoring & Operating Model

## 1) Scope (Minimal Viable Change)
- **Goal**: Document publisher CI interface and add consumer example wiring in this repo.
- **Out of scope**: Building the portal.

## 2) Files to Touch (exact)
1. `docs/design/contracts-publisher-ci.md` - describe pipeline (validate → codegen → publish SDK → sync catalog/registry).
2. `modules/examples/src/test/scala/.../ContractsConsumerDemoSpec.scala` - compile-only demo using typed endpoints.

## 3) Patch Sketch (pseudo-diffs)
```text
docs/design/contracts-publisher-ci.md
+ pipeline steps, inputs/outputs, offline mode, error handling
```

## 4) Risk mitigation
- Keep publisher CI doc neutral to org tooling; focus on interfaces and artifacts.

## 5) Validation
- Example compiles using typed endpoints (no runtime IO).

## 6) Acceptance criteria
- Clear publisher CI doc; example compiles.
