# Compatibility matrix

The following versions are validated in CI for FlowForge v1.0.0:

- Scala: 2.13.x (default)
- JDK: 17+
- Spark (engines-spark): 3.5.x (nightly matrix), basic checks on 3.4.x
- Delta: aligned with Spark 3.5.x release line
- Flink (engines-flink): 1.18.x (Scala 2.12 and 2.13 cross-compile)

Future versions will be added to the nightly matrix. See `.github/workflows/nightly.yml`.

