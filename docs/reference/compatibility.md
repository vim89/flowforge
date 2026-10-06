# Compatibility matrix

The following versions are validated in CI on `main` today. v1.0.0 is not released; readiness for it is
tracked in [v1.0 readiness](../plan/v1.0-readiness.md).

- Scala: 2.13.x (default)
- JDK: 17+
- Spark (engines-spark): 3.5.x (nightly matrix), basic checks on 3.4.x
- Delta: aligned with Spark 3.5.x release line
- Flink (engines-flink): 1.18.x, Scala 2.12 only. The Flink Scala API ships for 2.12, so `build.sbt` pins
  this module to 2.12 and it is not part of the 2.13 build.

Future versions will be added to the nightly matrix. See `.github/workflows/nightly.yml`.

