## CI/CD Review and Improvement Plan (frozen snapshot – Oct 4, 2025)

Scope: .github/workflows/*.yml, Codecov, badges, and release flows.

### Current strengths
- Modular CI with a matrix for modules in `ci.yml` and separate coverage/doc/security jobs.
- Nightly extended suite with cross‑Scala builds and integration tests.
- Codecov configured with project status per paths; now enhanced with per‑module flags (see Coverage workflow).

### Improvements (unbiased, actionable)

1) Composite action (adopted) vs reusable workflow (WHY/HOW)
- We adopted a composite action `.github/actions/sbt` for step‑level reuse (checkout → setup‑java → coursier cache → sbt). Composite actions are ideal when you need to reuse a sequence of steps inside an existing job without changing job structure. Reusable workflows (`workflow_call`) are job‑level reuse; we keep that in the toolbox for future multi‑job orchestrations. citeturn0search2

2) Hardening: permissions, pinning, and concurrency (WHY)
- Set default `permissions: contents: read` at the workflow level; elevate per job only when needed (e.g., Pages deploy, release). Minimally scoped `GITHUB_TOKEN` is an industry best practice. citeturn2search8
- Pin critical third‑party actions to full‑length commit SHAs (at least for security‑sensitive ones like `actions/checkout`, `setup-java`, `codecov-action`). Pinning to SHAs is GitHub’s guidance for supply‑chain hardening. citeturn1search3
- Add `concurrency` with `cancel-in-progress: true` for PR and push workflows to avoid queue backlogs and wasting minutes on superseded commits. citeturn1search2

3) Caching for sbt/Coursier (WHAT/HOW)
- We already use `actions/setup-java` cache=sbt; add `coursier/cache-action@v6` to also cache ~/.cache/coursier, ~/.ivy2, and ~/.sbt consistently across all jobs for faster dependency resolution and fewer cold starts. citeturn1search0turn1search1

4) Codecov flags and status checks (HOW)
- Adopt per‑module flags (core, contracts, connectors, infrastructure) for targeted PR statuses and README badges (implemented). Consider carryforward flags only if the matrix does not test all modules every commit. citeturn1search4

5) Dependabot for Actions (WHAT)
- Add/confirm `.github/dependabot.yml` to keep action versions current weekly; Actions ecosystem requires `package-ecosystem: github-actions` and `directory: "/"`. citeturn2search1turn2search6

6) Separate fast unit from heavier integration (HOW)
- Keep `ci.yml` green within ~5–8 minutes by running unit/focused tests (already done). Keep Spark/Flink integration in nightly with a clear badge (added). Optionally gate PRs touching engines/connectors with a light IT smoke job behind a label or path filter.

7) Release flow polish (WHAT)
- Ensure release workflows use least privileges and pin actions. Optionally add a pre‑release dry‑run workflow callable via `workflow_dispatch` that runs the full suite + coverage + doc site build.

### Quick diffs (status)
- [x] Coverage workflow uploads per‑module flags (core, contracts, connectors, infrastructure) and umbrella aggregate. Badges added to README.
- [x] Concurrency blocks added to CI/Coverage/Scaladoc/Nightly/Docs/Link check.
- [x] Composite SBT action `.github/actions/sbt` (pinned) for step‑level reuse.
- [x] Pin key actions to SHAs (checkout, setup‑java, coursier cache, gh‑pages, download/upload‑artifact, gh‑release, lychee, create‑issue-from‑file, deploy‑pages, upload‑pages‑artifact).
- [x] Dependabot weekly updates for GitHub Actions.
- [x] Coursier cache adopted in CI/Coverage/Nightly/Release.
- [x] Release flows restricted to `workflow_dispatch` only; tag creation handled by the release step with explicit `target_commitish`.

### References
- Reusable workflows (GitHub Docs): rationale and usage. citeturn0search2
- Concurrency: cancel in progress to avoid CI waste. citeturn1search2
- Security hardening: least‑privilege `GITHUB_TOKEN`, pin actions to SHAs. citeturn2search8turn1search3
- Codecov flags and carryforward. citeturn1search4
- Coursier caching for sbt builds. citeturn1search0
- Dependabot for Actions configuration. citeturn2search1turn2search6
