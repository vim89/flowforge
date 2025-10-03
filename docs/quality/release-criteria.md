# Release Criteria — FlowForge v1.0.0

This page defines the objective, testable criteria for shipping v1.0.0.

## Build & Platform
- CI runs on JDK 17 (Ubuntu 22.04), sbt 1.9+.
- Scala 2.13 primary; Scala 3 sources compile in `core` (no Spark dependencies).
- `engines-flink`: cross-build limited to 2.12 only (Flink Scala API constraint).

## Contracts & Compile‑Time Safety
- Macro `SchemaConforms` proves Exact/Backward/Forward/Ordered/ByPosition policies.
- `compile-fail-tests` contain at least the 3 mandatory scenarios: missing sink, schema mismatch, illegal evolution.
- Error messages show path‑aware diffs; docs include example screenshots.

## Engines & Quality
- Spark data algebra passes unit tests and smoke ITs with local[*] (Delta optional).
- DQ runs natively; when Deequ is added to classpath, reflection path activates automatically and degrades gracefully on failure.

## DX & Template
- g8 template builds and runs locally; README shows red→green flow under 3 minutes.
- sbt aliases: `ffDev`, `ffCheck`, `ffRunSpark` work as documented.

## Docs & Talks
- README, start‑here, getting‑started updated; “Why” added to talks with runtime vs compile‑time boundary.
- ADR index current; v1.0 acceptance checklist linked from README.

