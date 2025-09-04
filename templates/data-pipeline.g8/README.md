FlowForge Data Pipeline Template (giter8)
=======================================

Purpose
- Bootstrap a Cats-Effect based FlowForge project with:
  - Compile-time schema gates on the typed path using typed sources/contracts/sinks (shapeless LabelledGeneric)
  - Type-safe pipelines and effect-polymorphic orchestration
  - Spark engine wiring option
  - Metrics/logging hooks

Usage
- Install giter8 then run:
  `g8 file://$PWD/templates/data-pipeline.g8 -o my-pipeline`
- Enter the generated project and run: `sbt run`
  - Runs a Spark local demo which writes a small DataFrame as Delta and reads it back.

Notes
- Use the typed APIs: `TypedIO.localParquetSource[R]`, `TypedIO.localParquetSink[R]`, `TypedContract[A, R]` + `contractTyped`.
- This is an initial scaffold. Engine and connector dependencies are left as version variables.
- Choose your effect runtime (Cats-Effect default). ZIO variant can be added later.
- To upgrade to FlowForge APIs, add published artifacts for `flowforge-core`, `flowforge-framework`, and `flowforge-engines-spark` in `build.sbt`, then replace the demo with FlowForge pipeline wiring.
- Reality note (2025-09-04): This template is self-contained and does not depend on FlowForge artifacts by default. To adopt FlowForge’s typed contracts and CI gates, see `docs/contracts/OVERVIEW.md` in the FlowForge repo and add the corresponding dependencies when published.
