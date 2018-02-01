package com.datastore4s.core

import java.time.Instant

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Blob, Entity, LatLng}

import scala.reflect.macros.blackbox.Context

trait FieldFormat[A] {

  def addField(value: A, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder

  def fromField(entity: Entity, fieldName: String): A

}

object FieldFormat {

  implicit object StringFieldFormat extends FieldFormat[String] {
    override def addField(value: String, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): String = entity.getString(fieldName)
  }

  implicit object LongFieldFormat extends FieldFormat[Long] {
    override def addField(value: Long, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): Long = entity.getLong(fieldName)
  }

  implicit object BooleanFieldFormat extends FieldFormat[Boolean] {
    override def addField(value: Boolean, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): Boolean = entity.getBoolean(fieldName)
  }

  implicit object DoubleFieldFormat extends FieldFormat[Double] {
    override def addField(value: Double, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): Double = entity.getDouble(fieldName)
  }

  implicit object IntFieldFormat extends FieldFormat[Int] {
    override def addField(value: Int, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): Int = entity.getLong(fieldName).toInt
  }

  implicit object ByteArrayBlobFieldFormat extends FieldFormat[Array[Byte]] {
    override def addField(value: Array[Byte], fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, Blob.copyFrom(value))

    override def fromField(entity: Entity, fieldName: String): Array[Byte] = entity.getBlob(fieldName).toByteArray
  }

  implicit object TimestampFieldFormat extends FieldFormat[Timestamp] {
    override def addField(value: Timestamp, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): Timestamp = entity.getTimestamp(fieldName)
  }

  implicit object LatLngFieldFormat extends FieldFormat[LatLng] {
    override def addField(value: LatLng, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): LatLng = entity.getLatLng(fieldName)
  }

  implicit object InstantEpochMilliFormat extends FieldFormat[Instant] {
    override def addField(value: Instant, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value.toEpochMilli)

    override def fromField(entity: Entity, fieldName: String): Instant = Instant.ofEpochMilli(entity.getLong(fieldName))
  }

}

object NestedFieldFormat {

  import scala.language.experimental.macros

  def apply[A](): FieldFormat[A] = macro applyImpl[A]

  def applyImpl[A: context.WeakTypeTag](context: Context)(): context.Expr[FieldFormat[A]] = {
    import context.universe._

    val fieldType = weakTypeTag[A].tpe
    require(fieldType.typeSymbol.asClass.isCaseClass, s"Entity classes must be a case class but $fieldType is not")

    // TODO this relies on entity mutation. Is this avoidable? If not is it acceptable??
    // TODO is there some way to store the format as val ${fieldName}Format = implicitly[FieldFormat[A]]
    // TODO can we remove the empty q"" in fold left?
    // TODO Again create a helper to abstract this common code
    val builderExpression = fieldType.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten.foldLeft(q"": context.universe.Tree) {
      case (expression, field) =>
        val fieldName = field.asTerm.name
        q"""$expression
            implicitly[com.datastore4s.core.FieldFormat[${field.typeSignature.typeSymbol}]].addField(value.${fieldName}, fieldName + "." + ${fieldName.toString}, builder)
          """
    }

    // TODO create helper to abstract the common reflection code involved in all of these macros
    val constructionExpressions = fieldType.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten.map { field =>
      (q"""implicitly[com.datastore4s.core.FieldFormat[${field.typeSignature.typeSymbol}]].fromField(entity, fieldName + "." + ${field.asTerm.name.toString})""", field)
    }

    val args = constructionExpressions.map {
      case (expression, field) => AssignOrNamedArg(Ident(field.name), expression)
    }

    val companion = fieldType.typeSymbol.companion

    val expression =
      q"""new com.datastore4s.core.FieldFormat[$fieldType] {
            override def addField(value: $fieldType, fieldName: String, entityBuilder: com.google.cloud.datastore.Entity.Builder): com.google.cloud.datastore.Entity.Builder = {
              val builder = entityBuilder
              $builderExpression
              builder
            }

            override def fromField(entity: com.google.cloud.datastore.Entity, fieldName: String): $fieldType = {
              $companion.apply(..$args)
            }
          }
        """
    println(expression)

    context.Expr[FieldFormat[A]](
      expression
    )
  }

}
