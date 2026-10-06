# Compatibility matrix

The following versions are validated in CI on `main` today. v1.0.0 is not released; readiness for it is
tracked in [v1.0 readiness](../plan/v1.0-readiness.md).

- Scala: 2.13.16, on Linux, macOS and Windows. No other 2.13 patch and no other Scala version is in the test
  matrix.
- JDK: 17. Nothing above or below it is tested, and Spark 3.5.x does not support a JDK above 17.
- Spark (engines-spark): 3.5.6, the one version the build pins and the only one CI runs. No other Spark line
  is tested, including 3.4.x.
- Delta: 3.3.2, the version that pairs with Spark 3.5.x. Its tests are opt-in, so CI runs them only when the
  integration job is enabled.
- Flink (engines-flink): not built and not validated. `build.sbt` pins the module to Scala 2.12, because the
  Flink Scala API ships for 2.12, but `core` and `connectors` publish 2.13 and 3 only, so the module cannot
  resolve its own dependencies. No CI job builds it. See [v1.0 readiness](../plan/v1.0-readiness.md).

There is no nightly matrix. The jobs that produce the list above are `test`, `spark-compat` and `spark-it` in
[ci.yml](../../.github/workflows/ci.yml). A version that is not in one of those jobs is not validated, whatever
the build happens to resolve.

