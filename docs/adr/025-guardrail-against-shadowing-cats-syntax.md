---
id: 025
title: Guardrail Against Shadowing Cats Syntax (toValidated/ toValidatedNel)
status: Accepted
date: 2025-10-04
authors: FlowForge Team
supersedes: []
superseded-by: []
---

Context
- We observed a long-standing, flaky failure in core tests where sequencing a list of `Result[A]` into `ValidatedNel` blew the stack or behaved inconsistently.
- Root cause: we defined an extension `toValidatedNel` on `Result[A]` (alias of `Either[FlowForgeError, A]`) that delegated to `r.toValidatedNel`. Because `r` is an `Either`, name resolution chose our own extension again (not Cats’ `EitherOps`), causing infinite recursion at runtime when used by `sequenceV`. This remained latent until a traversal path exercised it.
- Cats already provides `EitherOps.toValidated` and `toValidatedNel` for conversions; Validated is the canonical datatype for error accumulation (applicative) vs Either (short‑circuiting).

Decision
- Do not define project-local extension methods that reuse Cats syntax names on standard datatypes (e.g., `toValidated`, `toValidatedNel`, etc.).
- Remove FlowForge’s `ResultOps.toValidatedNel` and rely on Cats’ provided conversions, or implement explicitly named helpers when necessary.
- Enforce this via Scalafix with regex-based DisableSyntax rules that ban definitions of `def toValidated` and `def toValidatedNel` anywhere in the codebase.

Consequences
- Source remains clear and unambiguous: conversions use Cats syntax or explicit constructors (`Validated.valid`, `Validated.invalidNel`).
- Prevents a class of subtle runtime failures from accidental recursion/shadowing.
- Minor migration: any FlowForge-defined extensions with those names must be renamed or removed. We removed `ResultOps.toValidatedNel` in `modules/core/src/main/scala/com/flowforge/core/safety/Safety.scala` and adjusted nothing else; call sites automatically use Cats syntax.

Implementation
- Code change: delete `toValidatedNel` method from `ResultOps` (Safety.scala). Keep `mapError`.
- Scalafix: `.scalafix.conf` adds two DisableSyntax regex rules that only match method definitions:
  - `id = noShadow_Cats_toValidatedNel`, pattern `\bdef\s+toValidatedNel\b`
  - `id = noShadow_Cats_toValidated`, pattern `\bdef\s+toValidated\b`
- Tests: re-enabled `sequenceV accumulates errors from Results (bounded)` in `SafetySpec` and verified full core suite.

Alternatives Considered
- Keep the method but implement safely (pattern match). Rejected to avoid future ambiguity and to align with policy of not shadowing Cats syntax on std types.
- Write a custom semantic Scalafix rule. Deferred; regex enforcement is sufficient and low‑overhead. If needed, we can author a semantic rule later to check receiver types.

References
- Cats Either: short‑circuit behavior and conversions: https://typelevel.org/cats/datatypes/either.html
- Cats Validated: error accumulation semantics and APIs: https://typelevel.org/cats/datatypes/validated.html
- Cats EitherOps/Validated API (official): https://typelevel.org/cats/api/cats/syntax/EitherOps.html

Acceptance Criteria
- No project code defines `def toValidated` or `def toValidatedNel`.
- `sbt "scalafixAll"` flags violations; CI fails on reintroduction.
- Core tests covering `sequenceV` pass and remain enabled.

