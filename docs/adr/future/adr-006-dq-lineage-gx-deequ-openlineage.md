# ADR-006: Data Quality & Lineage (Great Expectations/Deequ + OpenLineage)

- **Status**: Accepted
- **Date**: 2025-09-10
- **Owner**: Observability
- **Related**: ADR-001, ADR-002, ADR-007

## Context
We need human-readable data quality checks and standard lineage for impact analysis.

## Decision
- Provide **pluggable DQ**:
    - **Great Expectations**: model checks as **Expectations** and run via **Checkpoints**; produce Data Docs. :contentReference[oaicite:43]{index=43}
    - **Deequ**: Spark-based “unit tests for data” (metrics + constraints). :contentReference[oaicite:44]{index=44}
- Emit **OpenLineage** events for `Job`/`Run`/`Dataset` at read/transform/write boundaries. :contentReference[oaicite:45]{index=45}

## Low-Level Design
- `DataQualitySink`:
    - `GreatExpectationsRunner` and `DeequRunner`; both return pass/fail + metrics.
- `LineageEmitter`:
    - Emit `START`/`RUNNING`/`COMPLETE`/`FAIL` events per spec, with inputs/outputs and facets. :contentReference[oaicite:46]{index=46}
    - Respect OpenLineage naming rules for jobs/namespaces. :contentReference[oaicite:47]{index=47}

## Consequences
- Auditable DQ with artifacts; lineage portable across platforms.

## Risks
- DQ can slow runs; allow async publishing or sampling.

## References
- Great Expectations concepts. :contentReference[oaicite:48]{index=48}
- Deequ overview (AWS/OSS). :contentReference[oaicite:49]{index=49}
- OpenLineage spec (object model, run cycle). :contentReference[oaicite:50]{index=50}
