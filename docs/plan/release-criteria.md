# Release Criteria - FlowForge v1.0.0

This page defines the objective, testable criteria for shipping v1.0.0. It says what has to be true, not
what is true today: current status and the open gaps are in [v1.0 readiness](v1.0-readiness.md), which is the
one place a readiness claim belongs.

## Build & Platform
- CI runs on JDK 17 (Ubuntu 22.04), sbt 1.9+.
- Scala 2.13 primary; Scala 3 sources compile in `core` (no Spark dependencies).
- `engines-flink`: builds and resolves on a single axis, or the module is removed.
 - Spark IT job pinned to Java 17; println banned in production sources via CI guard.

## Contracts & Compile‑Time Safety
- Macro `SchemaConforms` proves Exact/Backward/Forward/Ordered/ByPosition policies.
- `compile-fail-tests` contain at least the 3 mandatory scenarios: missing sink, schema mismatch, illegal evolution.
- Error messages show path‑aware diffs; docs include example screenshots.
 - Builder typestate enforced: tests prove incomplete pipelines cannot build and typed endpoints require SchemaConforms evidence.

## Engines & Quality
- Spark data algebra passes unit tests and a local read-to-write test with local[*] (Delta optional).
- No pure operation on a Spark dataset derives its result from the decoded sample.
- `validate` enforces the contract it is given, or is removed from the algebra.
- DQ runs natively; when Deequ is added to classpath, reflection path activates automatically and degrades gracefully on failure.

## DX & Template
- g8 template builds and runs locally; README shows red→green flow under 3 minutes.
- sbt aliases: `ffDev`, `ffCheck`, `ffRunSpark` work as documented.

## Docs & Talks
- README, start‑here, getting‑started updated; “Why” added to talks with runtime vs compile‑time boundary.
- ADR index current; v1.0 acceptance checklist linked from README.
