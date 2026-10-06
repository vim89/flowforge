# Compatibility matrix

The following versions are validated in CI on `main` today. v1.0.0 is not released; readiness for it is
tracked in [v1.0 readiness](../plan/v1.0-readiness.md).

- Scala: 2.13.x (default)
- JDK: 17+
- Spark (engines-spark): 3.5.x (nightly matrix), basic checks on 3.4.x
- Delta: aligned with Spark 3.5.x release line
- Flink (engines-flink): not built and not validated. `build.sbt` pins the module to Scala 2.12, because the
  Flink Scala API ships for 2.12, but `core` and `connectors` publish 2.13 and 3 only, so the module cannot
  resolve its own dependencies. No CI job builds it. See [v1.0 readiness](../plan/v1.0-readiness.md).

Future versions will be added to the nightly matrix. See `.github/workflows/nightly.yml`.

