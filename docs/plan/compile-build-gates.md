# PLAN — Compile-Time & Build-Time Gates (CI-first)

## 1) Scope (Minimal Viable Change)
- Goal: Contracts authored by non-engineers via GitHub Actions Forms; CI materializes typed artifacts and runs schema validation against physical sources (Parquet/Delta/Hive/JDBC). PR fails on mismatches.
- Out of scope: Full registry integration and advanced compatibility policies (reserved for later ADR).

## 2) Files to Touch (exact)
1. `.github/workflows/contracts-submit.yml` — GHA workflow with `workflow_dispatch` + Forms (inputs: domain, entity, version, paths, policy).
2. `.github/actions/contract-materialize/` — composite action: validate contract YAML/JSON, generate typed stubs, place under `contracts/` or temp path for PR.
3. Use `modules/validation-cli` inside workflow to canonicalize Spark schemas and diff vs expected.
4. docs: Update ADR-011, Evidence, Handbook to reflect CI-first.

## 3) Workflow Sketch
```yaml
name: contracts-submit
on:
  workflow_dispatch:
    inputs:
      domain: { required: true }
      entity: { required: true }
      version: { required: true }
      sourcePath: { required: true, description: 'gs://... or s3://... or delta path' }
      format: { required: true, options: [parquet, delta, hive, jdbc] }
      policy: { required: true, options: [exact, superset, subset] }
jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Materialize typed artifacts
        uses: ./.github/actions/contract-materialize
        with: { domain: ${{ inputs.domain }}, entity: ${{ inputs.entity }}, version: ${{ inputs.version }} }
      - name: Validate physical schema
        run: |
          sbt "validation-cli/run --mode ${{ inputs.format }} --input '${{ inputs.sourcePath }}' --expected-json contracts/avro/${{ inputs.domain }}/${{ inputs.entity }}.v${{ inputs.version }}.avsc"
```

## 4) Risk Mitigation
- Fast schema-only reads; use metadata/footers.
- Credentials for cloud paths via OIDC or secrets in repository environments.
- Keep sbt AutoPlugin optional and delegating to CLI to avoid logic drift.

## 5) Validation
- Dry-run this workflow on a sample contract and known path; assert failure with clear diffs when mismatched.

## 6) Acceptance Criteria
- GHA workflow exists and fails PRs on mismatches with actionable diffs.
- Typed artifacts generated and attached to PR (or committed under a controlled path in the branch).
- Docs (ADR-011, Evidence, Handbook) updated to reflect CI-first approach.
