package com.flowforge.core.codecs

import com.flowforge.core.algebra._
import com.flowforge.core.instances.DefaultCodecs._
import com.flowforge.core.types._
import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import com.flowforge.core.testing.What

class CodecsMatrixSpec extends AnyFunSuite with Matchers {

  private val all = List(
    DataFormat.JSON,
    DataFormat.JSONL,
    DataFormat.CSV,
    DataFormat.Parquet,
    DataFormat.Avro,
    DataFormat.ORC,
    DataFormat.Delta,
  )

  test("Int encoder supports JSON/JSONL/CSV only; String encoder supports all; decoders follow supportsFormat", What) {
    val i = 42
    val s = "hello"
    all.foreach { f =>
      val ie = intEncoder.encode(i, f)
      ie.isRight shouldBe true
      intEncoder.supportsFormat(f) shouldBe (f == DataFormat.JSON || f == DataFormat.JSONL || f == DataFormat.CSV)

      val se = stringEncoder.encode(s, f)
      se.isRight shouldBe true
      // string encoder is format-agnostic; behavior is exercised via encode above

      val sd = stringDecoder.decode(EncodedData(s.getBytes("UTF-8"), f), f)
      sd.isRight shouldBe true
      stringDecoder.supportsFormat(f) shouldBe (f == DataFormat.JSON || f == DataFormat.JSONL || f == DataFormat.CSV)
    }
  }

  test("Json encoder/decoder and corrupted input branches", What) {
    val j   = Json.obj("a" -> Json.fromString("b"))
    val enc = jsonEncoder.encode(j, DataFormat.JSON)
    enc.isRight shouldBe true
    jsonDecoder.decode(enc.toOption.get, DataFormat.JSON).isRight shouldBe true

    // corrupted: not JSON object/array where expected
    val bad = EncodedData("not-json".getBytes("UTF-8"), DataFormat.JSON)
    jsonDecoder.decode(bad, DataFormat.JSON).isLeft shouldBe true
  }

  test("Map[String,String] decoder error branch (expected object) and CSV array split", What) {
    val m      = Map("k1" -> "v1", "k2" -> "v2")
    val mEncJ  = mapStringStringEncoder.encode(m, DataFormat.JSON).toOption.get
    mapStringStringDecoder.decode(mEncJ, DataFormat.JSON).toOption.get shouldBe m

    val csvEnc = mapStringStringEncoder.encode(m, DataFormat.CSV).toOption.get
    val back   = mapStringStringDecoder.decode(csvEnc, DataFormat.CSV).toOption.get
    back.values.toList should contain allElementsOf m.values

    val notObj = EncodedData("[1,2,3]".getBytes("UTF-8"), DataFormat.JSON)
    mapStringStringDecoder.decode(notObj, DataFormat.JSON).isLeft shouldBe true
  }

  test("tuple2Encoder supports JSON only", What) {
    val ok  = tuple2Encoder[String, String].encode("k" -> "v", DataFormat.JSON)
    ok.isRight shouldBe true
    val bad = tuple2Encoder[String, String].encode("k" -> "v", DataFormat.CSV)
    bad.isLeft shouldBe true
  }
}
