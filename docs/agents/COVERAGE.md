# Agents Coverage Crosswalk

> This file maps content from `docs/archive/backups/AGENTS.backup.2025-09-04.md` to the current canonical locations.

## Sources
- Backup: `docs/archive/backups/AGENTS.backup.2025-09-04.md`
- Canonical: Handbook `docs/agents/HANDBOOK.md`, ADRs under `docs/adr/*`.

## Crosswalk (Selected Headings → Canonical)
- Teaser / Brutal truth / Targets vs Today → Handbook §1; ADR‑018; ADR‑016
- Compile‑time contracts & effect system pitch → Handbook §1–2; ADR‑011; ADR‑012
- Archetype: what/why/benefits → Handbook §10 (Template Philosophy); ADR‑004
- Data Contracts (what/agreements/SLA/owners) → ADR‑010; Handbook §5 (Correctness)
- Proposed Automation (portal, CI publisher, KPIs) → ADR‑010; ADR‑011; Handbook §10
- Layered Architecture (Application/Framework/Domain/Service/Core/Infra) → Handbook §2; ADR‑013; ADR‑004
- Key Principles (DIP, Effect polymorphism, Type safety, Composability, Resource safety, Multi‑engine, Plugins) → ADR‑001/002/012/013; Handbook §2–3
- SOLID & Design Patterns → Handbook §5–7 (patterns), linked to ADRs
- Functional Foundation (immutability, ADTs, monads, type classes, etc.) → Handbook §7
- Effect Systems: developer choice, decorator pattern with effects → ADR‑012; Handbook §6–8
- Advanced Type‑Level (phantom types, LabelledGeneric, Kleisli) → ADR‑011/019; Handbook §7
- Production Pipeline Concerns (35+) → ADR‑020; Handbook §5
- Technical Implementation Strategy (tagless final, typed errors, schema evolution) → Handbook §3, §6–9; ADR‑011/012
- ETL & Pipeline Patterns (read → write, CDC, profiling, audit) → Handbook §4; ADR‑020
- Resource Management (Resource[F,_], retries, timeouts, circuit breaker) → Handbook §8
- Error Modeling Strategy (EitherT/ZIO, DLQ) → Handbook §9
- Template Generation Philosophy → Handbook §10; ADR‑004/011
- Prototype Integration & Incremental Adoption → Handbook §11
- Refactoring Strategy → Handbook §12
- Security/Config/Observability → Handbook §13; ADR‑013
- Testing/QA Strategy → Handbook §14; ADR‑014
- Anti‑patterns to reject → Handbook §15
- 30‑Point Checklist → ADR‑020; Handbook §16
- Session Workflow / Goals / Context re‑init → Handbook §17

If an item is missing, open an issue linking the exact lines in the backup and propose a Handbook section/ADR to host it.
