package com.flowforge.core.patterns

import cats.data.NonEmptyList
import cats.syntax.all._
import com.flowforge.core.algebra.{ SchemaError, SchemaIncompatible }
import com.flowforge.core.patterns.ValidationTypes.{ invalid, valid, SchemaValidationResult }
import com.flowforge.core.types.{ DataSchema, DataType }

/**
 * Schema validation patterns for ensuring data structure compatibility.
 */
object SchemaValidation {

  /**
   * Validate that two schemas are compatible.
   */
  def compatible(
    source: DataSchema,
    target: DataSchema
  ): SchemaValidationResult = {
    val errors = scala.collection.mutable.ListBuffer[SchemaError]()

    // Check field compatibility
    target.fields.foreach { targetField =>
      source.fieldByName(targetField.name.value) match {
        case Some(sourceField) =>
          if (!areTypesCompatible(sourceField.dataType, targetField.dataType)) {
            errors += SchemaIncompatible(source, target)
          }
        case None =>
          if (targetField.isRequired) {
            errors += SchemaIncompatible(source, target)
          }
      }
    }

    NonEmptyList.fromList(errors.toList) match {
      case Some(errs) => errs.invalid
      case None       => ().valid
    }
  }

  /**
   * Validate schema evolution - ensure new schema can read old data.
   */
  def evolutionCompatible(
    oldSchema: DataSchema,
    newSchema: DataSchema
  ): SchemaValidationResult = {
    val errors = scala.collection.mutable.ListBuffer[SchemaError]()

    // Check for removed required fields
    oldSchema.requiredFields.foreach { oldField =>
      newSchema.fieldByName(oldField.name.value) match {
        case None => errors += SchemaIncompatible(oldSchema, newSchema)
        case Some(newField) =>
          if (!areTypesCompatible(oldField.dataType, newField.dataType)) {
            errors += SchemaIncompatible(oldSchema, newSchema)
          }
      }
    }

    // Check for added required fields without defaults
    newSchema.requiredFields.foreach { newField =>
      if (!oldSchema.fieldByName(newField.name.value).isDefined) {
        errors += SchemaIncompatible(oldSchema, newSchema)
      }
    }

    NonEmptyList.fromList(errors.toList) match {
      case Some(errs) => errs.invalid
      case None       => ().valid
    }
  }

  /**
   * Check if two data types are compatible.
   */
  private def areTypesCompatible(source: DataType, target: DataType): Boolean =
    (source, target) match {
      case (a, b) if a == b                       => true
      case (DataType.Integer, DataType.Long)      => true
      case (DataType.Float, DataType.Double)      => true
      case (DataType.VarChar(_), DataType.String) => true
      case (DataType.Nullable(inner1), DataType.Nullable(inner2)) =>
        areTypesCompatible(inner1, inner2)
      case (inner, DataType.Nullable(targetInner)) =>
        areTypesCompatible(inner, targetInner)
      case _ => false
    }

  /**
   * Validate required fields are present in schema.
   */
  def hasRequiredFields(
    schema: DataSchema,
    requiredFields: List[String]
  ): SchemaValidationResult = {
    val schemaFieldNames = schema.fieldNames.toSet
    val missingFields    = requiredFields.filterNot(schemaFieldNames.contains)

    if (missingFields.isEmpty) {
      valid(())
    } else {
      val error = SchemaIncompatible(
        DataSchema.builder.build, // Empty schema as placeholder
        schema
      )
      invalid(error)
    }
  }
}
