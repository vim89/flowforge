package com.flowforge.contracts.sdk

import cats.effect.IO
import cats.implicits._
import io.circe.parser._
import java.nio.file.{ Files => JFiles, Path, Paths }
import scala.collection.JavaConverters._

object ContractSdkGenerator {

  final case class ContractMeta(
    domain: String,
    entity: String,
    namespace: String,
    version: String,
    avroSchema: String)

  def generateSdk(contractsRoot: String, outputDir: String): IO[Unit] = for {
    contracts <- discoverContracts(contractsRoot)
    _         <- contracts.traverse(generateScalaFiles(outputDir, _))
    _         <- generateBuildFile(outputDir, contracts)
  } yield ()

  private def discoverContracts(root: String): IO[List[ContractMeta]] = IO {
    val avroDir = Paths.get(root, "avro")
    if (!JFiles.exists(avroDir)) List.empty[ContractMeta]
    else {

      JFiles
        .walk(avroDir)
        .iterator()
        .asScala
        .filter(p => p.toString.endsWith(".avsc"))
        .map { avroFile =>
          val domain   = avroFile.getParent.getFileName.toString
          val fileName = avroFile.getFileName.toString
          val entity   = fileName.split("\\.")(0) // Remove .v1.0.0.avsc
          val version = fileName.split("\\.")(1) + "." + fileName.split("\\.")(2) + "." + fileName
            .split("\\.")(3)
          val schema    = new String(JFiles.readAllBytes(avroFile), "UTF-8")
          val namespace = extractNamespace(schema).getOrElse(s"com.acme.$domain")
          ContractMeta(domain, entity, namespace, version, schema)
        }
        .toList
    }
  }

  private def extractNamespace(avroJson: String): Option[String] =
    parse(avroJson).toOption.flatMap(_.hcursor.downField("namespace").as[String].toOption)

  private def generateScalaFiles(outputDir: String, contract: ContractMeta): IO[Unit] = for {
    caseClass <- generateCaseClass(contract)
    typed     <- generateTypedEndpoints(contract)
    _         <- writeFile(outputDir, contract.domain, s"${contract.entity}.scala", caseClass)
    _         <- writeFile(outputDir, contract.domain, s"${contract.entity}Contracts.scala", typed)
  } yield ()

  private def generateCaseClass(contract: ContractMeta): IO[String] = IO {
    val fields = extractFields(contract.avroSchema)
    s"""package ${contract.namespace}
       |
       |final case class ${contract.entity}(
       |${fields.map(f => s"  ${f.name}: ${f.scalaType}").mkString(",\n")}
       |)
       |
       |object ${contract.entity} {
       |  // Add any companion object methods here
       |}
       |""".stripMargin
  }

  private def generateTypedEndpoints(contract: ContractMeta): IO[String] = IO {
    s"""package ${contract.namespace}
       |
       |import com.flowforge.contracts.TypedSource
       |import com.flowforge.contracts.TypedSink
       |import com.flowforge.core.types.SchemaEvidence.SchemaEq
       |import shapeless.LabelledGeneric
       |
       |object ${contract.entity}Contracts {
       |  type ${contract.entity}Repr = LabelledGeneric.Aux[${contract.entity}, _]
       |  
       |  implicit val ${contract.entity.toLowerCase}Generic: LabelledGeneric.Aux[${contract.entity}, ${contract.entity}Repr] = 
       |    LabelledGeneric[${contract.entity}]
       |  
       |  implicit val ${contract.entity.toLowerCase}SchemaEq: SchemaEq[${contract.entity}, ${contract.entity}Repr] = 
       |    SchemaEq.fromLabelledGeneric
       |  
       |  val ${contract.entity}Source: TypedSource[${contract.entity}Repr] = 
       |    TypedSource("${contract.domain}.${contract.entity.toLowerCase()}")
       |  
       |  val ${contract.entity}Sink: TypedSink[${contract.entity}Repr] = 
       |    TypedSink("${contract.domain}.${contract.entity.toLowerCase()}")
       |}
       |""".stripMargin
  }

  final case class AvroField(
    name: String,
    avroType: String,
    nullable: Boolean) {
    def scalaType: String = {
      val base = avroType match {
        case "string"  => "String"
        case "int"     => "Int"
        case "long"    => "Long"
        case "double"  => "Double"
        case "float"   => "Float"
        case "boolean" => "Boolean"
        case _         => "String" // fallback
      }
      if (nullable) s"Option[$base]" else base
    }
  }

  private def extractFields(avroJson: String): List[AvroField] =
    parse(avroJson).toOption
      .flatMap(_.hcursor.downField("fields").as[List[io.circe.Json]].toOption)
      .getOrElse(List.empty)
      .flatMap { field =>
        for {
          name      <- field.hcursor.downField("name").as[String].toOption
          fieldType <- field.hcursor.downField("type").focus
        } yield {
          val (avroType, nullable) = fieldType.asString match {
            case Some(t) => (t, false)
            case None    =>
              // Handle union types like ["null", "string"]
              fieldType.asArray.map(_.toList) match {
                case Some(List(nullType, actualType)) if nullType.asString.contains("null") =>
                  (actualType.asString.getOrElse("string"), true)
                case _ => ("string", false)
              }
          }
          AvroField(name, avroType, nullable)
        }
      }

  private def generateBuildFile(outputDir: String, contracts: List[ContractMeta]): IO[Unit] = {
    val buildContent = s"""name := "contract-sdk"
                          |version := "1.0.0"
                          |scalaVersion := "2.13.10"
                          |
                          |libraryDependencies ++= Seq(
                          |  "com.flowforge" %% "flowforge-contracts" % "0.1.0",
                          |  "com.flowforge" %% "flowforge-core" % "0.1.0",
                          |  "com.chuusai" %% "shapeless" % "2.3.10"
                          |)
                          |""".stripMargin
    writeFile(outputDir, "", "build.sbt", buildContent)
  }

  private def writeFile(
    baseDir: String,
    subDir: String,
    fileName: String,
    content: String,
  ): IO[Unit] = IO {
    val dir =
      if (subDir.nonEmpty) Paths.get(baseDir, "src", "main", "scala", subDir.replace(".", "/"))
      else Paths.get(baseDir)
    if (!JFiles.exists(dir)) JFiles.createDirectories(dir)
    JFiles.write(dir.resolve(fileName), content.getBytes("UTF-8"))
  }
}
