package com.flowforge.core.codecs

import com.flowforge.core.algebra._
import com.flowforge.core.instances.DefaultCodecs._
import com.flowforge.core.types._
import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CodecsCoverageSpec extends AnyFunSuite with Matchers {
  private val allFormats = List(
    DataFormat.JSON,
    DataFormat.JSONL,
    DataFormat.CSV,
    DataFormat.Parquet,
    DataFormat.Avro,
    DataFormat.ORC,
    DataFormat.Delta,
  )

  test("String encoder/decoder roundtrips for common formats and supportsFormat advertises true") {
    val data = "hello"
    allFormats.foreach { fmt =>
      val enc = stringEncoder.encode(data, fmt)
      enc.isRight shouldBe true
      val dec = stringDecoder.decode(enc.toOption.get, fmt)
      // decoder supports only JSON/JSONL/CSV; for others it returns Left but encode succeeded
      if (fmt == DataFormat.JSON || fmt == DataFormat.JSONL || fmt == DataFormat.CSV)
        dec shouldBe Right("hello")
      stringEncoder.supportsFormat(fmt) shouldBe true
    }
  }

  test("Map[String,String] encoder/decoder covers supported and unsupported branches") {
    val m = Map("a" -> "1", "b" -> "2")
    val jsonEnc = mapStringStringEncoder.encode(m, DataFormat.JSON)
    jsonEnc.isRight shouldBe true
    mapStringStringDecoder.decode(jsonEnc.toOption.get, DataFormat.JSON).isRight shouldBe true

    val csvEnc = mapStringStringEncoder.encode(m, DataFormat.CSV)
    csvEnc.isRight shouldBe true
    mapStringStringDecoder.decode(csvEnc.toOption.get, DataFormat.CSV).isRight shouldBe true

    val bad = mapStringStringEncoder.encode(m, DataFormat.Parquet)
    bad.isLeft shouldBe true // UnsupportedFormat branch
  }

  test("Json encoder/decoder and tuple encoder exercise branches") {
    val j = Json.obj("x" -> Json.fromInt(1))
    val enc = jsonEncoder.encode(j, DataFormat.JSON)
    enc.isRight shouldBe true
    jsonDecoder.decode(enc.toOption.get, DataFormat.JSON).isRight shouldBe true

    // tuple2 encoder supported for JSON-based
    val tEncOk = tuple2Encoder[String, String].encode("k" -> "v", DataFormat.JSON)
    tEncOk.isRight shouldBe true
    val tEncBad = tuple2Encoder[String, String].encode("k" -> "v", DataFormat.CSV)
    tEncBad.isLeft shouldBe true
  }
}

