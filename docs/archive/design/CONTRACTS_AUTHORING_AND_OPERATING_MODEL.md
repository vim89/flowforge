# Contracts Authoring & Operating Model (Non-Technical Friendly)

Updated: 2025-09-03
Status: Adopt alongside compile/build gates

## Goals
- Let Data Stewards/Modelers/Product define data contracts without writing code.
- Keep a single source of truth, governance, and audit trail.
- Deliver versioned, consumable artifacts (SDK JARs) that give compile-time safety to engineers.

## Roles
- Data Steward / Modeler: authors contracts, owns semantics.
- Product Manager: defines fields and expectations.
- Data Architect: reviews compatibility and evolution.
- Platform Team: maintains registry, portal, CI pipelines.
- Consumer Engineers: depend on contract SDKs; no local codegen.

## Authoring Channels
1) Contract Portal (recommended)
- A simple internal web app (could be built with Retool/Backstage/low-code) with forms for:
  - Entities, fields (name, type, nullable, description, constraints)
  - Versioning notes and owners
- Validates entries (naming, types, constraints) and previews the generated schema.
- On submit: opens a PR to `contracts.git` with `.avsc`/`.proto` and a metadata YAML.

2) Registry UI (alternative)
- Use a managed Schema Registry UI to author JSON Schema/Avro. The portal syncs subjects to Git for audit.

3) Spreadsheet/CSV intake (fallback)
- Stewards fill a templated sheet; the portal converts it to Avro/JSON Schema and opens a PR.

## Storage of Truth
- `contracts.git` repository holds:
  - `/avro/<domain>/<entity>.v<semver>.avsc` (or `/proto/...`)
  - `/metadata/<domain>/<entity>.yaml` (owners, SLAs, DQ expectations, glossary links)
- All changes via PR with reviewers (Architect + Steward). CI lints/validates before merge.

## CI on Merge (Publisher Pipeline)
- Validate contracts (schema lint, uniqueness, default/nullability rules).
- Compatibility check against Registry (policy: BACKWARD/FULL_TRANSITIVE per domain).
- Generate SDKs:
  - Scala case classes and typed witnesses (TypedSource/TypedSink, SchemaEq evidence).
  - Package per domain into JARs (e.g., `com.acme.contracts:sales-contracts_2.13:1.2.3`).
- Publish artifacts to Maven/Nexus/Artifactory.
- Update Registry subjects to the merged versions.
- Sync docs to the data catalog (DataHub/OpenMetadata) with owners and descriptions.

## Consumer Workflow (Downstream Teams)
- Add dependency on the contract SDK JAR(s).
- Build pipelines with `PipelineBuilder2` typed endpoints only.
- Compile-time gates fire via SchemaEq evidence; build-time physical checks run in your repo (see CONTRACTS_COMPILE_AND_BUILD_GATES.md) and in shared CI.

## Physical Validation (Shared CI)
- Nightly and on-change: validate physical schemas (Hive/Delta/Parquet) vs contracts; alert on drift; block promotions.
- Optional: small sample DQ checks on canary partitions for freshness/uniqueness/nulls.

## Evolution & Governance
- Default policy: backward-compatible additions; breaking changes require a major version and dual-write period.
- Deprecation window and auto-notifications to subscribers.
- Registry compatibility modes enforce this mechanically.

## Why This Works for Non‑Technical Authors
- They never touch code or sbt.
- The portal gives guardrails and readable diffs.
- Engineers get strongly-typed SDKs and fail-fast builds.

## Minimal Build Contracts (MVP)
- Start with Avro as the contract format.
- Provide a portal-to-PR path and a publisher CI that emits the SDK JARs.
- Add Protobuf later if a team mandates it.

## Interfaces with FlowForge
- FlowForge core depends only on the SDK JAR types/witnesses.
- `PipelineBuilder2` requires TypedSource/TypedSink from SDK.
- The physical validation tasks can live in a tiny shared sbt/mill plugin or a standalone CLI used in CI; engineers don’t need to author schemas locally.
> Archived (2025-09-04): Superseded by ADR-010 Contracts Authoring & Operating Model. See `docs/adr/010-contracts-authoring-operating-model.md` and Evidence.
# Contracts Authoring & Operating Model (Non-Technical Friendly)

> Archived: Superseded by ADR-010 Contracts Authoring & Operating Model. See `docs/adr/010-contracts-authoring-operating-model.md`.
