# ADR-008: SLA, Metrics & Maintenance (OPTIMIZE/VACUUM) – Keep It Engine-Native

- **Status**: Accepted
- **Date**: 2025-09-10
- **Owner**: Observability & Storage
- **Related**: ADR-002, ADR-006, ADR-007

## Context
We need SLA visibility and lakehouse maintenance without re-creating vendor features.

## Decision
- **Metrics**: publish pipeline counters/timers via **OpenTelemetry Metrics** API; leave backend/exporter choice to deployers. :contentReference[oaicite:58]{index=58}
- **SLA**: expose `run_duration`, `freshness_lag`, `rows_in/out`, and `sla_status`. Orchestrators (Airflow/Dagster/dbt) consume these metrics; FlowForge does not ship its own SLA UI.
- **Maintenance**: provide a **separate, off-SLA** job to run **Delta OPTIMIZE/Z-ORDER** and **VACUUM**, using native SQL/commands; FlowForge does not implement compaction itself. (Use Delta’s documented `MERGE`/maintenance flow.) :contentReference[oaicite:59]{index=59}

## Low-Level Design
- `MetricEmitter` with OTel API (counter, histogram, observable gauges). :contentReference[oaicite:60]{index=60}
- `MaintenanceJob` that runs native commands against Delta tables (bin-pack, Z-order on hot columns, then VACUUM respecting retention). :contentReference[oaicite:61]{index=61}

## Consequences
- Transparent metrics; easy integration with Prometheus/Grafana/Datadog via OTel.
- Healthy tables and predictable latency.

## Risks
- Misconfigured VACUUM may remove files needed for time travel → warn and require explicit retention config.

## References
- OpenTelemetry Metrics spec/overview. :contentReference[oaicite:62]{index=62}
- Delta Lake ops (MERGE and maintenance docs). :contentReference[oaicite:63]{index=63}
