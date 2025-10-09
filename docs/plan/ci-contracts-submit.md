# PLAN - CI Contracts Submit (Forms + Validation CLI)

## 1) Scope (Minimal Viable Change)
- Add GitHub Actions workflow (Forms) to author/update contracts; CI materializes typed artifacts and validates physical schemas using validation-cli.
- Keep sbt plugin optional; delegate to CLI for parity.

## 2) Deliverables
- `.github/workflows/contracts-submit.yml` with `workflow_dispatch` inputs: `domain`, `entity`, `version`, `sourcePath`, `format`, `policy`.
- Composite action `.github/actions/contract-materialize/` to stage `contracts/` artifacts (case classes/avsc or links) for the run.
- Validation step invoking:
  - `sbt "validation-cli/run --mode ${{ inputs.format }} --input '${{ inputs.sourcePath }}' --expected-json contracts/avro/${{ inputs.domain }}/${{ inputs.entity }}.v${{ inputs.version }}.avsc --expected-format spark"`
  - or local alias `sbt ffValidate ...`.

## 3) Tasks
1. Author workflow with inputs and matrix on JDK ≤ 21; cache Ivy/Coursier to speed up runs.
2. Add composite action to materialize artifacts into `contracts/` for the workflow context.
3. Document provider auth (GCP/AWS) via OIDC or repo environment secrets; no hardcoded credentials.
4. Update ADR‑011/021 Evidence with usage examples and screenshots (later pass).

## 4) Tests
- Dry-run the workflow on a demo branch with sample `.avsc` and a known path; assert failure shows actionable diffs.
- Local parity: run `sbt ffValidate` with the same inputs.

## 5) Risks & Mitigations
- Cloud auth friction: provide clear instructions for setting up OIDC or environment-level secrets.
- Runtime variability: pin Spark version and run on ubuntu-latest, JDK 17/21 matrix.

## 6) Validation & Acceptance
- Manual dispatch succeeds and fails on schema drift with readable diffs.
- Parity verified locally via `ffValidate`.
- CI green otherwise; no changes to build graph.

