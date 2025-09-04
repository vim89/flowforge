# Contracts Runbook — Local and CI

This runbook shows how to validate physical schemas against data contracts locally and in CI, aligned with ADR‑011 (CI‑first gates).

## CI (Auto on PR changes + Manual Dispatch)
- Workflow: `.github/workflows/contracts-submit.yml`
- Auto trigger: on PRs to `main`/`master` touching `contracts/**` paths
- Manual trigger: `Run workflow` → fill inputs
  - `domain`: e.g., `sales`
  - `entity`: e.g., `Transactions`
  - `version`: e.g., `1.0.0`
  - `mode`: one of `parquet | delta | hive | jdbc`
  - `input`: physical path or table, e.g., `gs://my-bucket/sales/` or `delta:/mnt/tables/sales` or `db.table`
  - `expectedFormat`: `spark` (default)
  - `expectedJsonUrl` (optional): URL to expected schema JSON (Avro) (e.g., raw GitHub URL/artifact store)
  - `sourcePath` (optional): path to an existing expected JSON within this repo
  - `dryRun`: set to `true` to skip validation in manual runs (default is `false`)

Steps performed:
1. Materialize typed artifacts (stub in this repo → replace with real publisher output in production).
2. Run `validation-cli` to diff physical schema vs expected JSON.
3. Fail the workflow on mismatch. Manual runs validate when `dryRun` is `false`.

Notes
- PR auto-validation (scaffold):
  - If you set repo variables `FF_MODE` (parquet|delta|hive|jdbc) and `FF_INPUT` (physical path/table), the PR workflow will validate any changed `contracts/avro/**/*.avsc` files against the provided physical source using `validation-cli`.
  - If not set, it logs and skips validation.
  - Alternatively, create metadata files per contract:
    - `contracts/metadata/<domain>/<entity>.yaml` with keys:
      - `mode: parquet|delta|hive|jdbc`
      - `input: "/path/or/table"`
      - `expectedFormat: spark` (optional)
    - The workflow will auto-detect and use these values for each changed contract (fallback to `FF_MODE/FF_INPUT` if missing).
- Manual dispatch can validate immediately when `dryRun` is `false`. Use `expectedJsonUrl` to fetch contracts from a contracts repo/artifact store, or `sourcePath` to reuse an existing file in this repo.

### expectedJsonUrl examples
- Raw GitHub (contracts repo):
  - `https://raw.githubusercontent.com/<org>/<contracts-repo>/<ref>/avro/sales/Transactions.v1.0.0.avsc`
- Artifact store (S3/GCS; pre-signed):
  - `https://storage.googleapis.com/<bucket>/contracts/avro/sales/Transactions.v1.0.0.avsc?X-Goog-Algorithm=...`
- Internal registry service (if available):
  - `https://contracts.acme.internal/api/contracts/avro/sales/Transactions/1.0.0`

## Local (Developer Smoke Check)
- SBT input task (delegates to validation‑cli):
```
sbt ffValidate --mode parquet --input "/data/sales/" --expected-json contracts/avro/sales/Transactions.v1.0.0.avsc --expected-format spark
```
Arguments map 1:1 to validation‑cli flags.

## Credentials & Environments
- GCS/S3/Delta/Hive sources may require credentials; prefer workload identity/OIDC in CI.
- For local runs, configure cloud SDKs or environment variables per provider.

## References
- Overview: `docs/contracts/OVERVIEW.md`
- ADR‑010: `docs/adr/010-contracts-authoring-operating-model.md`
- ADR‑011: `docs/adr/011-contracts-compile-build-gates.md`
- Validation CLI: `modules/validation-cli/src/main/scala/com/flowforge/validation/SchemaValidateCli.scala`
### Toggle Avro `.avsc` Generation
- Some organizations may prefer Scala case classes as the only source of truth and derive schemas only in specific environments.
- Use a boolean flag to toggle `.avsc` generation in contracts CI:
  - `generateAvsc` input in the contracts publish workflow (see `.github/workflows/contracts-publish-sdk.yml`).
  - Or a repo variable `FF_GENERATE_AVSC` (read in the workflow and applied as default).
- When disabled, typed compile gates still apply; physical validation can operate on Spark JSON canonicalization instead of `.avsc`.
