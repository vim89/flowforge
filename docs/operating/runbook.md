# FlowForge Production Operations Runbook

This runbook provides operational procedures for running FlowForge pipelines in production environments.

## Service Level Objectives (SLOs)

### Pipeline Execution SLOs
- **Availability**: 99.9% pipeline execution success rate
- **Latency**: P99 pipeline completion < 5 minutes for standard workloads
- **Data Quality**: 99.95% contract compliance (compile-time + runtime)
- **Lineage**: 100% pipeline runs emit OpenLineage events

### Infrastructure SLOs  
- **Compute**: Auto-scaling within 2 minutes of demand
- **Storage**: 99.99% availability for Delta/Iceberg tables
- **Monitoring**: Alert delivery < 30 seconds for critical failures

## Monitoring and Alerting

### Key Metrics to Track

#### Pipeline Health
```yaml
# Contract Violations (Critical)
flowforge.contract.violations.total
  - Alert: Immediate (PagerDuty)
  - Threshold: > 0 violations in 5 minutes

# Pipeline Success Rate  
flowforge.pipeline.success.rate
  - Alert: Warning at < 95%, Critical at < 90%
  - Window: 15 minute rolling average

# Data Quality Failures
flowforge.dq.failure.rate  
  - Alert: Warning at > 1%, Critical at > 5%
  - Window: Per pipeline execution
```

#### Resource Utilization
```yaml
# Spark Cluster Utilization
spark.cluster.cpu.utilization
  - Alert: Warning at > 80%, Critical at > 95% 
  - Action: Trigger auto-scaling

# Memory Pressure
spark.executor.memory.utilization
  - Alert: Warning at > 85%
  - Action: Review partition strategy

# Flink Cluster Health  
flink.taskmanager.availability
  - Alert: Critical if < 90% available
  - Action: Investigate failed TaskManagers
```

#### Data Lineage
```yaml
# Lineage Event Delivery
openlineage.events.delivery.success.rate
  - Alert: Warning at < 99%
  - Action: Check Marquez connectivity

# Schema Evolution Events
flowforge.schema.evolution.events
  - Alert: Info level for tracking
  - Action: Review for breaking changes
```

### Dashboard Configuration

#### Primary Pipeline Dashboard
- **Contract Compliance**: Real-time contract violation count
- **Pipeline Throughput**: Records processed per minute
- **Execution Times**: P50, P95, P99 pipeline durations  
- **Error Rates**: By pipeline, by stage, by error type
- **Resource Usage**: CPU, memory, disk utilization

#### Data Quality Dashboard
- **DQ Check Results**: Deequ constraint pass/fail rates
- **Schema Drift Detection**: Contract evolution events
- **Data Freshness**: Last successful pipeline runs by source
- **Anomaly Detection**: Statistical outliers in data metrics

#### Lineage Dashboard
- **Graph Visualization**: Pipeline dependencies via Marquez
- **Impact Analysis**: Downstream effects of schema changes
- **Data Flow Health**: End-to-end pipeline success chains
- **Audit Trail**: Schema changes and approval workflow

## Incident Response Procedures

### Contract Violation (P0)
**Symptoms**: Pipeline build failures, contract drift alerts

**Immediate Actions**:
1. Check CI/CD pipeline status - builds may be failing
2. Review recent schema changes in affected data sources
3. Identify if this is planned evolution or unexpected drift
4. Block further deployments until resolution

**Investigation**:
```bash
# Check recent contract changes
git log --oneline --grep="contract" --since="1 day ago"

# Validate current schema against contracts
sbt "ff check"

# Review pipeline build logs
kubectl logs -l app=flowforge-ci --tail=100
```

**Resolution**:
- **Planned Evolution**: Update contracts to match new schema
- **Unexpected Drift**: Coordinate with data producers to revert or plan migration
- **Bug in Contract**: Fix contract definition and validate against test data

### Pipeline Execution Failure (P1)
**Symptoms**: High pipeline failure rate, timeout alerts

**Immediate Actions**:
1. Check cluster health and resource availability  
2. Review recent deployments for regression introduction
3. Examine pipeline execution logs for error patterns
4. Activate backup data processing if available

**Investigation**:
```bash
# Check Spark cluster status
kubectl get pods -l app=spark-executor
kubectl describe pod <failing-executor-pod>

# Review pipeline execution metrics
kubectl logs -l app=flowforge-pipeline --tail=1000 | grep ERROR

# Check data source availability
curl -f <data-source-health-endpoint>
```

**Resolution**:
- **Resource Constraint**: Scale cluster or optimize pipeline
- **Data Source Issue**: Coordinate with upstream teams
- **Code Regression**: Rollback to previous version
- **Configuration Error**: Update pipeline configuration

### Data Quality Degradation (P1)
**Symptoms**: Increasing DQ check failures, data anomalies

**Immediate Actions**:
1. Review DQ check failure details and affected datasets
2. Assess downstream impact - which consumers are affected
3. Determine if data should be quarantined or processing paused
4. Notify downstream consumers of potential data quality issues

**Investigation**:
```bash
# Review DQ check results
sbt "quality-deequ/run" 

# Check for schema changes in source data
SELECT schema_version, change_date FROM data_catalog.schema_changes 
WHERE table_name = 'affected_table' 
ORDER BY change_date DESC LIMIT 10;

# Analyze data distribution changes
spark-sql --master local[*] -f queries/data_distribution_analysis.sql
```

