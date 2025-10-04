package com.flowforge.core.algebra
import com.flowforge.core.types._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class EncoderDecoderInstanceCoverageSpec extends AnyFunSuite with Matchers {
  test("DataEncoder.instance and DataDecoder.instance defaults") {
    val enc = DataEncoder.instance[Int](
      (a, f) => Right(EncodedData(a.toString.getBytes("UTF-8"), f)),
      _ => DataSchema.builder.addField("value", DataType.Integer).build,
    )
    val e = enc.encode(7, DataFormat.JSON).toOption.get
    new String(e.data, "UTF-8") shouldBe "7"
    enc.estimateSize(7, DataFormat.JSON) shouldBe 1024L
    enc.supportsFormat(DataFormat.Delta) shouldBe true
    enc.optimizationHints(7, DataFormat.JSON) shouldBe EncodingHints.default

    val dec = DataDecoder.instance[Int]((ed, _) => Right(new String(ed.data, "UTF-8").toInt))
    dec.decode(e, DataFormat.JSON).toOption.get shouldBe 7
    dec.validateSchema(e, DataSchema.builder.build).isRight shouldBe true
    dec.decodeWithEvolution(e, DataFormat.JSON, DataSchema.builder.build).toOption.get shouldBe 7
  }

  test("EncodingError/DecodingError helpers") {
    val ue  = UnsupportedFormat(DataFormat.Parquet, "X").withContext(Map("k" -> 1)).withCause(new RuntimeException("x"))
    ue.message.toLowerCase should include ("not supported")
    val cd  = CorruptedData("bad").withContext(Map("k" -> 2)).withCause(new RuntimeException("y"))
    cd.message.toLowerCase should include ("corrupted")
  }
}
