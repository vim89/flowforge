package com.flowforge.core.instances

import com.flowforge.core.algebra._
import com.flowforge.core.types._
import com.flowforge.core.types.DataFormat
import io.circe.{ parser, Json }

/**
 * Practical default encoders/decoders for common shapes used in foundation flows.
 *   - Map[String,String]
 *   - io.circe.Json
 *   - String
 */
object DefaultCodecs {
  implicit val intEncoder: DataEncoder[Int] = new DataEncoder[Int] {
    def encode(data: Int, format: DataFormat) =
      Right(EncodedData(data.toString.getBytes("UTF-8"), format))
    def schema(format: DataFormat): DataSchema =
      DataSchema.builder.addField("value", DataType.Integer).build
    def estimateSize(data: Int, format: DataFormat): Long = 8L
    def supportsFormat(format: DataFormat): Boolean          =
      format match {
        case DataFormat.JSON | DataFormat.JSONL | DataFormat.CSV => true
        case _                                                   => false
      }
    def optimizationHints(data: Int, format: DataFormat): EncodingHints = EncodingHints.default
  }

  implicit val stringEncoder: DataEncoder[String] = new DataEncoder[String] {
    def encode(data: String, format: DataFormat) =
      Right(EncodedData(data.getBytes("UTF-8"), format))
    def schema(format: DataFormat): DataSchema =
      DataSchema.builder.addField("value", DataType.String).build
    def estimateSize(data: String, format: DataFormat): Long = data.getBytes("UTF-8").length
    def supportsFormat(format: DataFormat): Boolean          = true
    def optimizationHints(data: String, format: DataFormat): EncodingHints = EncodingHints.default
  }

  implicit val stringDecoder: DataDecoder[String] = new DataDecoder[String] {
    def decode(encodedData: EncodedData, format: DataFormat) =
      Right(new String(encodedData.data, "UTF-8"))
    def validateSchema(encodedData: EncodedData, expectedSchema: DataSchema) = Right(())
    def decodeWithEvolution(
      encodedData: EncodedData,
      format: DataFormat,
      targetSchema: DataSchema
    ) =
      Right(new String(encodedData.data, "UTF-8"))

    /**
     * Check if this decoder can handle the given format.
     *
     * @param format
     *   Data format to check
     * @return
     *   True if format is supported
     */
    override def supportsFormat(format: DataFormat): Boolean =
      format match {
        case DataFormat.JSON | DataFormat.JSONL | DataFormat.CSV => true
        case _                                                   => false
      }
  }

  implicit val jsonEncoder: DataEncoder[Json] = new DataEncoder[Json] {
    def encode(data: Json, format: DataFormat) = Right(
      EncodedData(data.noSpaces.getBytes("UTF-8"), format)
    )
    def schema(format: DataFormat): DataSchema =
      DataSchema.builder.addField("json", DataType.String).build
    def estimateSize(data: Json, format: DataFormat): Long               = data.noSpaces.length
    def supportsFormat(format: DataFormat): Boolean                      = true
    def optimizationHints(data: Json, format: DataFormat): EncodingHints = EncodingHints.default
  }

  implicit val jsonDecoder: DataDecoder[Json] = new DataDecoder[Json] {
    def decode(encodedData: EncodedData, format: DataFormat) =
      parser
        .parse(new String(encodedData.data, "UTF-8"))
        .left
        .map(err => CorruptedData(err.getMessage))
    def validateSchema(encodedData: EncodedData, expectedSchema: DataSchema) = Right(())
    def decodeWithEvolution(
      encodedData: EncodedData,
      format: DataFormat,
      targetSchema: DataSchema
    ) =
      decode(encodedData, format)

    /**
     * Check if this decoder can handle the given format.
     *
     * @param format
     *   Data format to check
     * @return
     *   True if format is supported
     */
    override def supportsFormat(format: DataFormat): Boolean =
      format match {
        case DataFormat.JSON | DataFormat.JSONL => true
        case _                                  => false
      }
  }

  implicit val mapStringStringEncoder: DataEncoder[Map[String, String]] =
    new DataEncoder[Map[String, String]] {
      def encode(data: Map[String, String], format: DataFormat) = format match {
        case DataFormat.JSON | DataFormat.JSONL =>
          Right(
            EncodedData(
              Json
                .obj(data.toSeq.map { case (k, v) => (k, Json.fromString(v)) }: _*)
                .noSpaces
                .getBytes("UTF-8"),
              format
            )
          )
        case DataFormat.CSV =>
          Right(EncodedData(data.values.mkString(",").getBytes("UTF-8"), format))
        case other => Left(UnsupportedFormat(other, "Map[String,String]"))
      }
      def schema(format: DataFormat): DataSchema =
        DataSchema.builder.addField("row", DataType.Map(DataType.String, DataType.String)).build
      def estimateSize(data: Map[String, String], format: DataFormat): Long = data.size * 16L
      def supportsFormat(format: DataFormat): Boolean =
        format == DataFormat.JSON || format == DataFormat.JSONL || format == DataFormat.CSV
      def optimizationHints(data: Map[String, String], format: DataFormat): EncodingHints =
        EncodingHints.default
    }

  implicit val mapStringStringDecoder: DataDecoder[Map[String, String]] =
    new DataDecoder[Map[String, String]] {
      def decode(encodedData: EncodedData, format: DataFormat) = format match {
        case DataFormat.JSON | DataFormat.JSONL =>
          parser
            .parse(new String(encodedData.data, "UTF-8"))
            .left
            .map(err => CorruptedData(err.message))
            .flatMap { j =>
              j.asObject
                .map(_.toMap.view.mapValues(_.asString.getOrElse("")).toMap)
                .toRight(CorruptedData("Expected JSON object"))
            }
        case DataFormat.CSV =>
          val parts = new String(encodedData.data, "UTF-8").split(",", -1).toList
          Right(parts.zipWithIndex.map { case (v, i) => s"col_$i" -> v }.toMap)
        case other => Left(CorruptedData(s"Unsupported format: $other"))
      }
      def validateSchema(encodedData: EncodedData, expectedSchema: DataSchema) = Right(())
      def decodeWithEvolution(
        encodedData: EncodedData,
        format: DataFormat,
        targetSchema: DataSchema
      ) =
        decode(encodedData, format)

      /**
       * Check if this decoder can handle the given format.
       *
       * @param format
       *   Data format to check
       * @return
       *   True if format is supported
       */
      override def supportsFormat(format: DataFormat): Boolean =
        format match {
          case DataFormat.JSON | DataFormat.JSONL | DataFormat.CSV => true
          case _                                                   => false
        }
    }
}