## Deployment Procedures

### Safe Deployment Checklist

#### Pre-Deployment
- [ ] All compile-fail tests passing (`sbt "ff check"`)
- [ ] Contract compatibility validated against staging data
- [ ] Performance testing completed with representative data volumes
- [ ] Rollback plan prepared and tested
- [ ] Downstream consumers notified of schema changes

#### Deployment Steps
```bash
# 1. Deploy to staging environment
kubectl apply -f k8s/staging/

# 2. Run smoke tests against staging
sbt "ff runSpark" # Validate pipeline execution

# 3. Run integration tests  
pytest tests/integration/

# 4. Deploy to production (blue-green)
kubectl apply -f k8s/production/

# 5. Monitor deployment metrics
watch kubectl get pods -l app=flowforge-pipeline

# 6. Validate lineage events
curl http://marquez:3000/api/v1/jobs/flowforge-pipeline
```

#### Post-Deployment Validation  
- [ ] Contract compliance metrics normal
- [ ] Pipeline execution success rate > 95%
- [ ] Data quality checks passing
- [ ] Lineage events appearing in Marquez
- [ ] No increase in error rates or latencies

## Backup and Recovery

### Data Recovery Procedures

#### Contract Definition Backup
- **Location**: Git repository with tagged releases
- **Recovery**: Checkout specific contract version, redeploy pipelines
- **RTO**: < 30 minutes for contract rollback

#### Pipeline State Recovery
- **Spark Checkpoints**: Stored in distributed file system
- **Flink Savepoints**: Automatic savepoints every 10 minutes
- **Recovery**: Restart from last successful checkpoint

#### Data Recovery
- **Delta Lake Time Travel**: Query previous table versions
- **Iceberg Snapshots**: Rollback to previous data snapshot
- **Source Data Replay**: Reprocess from source systems if needed

### Disaster Recovery

#### Multi-Region Setup
- **Active-Passive**: Primary region with warm standby
- **Data Replication**: Cross-region Delta/Iceberg table replication  
- **Pipeline Failover**: Automated pipeline restart in DR region
- **RTO**: < 1 hour, RPO: < 15 minutes

## Performance Optimization

### Pipeline Optimization

#### Spark Optimization Checklist
- **Partitioning**: Align with query patterns, avoid small files
- **Caching**: Cache frequently accessed intermediate results
- **Broadcast Variables**: For small lookup tables
- **Dynamic Allocation**: Enable for variable workloads

#### Flink Optimization  
- **Parallelism**: Set based on available cores and data skew
- **Checkpointing**: Balance frequency with performance impact
- **Memory Configuration**: Tune TaskManager heap and off-heap
- **Watermark Strategy**: Optimize for late data handling

### Resource Right-Sizing

#### Compute Resources
```yaml
# Spark Configuration
spark.executor.cores: 4
spark.executor.memory: 8g  
spark.sql.adaptive.coalescePartitions.enabled: true
spark.sql.adaptive.skewJoin.enabled: true

# Flink Configuration  
taskmanager.memory.process.size: 4gb
parallelism.default: 8
execution.checkpointing.interval: 60s
```

#### Storage Optimization
- **Compaction**: Schedule regular file compaction for Delta tables
- **Vacuum**: Remove old file versions based on retention policy
- **Z-Ordering**: Optimize file layout for query patterns

## Troubleshooting Guide

### Common Issues and Solutions

#### "Contract drift detected"
**Cause**: Source data schema changed without contract update
**Solution**: Update contract definition or coordinate schema revert
**Prevention**: Implement schema change approval workflow

#### "Pipeline execution timeout"  
**Cause**: Insufficient resources or data volume spike
**Solution**: Scale cluster or optimize query plan
**Prevention**: Monitor data volume trends, implement auto-scaling

#### "Data quality checks failing"
**Cause**: Data source quality degradation or check configuration
**Solution**: Review DQ check definitions, coordinate with data producers
**Prevention**: Implement upstream data quality monitoring

#### "Lineage events missing"
**Cause**: OpenLineage emitter configuration or network connectivity
**Solution**: Check Marquez connectivity, review emitter configuration
**Prevention**: Monitor lineage event delivery rates

### Debug Commands

```bash
# Pipeline execution debugging
sbt "ff check"                    # Validate contracts
sbt "engines-spark/run"           # Run Spark pipeline locally
kubectl logs -l app=flowforge     # Check production logs

# Contract debugging  
sbt "compile-fail-tests/test"     # Validate contract enforcement
git diff HEAD~1 -- "**/*.scala"   # Review recent contract changes

# Performance debugging
spark-submit --help              # Review Spark configuration options
flink run --help               # Review Flink job parameters
```

## Security Procedures

### Access Control
- **Pipeline Code**: Git repository access control via GitHub teams
- **Production Data**: Row-level security via Delta/Iceberg integration  
- **Cluster Access**: Kubernetes RBAC with least-privilege principles
- **Secrets Management**: Vault integration for database credentials

### Audit Logging
- **Schema Changes**: All contract modifications logged via Git
- **Pipeline Executions**: Full execution metadata in OpenLineage
- **Access Patterns**: Query logs retained for compliance requirements
- **Security Events**: Failed authentications and authorization attempts

---

*This runbook should be updated quarterly and after major incidents to incorporate lessons learned.*