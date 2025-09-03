package com.flowforge.core.instances

import com.flowforge.core.types._
import io.circe.Json

/**
 * Utilities to infer simple DataSchema from runtime values or JSON. Best-effort, not a full schema
 * registry replacement.
 */
object SchemaUtils {

  def inferFromJson(json: Json, name: String = "inferred"): DataSchema = {
    val builder = DataSchema.builder
    json.asObject match {
      case Some(obj) =>
        obj.toMap.foreach { case (k, v) => builder.addField(k, jsonToType(v)) }
        builder.withMetadata("name", name)
      case None => builder.addField("value", jsonToType(json)).withMetadata("name", name)
    }
    builder.build
  }

  def inferFromProduct(
    a: Product,
    fieldNames: Iterable[String],
    name: String = "inferred"
  ): DataSchema = {
    val builder = DataSchema.builder.withMetadata("name", name)
    fieldNames.zip(a.productIterator).foreach { case (n, v) =>
      builder.addField(n, scalaToType(v))
    }
    builder.build
  }

  private def jsonToType(j: Json): DataType =
    if (j.isString) DataType.String
    else if (j.isNumber) DataType.Double
    else if (j.isBoolean) DataType.Boolean
    else if (j.isArray) {
      val arr   = j.asArray.get
      val elemT = arr.headOption.map(jsonToType).getOrElse(DataType.String)
      DataType.Array(elemT)
    } else if (j.isObject) {
      val fields = j.asObject.get.toMap.map { case (k, v) =>
        StructField.required(k, jsonToType(v))
      }.toList
      DataType.Struct(fields)
    } else DataType.String

  private def scalaToType(v: Any): DataType = v match {
    case _: String            => DataType.String
    case _: Int               => DataType.Integer
    case _: Long              => DataType.Long
    case _: Double            => DataType.Double
    case _: Float             => DataType.Double
    case _: Boolean           => DataType.Boolean
    case _: java.time.Instant => DataType.Timestamp
    case l: List[_]   => DataType.Array(l.headOption.map(scalaToType).getOrElse(DataType.String))
    case a: Array[_]  => DataType.Array(a.headOption.map(scalaToType).getOrElse(DataType.String))
    case m: Map[_, _] => DataType.Map(DataType.String, DataType.String)
    case p: Product =>
      val names =
        try p.productElementNames.toList
        catch { case _: Throwable => (0 until p.productArity).map(i => s"_${i + 1}").toList }
      val fields = names.zip(p.productIterator.toList).map { case (n, vv) =>
        StructField.required(n, scalaToType(vv))
      }
      DataType.Struct(fields)
    case _ => DataType.String
  }
}
