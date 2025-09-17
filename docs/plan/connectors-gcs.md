# PLAN — Connectors: GCS

## Scope
- Add minimal Google Cloud Storage connector with effect safety and tests.
- Document configuration and usage.

## Configuration
- Authentication: uses Application Default Credentials (ADC).
  - `gcloud auth application-default login` locally, or workload identity on GKE.
  - Service account on CI with `GOOGLE_APPLICATION_CREDENTIALS` JSON key file.
- URIs: `gs://<bucket>/<prefix-or-key>`.
- Formats: connector does not parse format semantics; callers infer by path suffix (e.g., `.json`, `.parquet`).

## Usage (code)
```scala
import cats.effect.IO
import com.flowforge.core.algebra.EffectSystem
import com.flowforge.core.instances.EffectInstances._
import com.flowforge.core.types._
import com.flowforge.connectors.gcs.GcsFileSystemConnector

implicit val F: EffectSystem[IO] = catsEffectSystemInstance
val gcs = GcsFileSystemConnector.default[IO]

val src  = DataSource.GcsSource(RefinedTypes.BucketName.unsafeFrom("my-bucket"), "path/in.json", DataFormat.JSON)
val sink = DataSink.GcsSink(RefinedTypes.BucketName.unsafeFrom("my-bucket"), "out/path/out.json", DataFormat.JSON)

for {
  bytes <- gcs.read(src)
  _     <- gcs.write(sink, bytes match { case com.flowforge.connectors.ConnectorResult.Success(b) => b; case _ => Array.emptyByteArray })
} yield ()
```

## Tests
- Mocked unit tests (Mockito) validate read/write/list/exists/metadata paths without real GCS.
- Integration tests are out-of-scope; ADC and network required.

## Acceptance
- Module compiles with tests green; documentation added.

