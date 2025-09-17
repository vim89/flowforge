# ADR-004: Experimental – Caprese (Capture Checking) for Pure UDFs & Non-Escaping Capabilities

- **Status**: Proposed (Opt-In)
- **Date**: 2025-09-10
- **Owner**: Experimental
- **Related**: ADR-001, ADR-003

## Context
We want **compile-time** guarantees that:
- UDFs intended to be **pure** cannot capture IO/capabilities,
- Connectors/handles **cannot escape** lifecycle scopes,
- Forward-compat **defaults** are deterministic.

Scala 3’s **capture checking** (experimental) provides `A -> B` (non-capturing function) and **capability** types (`X^`). :contentReference[oaicite:31]{index=31}

## Decision
Introduce a small, opt-in module `experimental-caprese`:
- `type PureFn[-A,+B] = A -> B` for **pure transforms** (compiler-enforced). :contentReference[oaicite:32]{index=32}
- `def withCapability[C, A](acquire: => C)(use: C^ => A): A` to scope connectors (no escape). :contentReference[oaicite:33]{index=33}
- `type FieldDefault[+A] = Unit -> A` for forward-compat defaults (pure by type). :contentReference[oaicite:34]{index=34}
- Add `PipelineBuilder.pureTransform(name)(f: A -> B)` alongside normal `transform`.

## Low-Level Design
- Mark example/demo files with `import language.experimental.captureChecking`. :contentReference[oaicite:35]{index=35}
- Compiler rejects:
  - closures in `pureTransform` that reference connectors/secrets,
  - returning a function that closes over a `C^` param from `withCapability`.
- No changes to engine adapters; this is **compile-time only**.

## Consequences
- Fewer production issues from UDFs capturing non-serializable or long-lived resources.
- Deterministic defaults for schema evolution.

## Risks
- Experimental feature; syntax and rules may evolve → isolate in module, make optional. :contentReference[oaicite:36]{index=36}

## References
- Scala 3 Capture Checking reference. :contentReference[oaicite:37]{index=37}
- “Capturing Types” paper excerpt. :contentReference[oaicite:38]{index=38}
