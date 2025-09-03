# HDFS Connector (Hadoop) – Setup & Usage

Status: Experimental. Requires Hadoop client jars at runtime.

## Dependencies

The `connectors` module now pulls:
- org.apache.hadoop:hadoop-client-api:3.3.6
- org.apache.hadoop:hadoop-client-runtime:3.3.6
- org.apache.hadoop:hadoop-hdfs-client:3.3.6 (slf4j/log4j exclusions applied)

These are sufficient for basic HDFS FileSystem operations.

## Runtime Configuration

Provide Hadoop configuration via either:
- classpath core-site.xml and hdfs-site.xml, or
- programmatic overrides in `HDFSFileSystemConnector` configuration map, e.g.:
  - fs.defaultFS = hdfs://namenode-host:8020
  - dfs.client.use.datanode.hostname = true

Authentication:
- Non-kerberos clusters: ensure the process user is allowed.
- Kerberos: configure JAAS/Krb5 and set `hadoop.security.authentication=kerberos`.

## Usage

```scala
val hdfs = new HDFSFileSystemConnector[IO](
  hdfsUrl = "hdfs://namenode:8020",
  configuration = Map("dfs.client.use.datanode.hostname" -> "true")
)

val bytesF = hdfs.read(DataSource.gcs("hdfs", "/tmp/data.csv", DataFormat.CSV))
```

Note: DataSource/DataSink path fields should contain the HDFS path (e.g. `/path/to/file`).

## Known Issues / Next Steps

- Shading/logging: review transitive SLF4J/log4j bindings when integrating with other modules.
- Streaming reads: prefer FSDataInputStream with buffered chunking for large files.
- Retries: wrap IO in retry/circuit breaker for robustness.
- Tests: Integration tests can run against MiniDFSCluster (future work).

