## Contracts & Schema Validation

- If this PR changes contracts under `contracts/**`, a CI workflow will run to validate physical schemas against the submitted contracts.
- You can also run the workflow manually: Actions → `contracts-submit (CI-first gates scaffold)` → `Run workflow`.
- For a local smoke check:
  - `sbt ffValidate --mode <parquet|delta|hive|jdbc> --input "<path-or-table>" --expected-json contracts/avro/<domain>/<entity>.v<version>.avsc --expected-format spark`

Links
- Overview: `docs/contracts/OVERVIEW.md`
- Runbook: `docs/contracts/RUNBOOK.md`
- ADR‑010: `docs/adr/010-contracts-authoring-operating-model.md`
- ADR‑011: `docs/adr/011-contracts-compile-build-gates.md`

