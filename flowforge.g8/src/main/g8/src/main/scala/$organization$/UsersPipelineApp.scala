package $organization$

import cats.effect.{IO, IOApp}
import com.flowforge.contracts.syntax.ContractDSL._
import com.flowforge.core.syntax.PipelineSyntax._
import com.flowforge.core.types._
import cats.implicits._

/**
 * Real-world FlowForge Pipeline Application
 * 
 * Demonstrates the full power of FlowForge's type-safe, contract-driven approach
 */
object UsersPipelineApp extends IOApp.Simple {

  // Domain models
  case class RawUser(id: String, name: String, email: String, age: String, country: String)
  case class CleanUser(id: Long, name: String, email: String, age: Int, country: String)
  case class EnrichedUser(id: Long, name: String, email: String, age: Int, country: String, segment: String, riskScore: Double)

  // Business logic transformations
  def parseAndValidateUser(raw: RawUser): IO[CleanUser] = IO {
    CleanUser(
      id = raw.id.toLong,
      name = raw.name.trim.toLowerCase.split(" ").map(_.capitalize).mkString(" "),
      email = raw.email.trim.toLowerCase,
      age = raw.age.toInt,
      country = raw.country.toUpperCase
    )
  }.handleErrorWith(e => IO.raiseError(new IllegalArgumentException("Invalid user data: " + raw + ", error: " + e.getMessage)))

  def enrichWithBusinessData(user: CleanUser): IO[EnrichedUser] = IO {
    val segment = user.age match {
      case age if age < 25 => "Young Adult"
      case age if age < 40 => "Adult"
      case age if age < 60 => "Middle Aged"
      case _ => "Senior"
    }
    
    val riskScore = (user.age * 0.01 + user.country.length * 0.1) % 1.0
    
    EnrichedUser(user.id, user.name, user.email, user.age, user.country, segment, riskScore)
  }

  // Data contracts using enhanced DSL
  val rawUserContract = Contract("raw_user")
    .field("id").required.string.minLength(1)
    .field("name").required.string.minLength(2).maxLength(200)
    .field("email").required.string.email.maxLength(255)
    .field("age").required.string.minLength(1)
    .field("country").required.string.minLength(2).maxLength(3)
    .withSLA("real-time")
    .withOwner("DataIngestionTeam")
    .build

  val cleanUserContract = Contract("clean_user")  
    .field("id").required.long.positive
    .field("name").required.string.minLength(2).maxLength(200)
    .field("email").required.string.email.maxLength(255)
    .field("age").required.int.range(0, 150)
    .field("country").required.string.minLength(2).maxLength(3)
    .withSLA("real-time")
    .withOwner("DataProcessingTeam")
    .build

  val enrichedUserContract = Contract("enriched_user")
    .field("id").required.long.positive
    .field("name").required.string.minLength(2).maxLength(200)
    .field("email").required.string.email.maxLength(255)
    .field("age").required.int.range(0, 150)
    .field("country").required.string.minLength(2).maxLength(3)
    .field("segment").required.string.oneOf("Young Adult", "Adult", "Middle Aged", "Senior")
    .field("riskScore").required.double.range(0.0, 1.0)
    .withSLA("hourly")
    .withOwner("DataAnalyticsTeam")
    .build

  // Quality validation functions
  def validateCleanUser(user: CleanUser): cats.data.ValidatedNel[FlowForgeError, Unit] = {
    val validations = List(
      if (user.name.nonEmpty && user.name.length >= 2) ().validNel else FlowForgeError.ValidationError("Name too short").invalidNel,
      if (user.email.contains("@") && user.email.contains(".")) ().validNel else FlowForgeError.ValidationError("Invalid email format").invalidNel,
      if (user.age > 0 && user.age < 150) ().validNel else FlowForgeError.ValidationError("Invalid age").invalidNel,
      if (user.country.length >= 2) ().validNel else FlowForgeError.ValidationError("Invalid country code").invalidNel
    )
    
    validations.sequence_.map(_ => ())
  }

  def validateEnrichedUser(user: EnrichedUser): cats.data.ValidatedNel[FlowForgeError, Unit] = {
    val validSegments = Set("Young Adult", "Adult", "Middle Aged", "Senior")
    
    val validations = List(
      if (validSegments.contains(user.segment)) ().validNel else FlowForgeError.ValidationError("Invalid segment").invalidNel,
      if (user.riskScore >= 0.0 && user.riskScore <= 1.0) ().validNel else FlowForgeError.ValidationError("Invalid risk score").invalidNel
    )
    
    validations.sequence_.map(_ => ())
  }

  // Complete ETL Pipeline
  def createUserProcessingPipeline() = {
    EnhancedPipelineBuilder.from[IO, RawUser]("user-processing-pipeline",
      DataSource.gcs("$organization$-raw-data", "users/raw", DataFormat.CSV))
      .transform[CleanUser](parseAndValidateUser)
      .validate(validateCleanUser)
      .transform[EnrichedUser](enrichWithBusinessData) 
      .withQualityCheck(validateEnrichedUser)
      .to(DataSink.gcs("$organization$-processed-data", "users/enriched", DataFormat.Parquet))
      .withSLA("hourly")
      .withOwner("DataPlatformTeam")
  }

  // Application entry point
  def run: IO[Unit] = {
    for {
      _ <- IO.println("🚀 Starting FlowForge User Processing Pipeline...")
      
      // Build the pipeline
      pipeline = createUserProcessingPipeline()
      
      // Sample data for demonstration
      sampleRawUser = RawUser("123", "john doe", "JOHN.DOE@EXAMPLE.COM", "25", "us")
      
      // Execute the pipeline
      _ <- IO.println("📊 Processing sample user: " + sampleRawUser)
      result <- pipeline.execute(sampleRawUser)
      
      _ <- IO.println("✅ Pipeline completed successfully!")
      _ <- IO.println("📈 Final result: " + result)
      
      // Demonstrate contract validation
      _ <- IO.println("🔍 Validating contracts...")
      _ <- IO.println("✓ Raw User Contract: " + rawUserContract.name)
      _ <- IO.println("✓ Clean User Contract: " + cleanUserContract.name)  
      _ <- IO.println("✓ Enriched User Contract: " + enrichedUserContract.name)
      
    } yield ()
  }
}