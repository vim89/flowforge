package com.flowforge.engines.flink

import org.apache.flink.api.common.functions.{ FilterFunction, MapFunction }
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.core.fs.FileSystem
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

/**
 * Minimal Flink runner demo proving "same business logic, different runner".
 *
 * The transforms below are pure functions of a `User`, so the same ones a Spark pipeline applies are the ones
 * this job applies. Only the wrappers that hand a record to Flink are engine-specific.
 *
 * It uses the Java `DataStream` API, like the rest of this module: the Scala API is published for 2.12 only
 * and is deprecated upstream, so depending on it would pin this module to a Scala version `core` does not
 * publish for.
 */
object FlinkStreamingDemo {

  // Domain model (same as used in Spark examples)
  case class User(
    id: Long,
    name: String,
    email: String)
  case class ProcessedUser(
    id: Long,
    name: String,
    email: String,
    processed: Boolean)

  // The Java API infers no types from Scala signatures, so each one is stated.
  private val userTypeInfo: TypeInformation[User] = TypeInformation.of(classOf[User])
  private val processedUserTypeInfo: TypeInformation[ProcessedUser] =
    TypeInformation.of(classOf[ProcessedUser])
  private val stringTypeInfo: TypeInformation[String] = TypeInformation.of(classOf[String])

  /**
   * PURE DOMAIN TRANSFORM - Engine Agnostic
   *
   * This is the same business logic used in Spark pipelines. Notice it has no Flink dependencies - it's a
   * pure function.
   */
  def processUser(user: User): ProcessedUser =
    ProcessedUser(
      id = user.id,
      name = user.name.toUpperCase,
      email = user.email.toLowerCase,
      processed = true,
    )

  /**
   * PURE DOMAIN FILTER - Engine Agnostic
   *
   * Same validation logic across all engines.
   */
  def isValidUser(user: User): Boolean =
    user.id > 0 &&
      user.name.nonEmpty &&
      user.email.contains("@")

  /** The records the demo runs on. A bounded collection, so the job finishes. */
  private val users: List[User] = List(
    User(1, "john doe", "JOHN@EXAMPLE.COM"),
    User(2, "jane smith", "JANE@TEST.ORG"),
    User(0, "", "invalid"), // Invalid user - will be filtered
    User(3, "bob wilson", "BOB@COMPANY.NET"),
  )

  /** Where the demo writes its results. */
  val outputPath: String = "/tmp/flowforge-flink-output.txt"

  @nowarn("cat=deprecation")
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(2)

    val processedStream = env
      .fromCollection(users.asJava, userTypeInfo)
      .filter(ValidUser)                                       // Pure domain filter
      .process(new UserProcessFunction, processedUserTypeInfo) // Flink wrapper around pure transform
      .name("Process Users")

    // `DataStream.print` is a Flink sink, not Scala's `print`: it adds an operator to the job graph that
    // writes each record as the job runs. The linter matches on the name, so the rule is waived here.
    processedStream.print("Processed Users") // scalafix:ok DisableSyntax.noPrintln

    processedStream
      .map(RenderUser, stringTypeInfo)
      .writeAsText(outputPath, FileSystem.WriteMode.OVERWRITE)
      .setParallelism(1)

    env.execute("FlowForge Flink Demo")
    ()
  }

  /** Flink's wrapper around [[isValidUser]]. */
  private object ValidUser extends FilterFunction[User] {
    override def filter(user: User): Boolean = isValidUser(user)
  }

  /** How a result reaches a text sink. The sink writes lines, so a record has to become one. */
  private object RenderUser extends MapFunction[ProcessedUser, String] {
    override def map(user: ProcessedUser): String = user.toString
  }

  /**
   * Flink-specific wrapper around pure domain transform.
   *
   * This is the only part that knows about Flink - the actual business logic (processUser function) is
   * engine-agnostic.
   */
  private class UserProcessFunction extends ProcessFunction[User, ProcessedUser] {
    override def processElement(
      user: User,
      ctx: ProcessFunction[User, ProcessedUser]#Context,
      out: Collector[ProcessedUser],
    ): Unit =
      // Call the pure domain transform (same as used in Spark)
      out.collect(processUser(user))
  }
}
