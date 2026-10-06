package com.flowforge.app

import org.scalatest.funsuite.AnyFunSuite

class PipelineAppSpec extends AnyFunSuite {
  test("the User contract derives the fields the pipeline declares") {
    assert(PipelineApp.userShape.fields.map(_.name) == List("id", "email", "age"))
  }
}
