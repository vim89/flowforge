# Contracts-as-Code For Non-Technical Teams (No Coding Required)

> Archived: Superseded by ADR-010 Contracts Authoring & Operating Model. See `docs/adr/010-contracts-authoring-operating-model.md`.

Updated: 2025-09-03
Status: Adopt alongside CONTRACTS_COMPILE_AND_BUILD_GATES.md and CONTRACTS_AUTHORING_AND_OPERATING_MODEL.md

## What This Delivers
- A predictable, repeatable way for Data Stewards/Modelers/Product to define data contracts without code.
- Contracts live in Git as human‑readable artifacts; a “publisher CI” turns them into versioned SDK JARs.
- Downstream builds fail fast if code doesn’t match the contract (compile‑time) or storage drifts (build‑time).

## The Contract Model (Artifacts in Git)
- Schema (Avro primary; JSON Schema optional): `contracts/avro/<domain>/<entity>.vX.Y.Z.avsc`
- Data Quality rules (YAML): `contracts/dq/<domain>/<entity>.yaml`
- Metadata (YAML): `contracts/metadata/<domain>/<entity>.yaml`
- Optional examples/profiles: `contracts/examples/<domain>/<entity>/*`

Minimal Avro schema example (excerpt):
```
{
  "type": "record",
  "name": "SalesV1",
  "namespace": "com.acme.sales",
  "fields": [
    { "name": "invoiceNumber", "type": "string" },
    { "name": "customerId",    "type": "string" },
    { "name": "amount",         "type": "double" },
    { "name": "eventTs",        "type": "long" }
  ]
}
```

Minimal DQ YAML example:
```
rules:
  - name: not_null_invoiceNumber
    type: not_null
    field: invoiceNumber
    severity: error
  - name: non_negative_amount
    type: range
    field: amount
    min: 0
    severity: error
```

Metadata YAML example:
```
owner: data-platform@sales.acme
slas:
  freshness_minutes: 60
  late_data_policy: accept_with_flag
compatibility: FULL
subscribers:
  - analytics@sales.acme
```

## How Non‑Technical Authors Create Contracts

1) Begin in the Contract Portal (or Spreadsheet Intake)
- Choose “Create Contract” or “Import from Data Source”.
- Provide basics: domain, entity, owners, description.

2) Import From Data Source (wizards)
- Parquet (local/GCS/S3): point to path/prefix → schema inferred from footer (no data scan).
- Delta: point to table/path → DESCRIBE DETAIL or Spark schema.
- Hive: choose database.table → schema from metastore.
- JDBC: connect + select table → schema from DatabaseMetaData.
- CSV: upload sample/header → infer types; confirm overrides.
- Optional “Profile sample” switch to propose DQ rules (nullability, ranges, set membership, regex).

3) Preview & Edit
- Portal renders the proposed schema and DQ rules side‑by‑side.
- Stewards adjust field names, descriptions, nullability, constraints.
- Set compatibility policy (BACKWARD/FULL), SLA/freshness.

4) Submit
- Portal opens a PR to `contracts.git` with `.avsc`, `.yaml` files.
- Reviewers (Architect + Steward) approve. CI runs on the PR.

## Publisher CI (Contracts Repo)
On PR merge, CI performs:
- Validate + lint: field naming, reserved words, nullability defaults, uniqueness of rule names.
- Compatibility check: call Schema Registry (if enabled) and enforce the domain policy.
- SDK generation:
  - Avro→Scala codegen (sbt‑avrohugger or avro4s codegen) to case classes.
  - Emit typed endpoints + witnesses (TypedSource/TypedSink, SchemaEq/SchemaConforms hooks).
  - Build and publish `com.acme.contracts:<domain>-contracts_2.13:<version>`.
- Register the new version in Schema Registry (if applicable).
- Publish to Catalog (DataHub/OpenMetadata): fields, descriptions, owners, tags, contract links.
- Notify subscribers via Slack/Webhook.

## Downstream Consumption (Developers)
- Add dependency on the contract SDK JAR(s).
- Use only typed endpoints in `PipelineBuilder2`.
- Compile‑time gates: Out vs sink contract mismatch fails scalac (Exact or ExactUnordered policy).
- Build‑time gates: shared CI job validates physical schemas vs contracts before any run.

## Build‑Time Physical Validation (Shared CI)
- Parquet: read schema (footer) → canonical model → diff vs contract.
- Delta/Hive: use Spark to fetch StructType → diff vs contract.
- JDBC: map JDBC types to canonical → diff vs contract.
- Report missing/extra/type/nullability with remediation hints.
- Optionally run small DQ canaries on last partition.

## Evolution & Governance
- Default: backward‑compatible additions allowed; breaking changes need major version + dual‑write window.
- Registry compatibility modes enforce this mechanically (BACKWARD or FULL_TRANSITIVE).
- Deprecations: communicate via portal and subscribers list.

## Security & Approvals
- PR approvals required for any schema/DQ/metadata change.
- Portal keeps audit trail (who proposed/approved; diffs rendered in UI).
- Service accounts for CI to publish artifacts; read‑only for most users.

## Rollback
- If physical data drifts, shared CI turns red → block downstream builds.
- Producers either fix the write job or update the contract through proper review.
- Emergency: freeze consumers using last good SDK version; contracts are versioned.

## Integration With FlowForge
- Compile‑time enforcement: `SchemaEq` (Exact) and `SchemaConforms` (ExactUnordered) in core.
- Typed‑only builder: invalid pipelines don’t compile.
- Physical validation: small CLI/CI job (parquet/delta/hive/jdbc) to fail builds before runs.
- Optional typed‑spark helpers (Frameless) for safer projections/joins.

## Minimal Viable Adoption (30–60 min)
- Create contracts repo with the folder structure above.
- Add a simple Portal (or Spreadsheet intake → PR bot).
- Add Publisher CI that:
  - Validates → Codegens → Publishes SDK JAR → Updates catalog/registry.
- Add Shared CI job template for consumers to validate physical storage.
- In FlowForge projects, depend on the SDK JARs and use typed endpoints only.

## FAQs
- Q: Can authors avoid Avro/JSON completely?
  - A: The Portal hides it; they only fill forms and review a human‑readable diff before PR.
- Q: What about nested/array types?
  - A: Supported in Avro; Portal allows adding nested fields; physical validators handle nested by flattening names with a path convention.
- Q: What if storage is unreachable during compile locally?
  - A: Shared CI always runs validation; local builds can pass with `ff.offline=true` but can’t merge unless CI passes.

See also:
- CONTRACTS_COMPILE_AND_BUILD_GATES.md — compile/build gates & APIs
- CONTRACTS_AUTHORING_AND_OPERATING_MODEL.md — operating model & roles
> Archived (2025-09-04): Superseded by ADR-010 Contracts Authoring & Operating Model. See `docs/adr/010-contracts-authoring-operating-model.md` and Evidence.
