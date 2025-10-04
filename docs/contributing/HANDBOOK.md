# Developer Handbook (Bridge)

This bridge indexes the canonical documents while preserving legacy anchors required by CI.

## 1. Philosophy, Pitch, Mission
- See: docs/README.md

## 2. Architecture Overview
- See: docs/design/core-design.md

## 2.1 SOLID Principles Implementation
- See: docs/design/core-design.md

## 3. Coding Patterns (Tagless Final)
- See: docs/design/core-design.md

### 3.1 Kleisli Composition (Example)
- See: docs/framework/PipelineCombinators (code) and docs/design/core-design.md

## 4. ETL & Pipeline Patterns
- See: docs/design/framework-behaviors.md

### 4.1 Effect Boundaries (Pure vs IO)
- See: docs/effects/bring-your-own-effect.md

## 5. Production Pipeline Concerns (35+)
- See: docs/quality/release-criteria.md

## 6. Technical Implementation Strategy
- See: docs/plan/INDEX.md

## 6.1 Multi-Engine Strategy
- See: docs/design/core-design.md

### 6.2 Resource Safety (Example)
- See: docs/core/algebra/FlowforgeResource.scala

## 7. Advanced Type-Level Patterns
- See: docs/design/core-design.md

## 8. Resource Management Patterns
- See: docs/effects/bring-your-own-effect.md

## 9. Error Modeling Strategy
- See: modules/core/src/main/scala/com/flowforge/core/types/PipelineError.scala

### 9.1 Aggregating Validation (ValidatedNel)
- See: modules/core/src/main/scala/com/flowforge/core/patterns/ValidationCombinators.scala

## 10. Template Generation Philosophy
- See: flowforge.g8/README.md

## 10.1 Archetype & Compile-time Contracts
- See: docs/diagrams/compile-time-contracts/flowchart.svg

## 10.2 Type-Safe Archetypes (Scala Ecosystem)
- See: docs/design/framework-behaviors.md

### 10.3 Typed Contracts & Builder (Example)
- See: modules/core/src/main/scala/com/flowforge/core/PipelineBuilder.scala

## 11. Prototype Integration & Incremental Adoption
- See: docs/plan/INDEX.md

### 11.1 Prototype Index (Repo Paths)
- See: docs/INDEX.md

## 12. Refactoring Strategy
- See: docs/plan/archive/iterations

## 13. Security, Config, and Observability
- See: modules/infrastructure and docs/plan/INDEX.md

## 14. Testing & QA
- See: docs/quality/release-criteria.md

## 15. Anti-Patterns to Reject
- See: docs/design/framework-behaviors.md

## 16. 30-Point Checklist (Pointer)
- See: docs/quality/release-criteria.md

## 17. Session Workflow (Developer Tooling)
- See: docs/talks/presenter-cheatsheet.md

## 18. Functional Programming Foundation
- See: docs/design/core-design.md

## 19. Low-Level Design & Design Patterns
- See: docs/design/core-design.md

## ADR-002, ADR-011, ADR-012, ADR-013, ADR-014, ADR-018, ADR-019, ADR-020
- See: docs/adr/README.md and docs/adr
