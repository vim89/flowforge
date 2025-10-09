# PLAN - Effect System & Purity

## 1) Scope (Minimal Viable Change)
- **Goal**: Keep transforms pure; slim leaf modules to depend on `EffectSystem[F]` only (no direct IO/ZIO).
- **Out of scope**: Removing instances from core.

## 2) Files to Touch (exact)
1. `project/Dependencies.scala` - adjust `forModule` to avoid bringing both stacks into leaf modules.
2. Leaf module sources - replace direct IO/ZIO imports with `EffectSystem[F]` constraints (as needed).

## 3) Patch Sketch (pseudo-diffs)
```scala
// project/Dependencies.scala (sketch)
case "connectors" => Core.functional ++ Testing.unit ++ Connectors.all // drop effectSystems here
```

## 4) Risk mitigation
- Phase changes; ensure tests compile; only drop where unused.

## 5) Validation
- `sbt compile` green; grep leaf modules for IO/ZIO imports.

## 6) Acceptance criteria
- Leaf modules free of both stacks; core keeps instances.
