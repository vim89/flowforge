# Contributors cverage crosswalk

> This file maps contributor guidance to current canonical locations.

## Sources
- Canonical: handbook `docs/contributing/HANDBOOK.md`, ADRs under `docs/adr/*`.

## Crosswalk (Selected Headings → Canonical)
- Teaser / Brutal truth / Targets vs Today → Handbook §1; ADR‑018; ADR‑016
- Compile‑time contracts & effect system pitch → Handbook §1–2; ADR‑011; ADR‑012
- Archetype: what/why/benefits → Handbook §10 (Template Philosophy); ADR‑004
- Data contracts (what/agreements/SLA/owners) → ADR‑010; Handbook §5 (Correctness)
- Proposed automation (portal, CI publisher, KPIs) → ADR‑010; ADR‑011; Handbook §10
- Layered architecture (application/framework/domain/service/core/infra) → Handbook §2; ADR‑013; ADR‑004
- Key principles (DIP, Effect polymorphism, Type safety, Composability, Resource safety, Multi‑engine, Plugins) → ADR‑001/002/012/013; Handbook §2–3
- SOLID & Design Patterns → Handbook §5–7 (patterns), linked to ADRs
- Functional foundation (immutability, ADTs, monads, type classes, etc.) → Handbook §7
- Effect systems: developer choice, decorator pattern with effects → ADR‑012; Handbook §6–8
- Advanced typelevel (phantom types, LabelledGeneric, Kleisli) → ADR‑011/019; Handbook §7
- Production pipeline Concerns (35+) → ADR‑020; Handbook §5
- Technical implementation Strategy (tagless final, typed errors, schema evolution) → Handbook §3, §6–9; ADR‑011/012
- ETL & pipeline patterns (read → write, CDC, profiling, audit) → Handbook §4; ADR‑020
- Resource management (Resource[F,_], retries, timeouts, circuit breaker) → Handbook §8
- Error modeling strategy (EitherT/ZIO, DLQ) → Handbook §9
- Template generation philosophy → Handbook §10; ADR‑004/011
- Prototype integration & Incremental adoption → Handbook §11
- Refactoring strategy → Handbook §12
- Security/config/observability → Handbook §13; ADR‑013
- Testing/QA strategy → Handbook §14; ADR‑014
- Anti‑patterns to reject → Handbook §15
- 30‑point checklist → ADR‑020; Handbook §16
- Session workflow / goals / context re‑init → Handbook §17

If an item is missing, open an issue linking the exact lines in the backup and propose a Handbook section/ADR to host it.
