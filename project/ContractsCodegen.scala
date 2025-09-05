import sbt._

object ContractsCodegen {
  case class Codegen(relativePath: String, contents: String)

  private val pkgBase = "com.flowforge.contracts"

  def generateScala(relPath: String, avroJson: String): Either[String, Codegen] = {
    // Expect relPath like: sales/Transactions.v1.0.0.avsc
    val parts = relPath.split('/').toList
    if (parts.length < 2) return Left(s"Invalid path: $relPath")
    val domain    = parts.init.last
    val fileName  = parts.last
    val nameNoExt = fileName.stripSuffix(".avsc")
    val jsonTop = avroJson.take(avroJson.indexOf("\"fields\"") match {
      case -1 => avroJson.length; case idx => idx
    })
    // Prefer top-level Avro name if present (e.g., TransactionsV1)
    val topNameRe = """"name"\s*:\s*"([^"]+)"""".r
    val topName   = topNameRe.findFirstMatchIn(jsonTop).map(_.group(1))
    // Fallback to file name base (strip version suffix if present)
    val fallbackEntity = nameNoExt.replaceAll("\\.v[0-9]+\\.[0-9]+\\.[0-9]+$", "")
    val entity         = topName.getOrElse(fallbackEntity)

    // Very simple field extractor: look for lines under "fields" array with name/type primitives
    val fields = extractFields(avroJson)
    if (fields.isEmpty) return Left("No fields parsed or unsupported schema")

    val pkg    = s"$pkgBase.$domain"
    val cls    = sanitize(entity)
    val params = fields.map { case (n, t) => s"${sanitizeVar(n)}: ${mapScalaType(t)}" }.mkString(",\n  ")
    val code =
      s"""
         |package %s
         |
         |import shapeless.LabelledGeneric
         |
         |final case class %s(
         |  %s
         |)
         |
         |object %sContract {
         |  type Repr = LabelledGeneric[%s]#Repr
         |}
         |""".stripMargin.format(pkg, cls, params, cls, cls)

    val relOut = pkg.replace('.', '/') + s"/$cls.scala"
    Right(Codegen(relOut, code))
  }

  private def sanitize(name: String): String = name.replaceAll("[^A-Za-z0-9_]", "_") match {
    case s if s.headOption.exists(_.isDigit) => s"N_$s"
    case s                                   => s
  }
  private def sanitizeVar(name: String): String = sanitize(name) match {
    case s if s == "type" => "`type`"
    case s                => s
  }
  private def mapScalaType(avroType: String): String = avroType.toLowerCase match {
    case "string"  => "String"
    case "int"     => "Int"
    case "long"    => "Long"
    case "double"  => "Double"
    case "float"   => "Float"
    case "boolean" => "Boolean"
    case other     => "String" // fallback to string for unsupported (risk mitigated; user can override)
  }
  private def extractFields(json: String): List[(String, String)] = {
    // Isolate the fields array content
    val s     = json
    val key   = "\"fields\""
    val start = s.indexOf(key)
    if (start < 0) return Nil
    val lb = s.indexOf('[', start)
    if (lb < 0) return Nil
    var i     = lb + 1
    var depth = 1
    var end   = -1
    var inStr = false
    var prev  = '\u0000'
    while (i < s.length && end == -1) {
      val ch = s.charAt(i)
      if (ch == '"' && prev != '\\') inStr = !inStr
      if (!inStr) {
        if (ch == '[') depth += 1
        else if (ch == ']') { depth -= 1; if (depth == 0) end = i }
      }
      prev = ch
      i += 1
    }
    val arr = if (end > lb) s.substring(lb + 1, end) else s
    // Extract objects and read name/type pairs
    val out = scala.collection.mutable.ListBuffer.empty[(String, String)]
    var j   = 0
    while (j < arr.length) {
      val so = arr.indexOf('{', j)
      if (so < 0) j = arr.length
      else {
        var k     = so + 1
        var d     = 1
        var eo    = -1
        var str   = false
        var pprev = '\u0000'
        while (k < arr.length && eo == -1) {
          val ch = arr.charAt(k)
          if (ch == '"' && pprev != '\\') str = !str
          if (!str) {
            if (ch == '{') d += 1
            else if (ch == '}') { d -= 1; if (d == 0) eo = k }
          }
          pprev = ch
          k += 1
        }
        if (eo > so) {
          val obj       = arr.substring(so, eo + 1)
          val nameRegex = """"name"\s*:\s*"([^"]+)"""".r
          val typeRegex = """"type"\s*:\s*"([^"]+)"""".r
          val nameOpt   = nameRegex.findFirstMatchIn(obj).map(_.group(1))
          val typeOpt   = typeRegex.findFirstMatchIn(obj).map(_.group(1))
          (nameOpt, typeOpt) match {
            case (Some(n), Some(t)) => out += (n -> t)
            case _                  => // ignore complex types for demo
          }
          j = eo + 1
        } else j = arr.length
      }
    }
    out.toList
  }
}
