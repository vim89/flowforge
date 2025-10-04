# How to Cut a Release (Manual, Safe, Reproducible)

This project ships releases only via a manual GitHub Actions workflow to avoid accidental publishes and to conserve CI minutes.

## Prerequisites
- You have maintainer rights.
- The `main` branch is green.

## Steps (workflow_dispatch)
1) Go to GitHub → Actions → "Release" workflow.
2) Click "Run workflow".
3) Fill inputs:
   - `version`: semantic version, e.g. `0.2.0` or `1.0.0-rc1`.
   - `prerelease`: check only if this is a pre-release (e.g., rc/beta).
   - `target`: optional branch name or commit SHA for `target_commitish`. Leave empty to use the triggering commit.
4) Run the workflow. It will:
   - Validate inputs and ensure the release tag doesn’t already exist.
   - Build and test (including compile-fail proofs and engine smoke tests).
   - Assemble release artifacts and publish them to a draft GitHub Release with tag `v<version>`.
   - Publish Scaladoc to GitHub Pages.

## Notes
- `target_commitish`: If you leave it blank, the workflow uses the commit that triggered the workflow (safe default). If you set it to a branch (e.g., `main`) or a SHA, the release tag will be created at that reference.
- The workflow pins all actions to immutable SHAs and uses minimal permissions.
- Integration tests are limited to smoke checks in the release path; nightly runs the heavier suite.

## Troubleshooting
- If the release fails, follow the rollback instructions printed in the job log (delete tag and release), fix the issue, and re-run.
- For "no coverage report" warnings in the release upload step, check that the coverage step completed in the pre-release tests job.
