# FlowForge Lineage Demo

This directory contains a Docker Compose setup for running Marquez (OpenLineage) locally to demo FlowForge's lineage integration capabilities.

## Quick Start

### 1. Start the Stack
```bash
cd docker/lineage
docker-compose up -d
```

This boots:
- **Postgres** (port 5432): Backend database for Marquez
- **Marquez API** (port 5000): OpenLineage collection API  
- **Marquez Web UI** (port 3000): Lineage visualization

### 2. Verify Services
```bash
# Check all services are running
docker-compose ps

# Check Marquez API health
curl http://localhost:5000/api/v1/health

# Check Marquez Web UI
open http://localhost:3000
```

### 3. Run FlowForge Pipeline with Lineage
```bash
# From FlowForge root directory
sbt "examples/runMain com.flowforge.examples.LineageDemo"
```

This will:
- Execute a sample FlowForge pipeline
- Emit OpenLineage events to Marquez
- Show job/run/dataset lineage in the Web UI

### 4. View Lineage Graph
1. Open http://localhost:3000 in your browser
2. Navigate to the "flowforge" namespace
3. View the job execution graph and data lineage

## What You'll See

The demo shows FlowForge's **first-class lineage integration**:

✅ **Job Tracking**: Each FlowForge pipeline becomes a tracked job  
✅ **Run Tracking**: Every execution creates a unique run with timestamps  
✅ **Dataset Lineage**: Input/output datasets tracked with full provenance  
✅ **Real-time Updates**: Lineage updates as pipelines execute  

## Configuration

### Custom Marquez URL
```scala
// In your FlowForge pipeline
implicit val client: Client[IO] = ...
val emitter = OpenLineageEmitter.create("http://your-marquez:5000")
```

### Custom Namespace
```scala
// Group your jobs by project/team
emitter.emitJobStart(jobName, runId, namespace = "data-platform")
```

## Cleanup

```bash
# Stop and remove containers
docker-compose down

# Remove volumes (WARNING: deletes all lineage data)
docker-compose down -v
```

## Integration

FlowForge pipelines automatically emit lineage events when:
- Pipeline execution starts (`START` event)
- Pipeline completes successfully (`COMPLETE` event with datasets)
- Pipeline fails (`FAIL` event)

No additional configuration needed - lineage is **built-in by default**!

## Troubleshooting

### Services not starting
```bash
# Check logs
docker-compose logs marquez
docker-compose logs postgres

# Restart services
docker-compose restart
```

### API not responding
```bash
# Check Marquez health
curl -v http://localhost:5000/api/v1/health

# Check network connectivity
docker-compose exec marquez curl postgres:5432
```

### Web UI blank
- Wait 30-60 seconds for services to fully initialize
- Check that Marquez API is responding on port 5000
- Try refreshing the browser

---

*FlowForge Lineage Demo | OpenLineage + Marquez | First-class data lineage*