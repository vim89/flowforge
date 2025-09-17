# Marquez / OpenLineage (Local)

Run the lineage backend locally in 60 seconds:

```bash
docker compose -f ops/marquez/docker-compose.yml up -d
open http://localhost:3000
```

Environment variables for emitters:

- `OPENLINEAGE_URL` (default: `http://localhost:5000/api/v1/lineage`)
- `OPENLINEAGE_NAMESPACE` (default: `flowforge`)
- `OPENLINEAGE_RUN_ID` (optional; overrides generated run id)

In code, use `OpenLineageEmitter.http[F]` or `OpenLineageEmitter.asyncHttp[F]` and pass to `PipelineBuilder.withLineageEmitter`.

