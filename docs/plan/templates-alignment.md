# PLAN - Modules/Templates Alignment

## 1) Scope (Minimal Viable Change)
- **Goal**: Mirror `templates/data-pipeline.g8` under `modules/templates` without changing build.sbt.
- **Out of scope**: Build graph changes; new plugins.

## 2) Files to Touch (exact)
1. `modules/templates/README.md` - add banner clarifying mirror of canonical g8 path.
2. `modules/templates/data-pipeline.g8/**` - copy of `templates/data-pipeline.g8/**`.

## 3) Patch Sketch (pseudo-diffs)
```text
modules/templates/README.md
+ Canonical source: ../../templates/data-pipeline.g8
+ This directory mirrors g8 files for discoverability.
```

## 4) Risk mitigation
- Keep a single source of truth (top-level templates/); note in README.

## 5) Validation
- `sbt compile` unchanged; run locally.

## 6) Acceptance criteria
- Files present under `modules/templates/**`; no build.sbt changes.
