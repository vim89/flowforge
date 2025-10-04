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
         |// Generated from Avro by FlowForge ContractsCodegen
         |// NOTE: Keep this minimal and macro-friendly; compile-time conformance uses SchemaConforms
         |
         |final case class %s(
         |  %s
         |)
         |""".stripMargin.format(pkg, cls, params)

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
    val jsonString = json
    val key        = "\"fields\""
    val start      = jsonString.indexOf(key)
    if (start < 0) return Nil
    val lb = jsonString.indexOf('[', start)
    if (lb < 0) return Nil
    var i     = lb + 1
    var depth = 1
    var end   = -1
    var inStr = false
    var prev  = '\u0000'
    while (i < jsonString.length && end == -1) {
      val ch = jsonString.charAt(i)
      if (ch == '"' && prev != '\\') inStr = !inStr
      if (!inStr) {
        if (ch == '[') depth += 1
        else if (ch == ']') { depth -= 1; if (depth == 0) end = i }
      }
      prev = ch
      i += 1
    }
    val arr = if (end > lb) jsonString.substring(lb + 1, end) else jsonString
    // Extract objects and read name/type pairs
    val out   = scala.collection.mutable.ListBuffer.empty[(String, String)]
    var index = 0
    while (index < arr.length) {
      val startObj = arr.indexOf('{', index)
      if (startObj < 0) index = arr.length
      else {
        var pos    = startObj + 1
        var depth  = 1
        var endObj = -1
        var str    = false
        var pprev  = '\u0000'
        while (pos < arr.length && endObj == -1) {
          val ch = arr.charAt(pos)
          if (ch == '"' && pprev != '\\') str = !str
          if (!str) {
            if (ch == '{') depth += 1
            else if (ch == '}') { depth -= 1; if (depth == 0) endObj = pos }
          }
          pprev = ch
          pos += 1
        }
        if (endObj > startObj) {
          val obj       = arr.substring(startObj, endObj + 1)
          val nameRegex = """"name"\s*:\s*"([^"]+)"""".r
          val typeRegex = """"type"\s*:\s*"([^"]+)"""".r
          val nameOpt   = nameRegex.findFirstMatchIn(obj).map(_.group(1))
          val typeOpt   = typeRegex.findFirstMatchIn(obj).map(_.group(1))
          (nameOpt, typeOpt) match {
            case (Some(name), Some(fieldType)) => out += (name -> fieldType)
            case _                             => // ignore complex types for demo
          }
          index = endObj + 1
        } else index = arr.length
      }
    }
    out.toList
  }
}
