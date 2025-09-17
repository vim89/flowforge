# FlowForge Lineage - OpenLineage "By Default"

## Overview

FlowForge v1.0 provides **lineage "by default"** - automatic OpenLineage event emission without configuration. This enables the core v1.0 promise: **"run → see lineage immediately"** without custom glue.

## How It Works

Every FlowForge pipeline automatically emits OpenLineage events:
- **START**: When pipeline execution begins
- **COMPLETE**: When pipeline execution succeeds  
- **FAIL**: When pipeline execution fails

Events are emitted from `PipelineBuilder.build()` through the integrated `OpenLineageEmitter`.

## Quick Start with Marquez

FlowForge includes a ready-to-use Marquez setup in `ops/marquez/docker-compose.yml`.

### 1. Start Marquez using included docker-compose

```bash
cd ops/marquez
docker-compose up -d
```

This starts:
- **Marquez API** at `http://localhost:5000`
- **Marquez Web UI** at `http://localhost:3000`  
- **PostgreSQL** backend for metadata storage

### 2. Run FlowForge Pipeline

```bash
# Set environment (optional - these are defaults)
export OPENLINEAGE_URL="http://localhost:5000/api/v1/lineage"
export OPENLINEAGE_NAMESPACE="flowforge"

# Run example pipeline
sbt "examples-spark/runMain com.flowforge.examples.spark.UsersPipeline"
```

### 3. View Lineage in Marquez

Open http://localhost:3000 and you'll see:
- Pipeline runs with START/COMPLETE/FAIL events
- Job execution timeline
- Automatic lineage tracking

## Configuration

### Environment Variables

FlowForge uses zero-configuration defaults but supports customization:

| Variable | Default | Description |
|----------|---------|-------------|
| `OPENLINEAGE_URL` | `http://localhost:5000/api/v1/lineage` | OpenLineage endpoint |
| `OPENLINEAGE_NAMESPACE` | `flowforge` | Namespace for events |

### Multiple Environments

```bash
# Development (local Marquez)
export OPENLINEAGE_URL="http://localhost:5000/api/v1/lineage"
export OPENLINEAGE_NAMESPACE="dev"

# Staging 
export OPENLINEAGE_URL="https://marquez-staging.company.com/api/v1/lineage"
export OPENLINEAGE_NAMESPACE="staging"

# Production
export OPENLINEAGE_URL="https://marquez-prod.company.com/api/v1/lineage"  
export OPENLINEAGE_NAMESPACE="prod"
```

## OpenLineage Compatibility

FlowForge emits standard OpenLineage v1.0 events compatible with:

- **Marquez** - Reference implementation
- **DataHub** - LinkedIn's metadata platform  
- **Apache Atlas** - Hadoop ecosystem governance
- **Egeria** - ODPi metadata and governance
- **Any OpenLineage-compatible system**

## Event Structure

### START Event
```json
{
  "eventType": "START",
  "eventTime": "2025-01-15T10:30:00.000Z",
  "run": {
    "runId": "12345678-1234-1234-1234-123456789012"
  },
  "job": {
    "namespace": "flowforge",
    "name": "users-pipeline"
  },
  "inputs": [],
  "outputs": [],
  "producer": "https://github.com/flowforge/flowforge"
}
```

### COMPLETE Event
```json
{
  "eventType": "COMPLETE",
  "eventTime": "2025-01-15T10:35:00.000Z",
  "run": {
    "runId": "12345678-1234-1234-1234-123456789012"  
  },
  "job": {
    "namespace": "flowforge",
    "name": "users-pipeline"
  },
  "inputs": [],
  "outputs": [], 
  "producer": "https://github.com/flowforge/flowforge"
}
```

## Production Deployment

### Docker Compose Production Setup

The included `ops/marquez/docker-compose.yml` is production-ready:

```bash
# Production deployment
cd ops/marquez
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

Features:
- PostgreSQL with persistent volumes
- Redis for caching  
- Nginx reverse proxy
- Health checks and restart policies
- Resource limits

### Kubernetes Deployment

For Kubernetes, use the official Marquez Helm chart:

```bash
helm repo add marquez https://marquez-helm.github.io/marquez/
helm repo update
helm install marquez marquez/marquez
```

Point FlowForge to your Kubernetes Marquez service:
```bash
export OPENLINEAGE_URL="http://marquez.default.svc.cluster.local:5000/api/v1/lineage"
```

## Troubleshooting

### Common Issues

**Lineage events not appearing in Marquez:**
1. Check Marquez is running: `curl http://localhost:5000/api/v1/namespaces`
2. Verify environment variables are set correctly
3. Check FlowForge logs for emission warnings

**Connection errors:**
1. Ensure `OPENLINEAGE_URL` is accessible from FlowForge process
2. Check firewall/network policies
3. Verify Marquez API is responding

**Performance impact:**
- Lineage emission is asynchronous and non-blocking
- Failed emissions log warnings but don't fail pipelines
- Emission uses lightweight HTTP client with minimal overhead

### Debug Mode

Enable verbose lineage logging:
```bash
export OPENLINEAGE_DEBUG="true"
sbt "examples-spark/runMain com.flowforge.examples.spark.UsersPipeline"
```

This shows detailed emission logs without extra dependencies.

## Integration with Other Systems

### Apache Airflow
FlowForge lineage integrates with Airflow through OpenLineage:
```python
from openlineage.airflow import create_airflow_dag
# FlowForge jobs appear as Airflow task dependencies
```

### dbt Integration  
FlowForge and dbt can share lineage through OpenLineage:
```bash
# dbt also emits to same OpenLineage endpoint
dbt run --profiles-dir ./profiles
```

### Data Catalogs
OpenLineage events automatically populate data catalogs:
- **DataHub**: Auto-discovery of FlowForge datasets
- **Apache Atlas**: Integration via OpenLineage connector
- **Amundsen**: Lineage visualization of FlowForge pipelines

## Architecture

### Zero-Config Design
```
FlowForge Pipeline
       ↓
PipelineBuilder.build()
       ↓  
OpenLineageEmitter (automatic)
       ↓
HTTP POST to OpenLineage endpoint
       ↓
Marquez/DataHub/Atlas (lineage storage)
```

### Reliability Features
- **Non-blocking**: Failed lineage emission doesn't fail pipelines
- **Retry logic**: Built-in HTTP retry for transient failures  
- **Graceful degradation**: Pipelines work even if lineage endpoint is down
- **Standard format**: OpenLineage v1.0 compatibility ensures portability

This design delivers the v1.0 promise: **"open Marquez → see lineage light up"** without any configuration or custom integration code.

---

**References:**
- [OpenLineage Specification](https://openlineage.io/spec/)
- [Marquez Documentation](https://marquezproject.github.io/marquez/)
- [DataHub OpenLineage](https://datahubproject.io/docs/metadata-ingestion/source/openlineage/)