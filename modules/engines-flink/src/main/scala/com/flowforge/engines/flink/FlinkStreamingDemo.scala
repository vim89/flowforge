package com.flowforge.engines.flink

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import org.apache.flink.api.common.typeinfo.TypeInformation

/**
 * Minimal Flink runner demo proving "same business logic, different runner".
 *
 * This demo shows that FlowForge's pure domain transforms can be reused across different execution engines
 * (Spark, Flink, etc.) without modification.
 *
 * The business logic (data transformations) remains pure and engine-agnostic, while only the execution
 * framework changes.
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

  // Flink requires explicit TypeInformation for case classes
  implicit val userTypeInfo: TypeInformation[User] = TypeInformation.of(classOf[User])
  implicit val processedUserTypeInfo: TypeInformation[ProcessedUser] =
    TypeInformation.of(classOf[ProcessedUser])
  implicit val stringTypeInfo: TypeInformation[String] = TypeInformation.of(classOf[String])

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

  def main(args: Array[String]): Unit = {
    // Set up Flink execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(2)

    // Option 1: Socket text stream (as specified in plan)
    // Uncomment to read from socket: nc -l 9999
    // val socketStream = env.socketTextStream("localhost", 9999)
    //   .map(line => {
    //     val parts = line.split(",")
    //     if (parts.length >= 3) User(parts(0).toLong, parts(1), parts(2))
    //     else User(0, "", "")
    //   })

    // Option 2: Test data stream for demo
    val users = Seq(
      User(1, "john doe", "JOHN@EXAMPLE.COM"),
      User(2, "jane smith", "JANE@TEST.ORG"),
      User(0, "", "invalid"), // Invalid user - will be filtered
      User(3, "bob wilson", "BOB@COMPANY.NET"),
    )

    val dataStream = env.fromCollection(users)

    // Apply the same business logic as Spark pipelines
    val processedStream = dataStream
      .filter(isValidUser)              // Pure domain filter
      .process(new UserProcessFunction) // Flink wrapper around pure transform
      .name("Process Users")

    // Output results - both console and file sink (as specified in plan)
    processedStream.print("Processed Users")

    // File sink as required by plan
    processedStream
      .map((user: ProcessedUser) => user.toString)
      .writeAsText("/tmp/flowforge-flink-output.txt")
      .setParallelism(1)

    println("""
=== FlowForge Flink Demo ===

This demo proves FlowForge's engine abstraction:
- Same pure domain transforms (processUser, isValidUser)
- Different execution engine (Flink vs Spark)
- Business logic remains unchanged
- Only the engine-specific wrappers change

Expected Output:
- Processed User: ProcessedUser(1,JOHN DOE,john@example.com,true)
- Processed User: ProcessedUser(2,JANE SMITH,jane@test.org,true) 
- Processed User: ProcessedUser(3,BOB WILSON,bob@company.net,true)
- Invalid user (id=0) filtered out

Run this with: sbt "engines-flink/run"
    """)

    // Execute the Flink job
    env.execute("FlowForge Flink Demo")
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
    ): Unit = {
      // Call the pure domain transform (same as used in Spark)
      val processed = processUser(user)
      out.collect(processed)
    }
  }
}
