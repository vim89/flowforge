# Event: ScalaIO Paris 2025
Link: https://scala.io/
PaperCall: https://www.papercall.io/speakers/164612/speaker_talks/300657-compile-time-contracts-fiber-safe-data-pipelines-scala-s-effect-system-in-action

## Elevator Pitch
Writing data pipelines?
Say goodbye to config/metadata chaos! Let Scala’s type & effect systems co-author them with you.
We’ll show how we built a contract-first, pluggable platform using Giter8, Refined, Cats Effect, ZIO - delivering compile-time guarantees and fiber-safe execution.

## Descriptions
What if your data platform stopped relying on configuration-driven / metadata-driven and postmortems - and instead enforced correctness, traceability, and effect boundaries at compile time?
In this talk, we’ll walk through how we built a production-ready data pipeline archetype system in Scala. We used Giter8 templates to scaffold pipelines that are contract-driven, type-safe, effectful, and pluggable - all enforced through the Scala type system and effect libraries.
🔧 You’ll see how:
•	📦 Giter8 templates bootstrap consistent, compile-time safe projects
•	🛡️ Refined types validate configuration before runtime
•	✅ Cats ValidatedNel catches multi-rule violations in DQ checks
•	🔌 Type classes enable pluggable validation and ingestion
•	⚙️ ZIO Layers and Cats Effect offer fiber-safe orchestration
•	🧠 Kyo and Caprese add effect composition and static guarantees
•	🚀 Trait-based runners switch between Spark, Flink, and Kafka
•	📊 Data Quality and custom rules enforce data contract quality at runtime

🧪 We’ll demo a real pipeline: scaffolded from template, validated with contracts, and executed via a modular runtime - showing how functional design meets engineering scale.
Whether you’re a platform engineer, data architect, or Scala practitioner, this talk showcases how to build systems that scale with correctness, traceability, and developer delight.

## Duration
45-60 minutes

## Notes
- The speaker has over a 12 years of experience building distributed data platforms at scale.
- This talk blends real production use-cases with advanced Scala features like type classes, refined types, ZIO layers, and effect capture checking. 
- Demos will include real code, showing Giter8 usage, validation DSLs, and runtime enforcement.
- This topic appeals to both Scala infrastructure builders and applied functional programming communities.
- The content also promotes Scala ecosystem tools (ZIO, Cats Effect, etc.) with real-world impact.
