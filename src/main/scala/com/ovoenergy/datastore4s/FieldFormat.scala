package com.datastore4s.core

import java.time.Instant

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{BaseEntity, Blob, Entity, LatLng}

import scala.reflect.macros.blackbox.Context

trait FieldFormat[A] {
  // TODO should there be some form of asValue(a:A):Value[_]  for queries? How would this affect the nestedfield format?

  def addField(value: A, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder

  def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): A

}

object FieldFormat {

  implicit object StringFieldFormat extends FieldFormat[String] {
    override def addField(value: String, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): String = entity.getString(fieldName)
  }

  implicit object LongFieldFormat extends FieldFormat[Long] {
    override def addField(value: Long, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): Long = entity.getLong(fieldName)
  }

  implicit object BooleanFieldFormat extends FieldFormat[Boolean] {
    override def addField(value: Boolean, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): Boolean = entity.getBoolean(fieldName)
  }

  implicit object DoubleFieldFormat extends FieldFormat[Double] {
    override def addField(value: Double, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): Double = entity.getDouble(fieldName)
  }

  implicit object IntFieldFormat extends FieldFormat[Int] {
    override def addField(value: Int, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): Int = entity.getLong(fieldName).toInt
  }

  implicit object ByteArrayBlobFieldFormat extends FieldFormat[Array[Byte]] {
    override def addField(value: Array[Byte], fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, Blob.copyFrom(value))

    override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): Array[Byte] = entity.getBlob(fieldName).toByteArray
  }

  implicit object TimestampFieldFormat extends FieldFormat[Timestamp] {
    override def addField(value: Timestamp, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): Timestamp = entity.getTimestamp(fieldName)
  }

  implicit object LatLngFieldFormat extends FieldFormat[LatLng] {
    override def addField(value: LatLng, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): LatLng = entity.getLatLng(fieldName)
  }

  // TODO should the following have to be brought into implicit scope in case people want to persist differently?
  implicit object InstantEpochMilliFormat extends FieldFormat[Instant] {
    override def addField(value: Instant, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value.toEpochMilli)

    override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): Instant = Instant.ofEpochMilli(entity.getLong(fieldName))
  }

  implicit object BigDecimalStringFormat extends FieldFormat[BigDecimal] {
    override def addField(value: BigDecimal, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value.toString())

    override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): BigDecimal = BigDecimal(entity.getString(fieldName))
  }

  implicit def optionFormat[A](implicit format:FieldFormat[A]): FieldFormat[Option[A]] = new  FieldFormat[Option[A]] {
    override def addField(value: Option[A], fieldName: String, entityBuilder: Entity.Builder) = value match {
      case Some(a) => format.addField(a, fieldName, entityBuilder)
      case None => entityBuilder.setNull(fieldName)
    }

    override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String) = Option(format.fromField(entity, fieldName))
  }

  def fieldFormatFromFunctions[A, B](constructor: B => A)(extractor: A => B)(implicit existingFormat: FieldFormat[B]): FieldFormat[A] = {
    new FieldFormat[A] {
      override def addField(value: A, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = existingFormat.addField(extractor(value), fieldName, entityBuilder)

      override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): A = constructor(existingFormat.fromField(entity, fieldName))
    }
  }

}

object NestedFieldFormat {

  import scala.language.experimental.macros

  def apply[A](): FieldFormat[A] = macro applyImpl[A]

  def applyImpl[A: context.WeakTypeTag](context: Context)(): context.Expr[FieldFormat[A]] = {
    import context.universe._

    val fieldType = weakTypeTag[A].tpe
    if(!fieldType.typeSymbol.asClass.isCaseClass){ context.abort(context.enclosingPosition, s"NestedFieldFormat classes must be a case class but $fieldType is not") }

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

            override def fromField[E <: com.google.cloud.datastore.BaseEntity[_]](entity: E, fieldName: String): $fieldType = {
              $companion.apply(..$args)
            }
          }
        """
    context.info(context.enclosingPosition, expression.toString, false)

    context.Expr[FieldFormat[A]](
      expression
    )
  }

}
