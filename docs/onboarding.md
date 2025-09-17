# FlowForge Onboarding Guide

Welcome to FlowForge! This guide will help you get productive quickly with our functional data engineering platform.

## Prerequisites

- Scala 2.13+ knowledge
- Familiarity with functional programming concepts (Option, Either, IO)
- Basic understanding of data pipelines
- SBT build tool experience

## Learning Path

### Day 1: Fundamentals
1. **Setup Development Environment**
   ```bash
   # Install required tools
   sdk install sbt
   sdk install scala 2.13.12
   
   # Generate your first project  
   sbt new vim89/flowforge.g8
   ```

2. **Core Concepts**
   - Type-safe pipeline composition
   - Effect systems (Cats Effect/ZIO)
   - Data contracts and validation
   - Resource management

3. **First Pipeline**
   ```scala
   // Start with this simple example
   val pipeline = pipeline[IO, String]("hello-world", csvSource)
     .map(_.trim.toUpperCase)
     .filter(_.nonEmpty) 
     .to(consoleSink)
   ```

### Day 2: Contracts & Validation
1. **Data Contracts DSL**
   ```scala
   val userContract = Contract("user")
     .field("id").required.long.positive
     .field("email").required.string.email
     .withOwner("DataTeam")
     .build
   ```

2. **Validation Patterns**
   - Schema validation
   - Business rule enforcement
   - Data quality checks

3. **Error Handling**
   - ValidatedNel for accumulating errors
   - PipelineError ADT hierarchy
   - Recovery strategies

### Day 3: Advanced Patterns
1. **Multi-Stage Pipelines**
2. **Parallel Processing**
3. **Resource Management**
4. **Monitoring & Lineage**

### Day 4: Production Deployment
1. **Configuration Management**
2. **Testing Strategies**
3. **Monitoring Setup**
4. **CI/CD Integration**

## Common Patterns

### ETL Pipeline
```scala
val etlPipeline = pipeline[IO, RawData]("etl-pipeline", source)
  .transform(extract)
  .transform(transform) 
  .validate(businessRules)
  .to(warehouse)
```

### Stream Processing
```scala
val streamPipeline = pipeline[IO, Event]("stream-pipeline", kafkaSource)
  .map(enrichWithContext)
  .filter(isRelevant)
  .to(eventStore)
```

### Data Quality
```scala
val qualityPipeline = pipeline[IO, Dataset]("quality-pipeline", source)
  .withQualityCheck(schemaValidation)
  .withQualityCheck(businessRules)
  .withQualityCheck(dataFreshness)
  .to(cleanDataSink)
```

## Development Workflow

1. **Define Contracts**: Start with data contracts
2. **Build Pipeline**: Use type-safe builders
3. **Add Validation**: Implement quality checks
4. **Test Locally**: Use test fixtures
5. **Deploy**: Follow production checklist

## Resources

- [API Documentation](api/index.html)
- [Example Projects](modules/examples/)
- [Troubleshooting Guide](troubleshooting.md)
- [Contributing Guidelines](CONTRIBUTING.md)

## Support

- Slack: #flowforge-support
- Issues: GitHub Issues
- Email: flowforge-team@company.com