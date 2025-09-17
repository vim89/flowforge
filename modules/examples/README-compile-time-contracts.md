# Compile-Time Contracts: Failing Examples

These tests demonstrate compile-time failures using ScalaTest `assertTypeError` for realistic Spark-style pipelines. See:

- modules/compile-fail-tests/src/test/scala/com/flowforge/compilefail/SparkContractCompileFailSpec.scala
- modules/compile-fail-tests/src/test/scala/com/flowforge/compilefail/CompileTimeContractFailSpec.scala
- modules/compile-fail-tests/src/test/scala/com/flowforge/compilefail/SchemaPolicyModesSpec.scala
- modules/compile-fail-tests/src/test/scala/com/flowforge/compilefail/SchemaPolicyMatrixSpec.scala

Run locally:

- `sbt compile-fail-tests/test`

What to look for:

- Exact policy rejects extra/missing fields and type changes
- Backward policy allows extra fields and optional contract fields
- Forward policy allows fewer fields but not extras
- ExactOrdered/Unordered and ExactByPosition enforce ordering and positional types

