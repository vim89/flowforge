package com.flowforge.engines.flink

import com.flowforge.core.algebra.{ DataDecoder, DataEncoder, EncodedData }
import com.flowforge.core.types.RefinedTypes.{ FieldName, SchemaVersion }
import com.flowforge.core.types.{ DataFormat, DataSchema, DataType, StructField }
import io.circe.Json

import java.nio.charset.StandardCharsets
import java.time.Instant

/**
 * Everything this engine does to a single JSON row, with no environment and no job involved.
 *
 * These are all calculations, so they can be tested without starting Flink, and the Flink function classes in
 * [[FlinkStreamOps]] can call them from inside a task. They are reached through this object's static instance
 * rather than captured, which is what keeps those function classes serializable.
 */
private[flink] object FlinkRows {

  /** Decode one JSON row. `None` when the decoder rejects it. */
  def decode[A](json: String, decoder: DataDecoder[A]): Option[A] =
    decoder
      .decode(EncodedData(json.getBytes(StandardCharsets.UTF_8), DataFormat.JSON), DataFormat.JSON)
      .toOption

  /** Encode one record in `format`. `None` when the encoder rejects it. */
  def encode[A](
    value: A,
    encoder: DataEncoder[A],
    format: DataFormat,
  ): Option[String] =
    encoder.encode(value, format).toOption.map(ed => new String(ed.data, StandardCharsets.UTF_8))

  /**
   * A CSV file's header: the line as the file holds it, and the column names read off it.
   *
   * Both are needed and neither is derivable from the other. The names are what a row's cells are named
   * under, and they are trimmed, so rebuilding a line from them does not give back the line - a header
   * written `id, name` rebuilds as `id,name`. The line is what [[csvLineToJson]] compares against to leave
   * the header out of the records.
   */
  final case class CsvHeader(line: String, names: List[String])

  /** Read the column names off a file's header line. */
  def header(line: String): CsvHeader =
    CsvHeader(line, line.trim.split(",", -1).toList.map(_.trim))

  /**
   * Read one CSV line as a JSON object, under the column names the file's header line gave.
   *
   * `None` for a blank line and for the header line itself, because the source hands every line of the file
   * to the same function and the header is not a record. The comparison is against the header line as the
   * file holds it, so a header the reader had to trim is still recognised as the header.
   *
   * A record whose text is the header line is dropped with it. A line-oriented source reports no line number,
   * so there is nothing to tell the two apart by; the alternative is reading the header as a record, which is
   * worse for every file that has one.
   *
   * A line with fewer cells than the header leaves the remaining fields null. Extra cells are dropped: the
   * header is what names a column, so a cell with no name has nowhere to go.
   */
  def csvLineToJson(header: CsvHeader, line: String): Option[String] = {
    val trimmed = line.trim
    if (trimmed.isEmpty || trimmed == header.line.trim) None
    else {
      val cells = trimmed.split(",", -1).toList
      val fields =
        header.names.zipAll(cells, "", "").collect { case (name, cell) if name.nonEmpty => name -> cell }
      Some(Json.obj(fields.map { case (name, cell) => name -> cellToJson(cell) }: _*).noSpaces)
    }
  }

  /**
   * A CSV cell carries no type, so it is read the way a CSV reader with schema inference reads it.
   *
   * This matters for decoding rather than for display: a decoder for a record with an `Int` field rejects
   * `{"id":"1"}` and accepts `{"id":1}`, so quoting every cell would make every typed record undecodable.
   */
  private def cellToJson(cell: String): Json = {
    val value = cell.trim
    if (value.isEmpty) Json.Null
    else if (value == "true") Json.True
    else if (value == "false") Json.False
    else
      value.toLongOption
        .map(Json.fromLong)
        .orElse(value.toDoubleOption.flatMap(Json.fromDouble))
        .getOrElse(Json.fromString(cell))
  }

  /**
   * The schema of a dataset, read off the rows it starts with.
   *
   * A stream of JSON rows carries no schema of its own, so the only thing that can describe one is the rows
   * themselves. The sample is what the caller already paid for, so this costs no extra job. A field absent
   * from every sampled row is absent from the schema, which is the same limit any schema-on-read inference
   * has.
   */
  def schemaOf(rows: List[String]): DataSchema = {
    val fields = rows.headOption
      .flatMap(row => io.circe.parser.parse(row).toOption)
      .flatMap(_.asObject)
      .toList
      .flatMap(_.toList)
      .map {
        case (name, value) =>
          StructField(FieldName.unsafeFrom(name), typeOf(value), nullable = value.isNull)
      }
    DataSchema(
      fields = fields,
      version = SchemaVersion.unsafeFrom(1),
      metadata = Map("inferred_from" -> "flink_json_sample"),
      createdAt = Instant.now(),
    )
  }

  /** The FlowForge type of one JSON value. A null carries no type, so it is reported as a string. */
  private def typeOf(value: Json): DataType =
    value.fold(
      jsonNull = DataType.String,
      jsonBoolean = _ => DataType.Boolean,
      jsonNumber = number => if (number.toLong.isDefined) DataType.Long else DataType.Double,
      jsonString = _ => DataType.String,
      jsonArray = elements => DataType.Array(elements.headOption.fold[DataType](DataType.String)(typeOf)),
      jsonObject = obj =>
        DataType.Struct(
          obj.toList.map {
            case (name, field) =>
              StructField(FieldName.unsafeFrom(name), typeOf(field), nullable = field.isNull)
          },
        ),
    )
}
