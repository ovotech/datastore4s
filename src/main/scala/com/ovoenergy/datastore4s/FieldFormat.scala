package com.ovoenergy.datastore4s

import scala.reflect.macros.blackbox

trait FieldFormat[A] {

  def toEntityField(fieldName: String, value: A): Field

  def fromEntityField(fieldName: String, entity: Entity): Either[DatastoreError, A] =
    fromEntityFieldWithoutContext(fieldName, entity).left.map(DatastoreError.errorInField(fieldName))

  private[datastore4s] def fromEntityFieldWithoutContext(fieldName: String, entity: Entity): Either[DatastoreError, A]

}

class Field private (val values: Seq[(String, DatastoreValue)]) {
  def +(name: String, value: DatastoreValue) = new Field((name -> value) +: values)

  def +(other: Field) = new Field(other.values ++ values) // Composite field
}

object Field {
  def apply(name: String, value: DatastoreValue): Field = new Field(Seq(name -> value))
}

object FieldFormat {

  implicit def fieldFormatFromValueFormat[A](implicit valueFormat: ValueFormat[A]): FieldFormat[A] =
    new FieldFormat[A] {
      override def toEntityField(fieldName: String, value: A) =
        Field(fieldName, valueFormat.toValue(value))

      override def fromEntityFieldWithoutContext(fieldName: String, entity: Entity): Either[DatastoreError, A] =
        entity
          .field(fieldName)
          .map(valueFormat.fromValue)
          .getOrElse(DatastoreError.missingField(fieldName, entity))
    }

  private val eitherField = "either_side"

  implicit def fieldFormatFromEither[L, R](implicit leftFormat: FieldFormat[L], rightFormat: FieldFormat[R]): FieldFormat[Either[L, R]] =
    new FieldFormat[Either[L, R]] {
      override def toEntityField(fieldName: String, value: Either[L, R]): Field = value match {
        case Left(l)  => leftFormat.toEntityField(fieldName, l) + (s"$fieldName.$eitherField", StringValue("Left"))
        case Right(r) => rightFormat.toEntityField(fieldName, r) + (s"$fieldName.$eitherField", StringValue("Right"))
      }

      override def fromEntityFieldWithoutContext(fieldName: String, entity: Entity): Either[DatastoreError, Either[L, R]] =
        entity.field(s"$fieldName.$eitherField") match {
          case Some(StringValue("Left"))  => leftFormat.fromEntityField(fieldName, entity).map(Left(_))
          case Some(StringValue("Right")) => rightFormat.fromEntityField(fieldName, entity).map(Right(_))
          case Some(other)                => DatastoreError.error(s"Either field should be either 'Left' or 'Right' but was $other.")
          case None                       => DatastoreError.missingField(eitherField, entity)
        }
    }

  import scala.language.experimental.macros

  def apply[A]: FieldFormat[A] = macro applyImpl[A]

  def applyImpl[A: context.WeakTypeTag](context: blackbox.Context): context.Expr[FieldFormat[A]] = {
    val helper = MacroHelper(context)
    import context.universe._
    val fieldType = weakTypeTag[A].tpe
    helper.sealedTraitCaseClassOrAbort[FieldFormat[A]](fieldType, sealedTraitFormat(context)(helper), caseClassFormat(context)(helper))
  }

  private def sealedTraitFormat[A: context.WeakTypeTag](
    context: blackbox.Context
  )(helper: MacroHelper[context.type]): context.Expr[FieldFormat[A]] = {
    import context.universe._
    val fieldType = weakTypeTag[A].tpe
    val subTypes = helper.subTypes(fieldType)

    val toCases = subTypes.map { subType =>
      if (helper.isObject(subType)) {
        cq"""f: ${subType.asClass} => stringFormat.toEntityField(fieldName + ".type", ${subType.name.toString})"""
      } else {
        cq"""f: ${subType.asClass} => FieldFormat[$subType].toEntityField(fieldName, f) + stringFormat.toEntityField(fieldName + ".type", ${subType.name.toString})"""
      }
    }

    val fromCases = subTypes.map { subType =>
      if (helper.isObject(subType)) {
        cq"""Right(${subType.name.toString}) => Right(${helper.singletonObject(subType)})"""
      } else {
        cq"""Right(${subType.name.toString}) => FieldFormat[$subType].fromEntityField(fieldName, entity)"""
      }
    }

    context.Expr[FieldFormat[A]](q"""import com.ovoenergy.datastore4s._

          new FieldFormat[$fieldType] {
            private val stringFormat = implicitly[FieldFormat[String]]
            override def toEntityField(fieldName: String, value: $fieldType): Field = value match {
              case ..$toCases
            }

            override def fromEntityFieldWithoutContext(fieldName: String, entity: Entity): Either[DatastoreError, $fieldType] = stringFormat.fromEntityField(fieldName + ".type", entity) match {
              case ..$fromCases
              case Right(other) => DatastoreError.error(s"Unknown subtype found: $$other")
              case Left(error) => Left(error)
            }
          }
        """)
  }

  private def caseClassFormat[A: context.WeakTypeTag](
    context: blackbox.Context
  )(helper: MacroHelper[context.type]): context.Expr[FieldFormat[A]] = {
    import context.universe._

    val fieldType = weakTypeTag[A].tpe

    val fields = helper.caseClassFieldList(fieldType)

    val fieldExpressions = fields.map { field =>
      val fieldName = field.asTerm.name
      q"""implicitly[FieldFormat[${field.typeSignature}]].toEntityField(fieldName + "." + ${fieldName.toString}, value.$fieldName)"""
    }

    val companion = fieldType.typeSymbol.companion
    val companionNamedArguments = fields.map(field => AssignOrNamedArg(Ident(field.name), q"${field.asTerm.name}"))

    val fieldFormats = fields.map { field =>
      val fieldName = field.asTerm.name
      fq"""${field.name} <- implicitly[FieldFormat[${field.typeSignature}]].fromEntityField(fieldName + "." + ${fieldName.toString}, entity)"""
    }

    context.Expr[FieldFormat[A]](q"""import com.ovoenergy.datastore4s._

          new FieldFormat[$fieldType] {
            override def toEntityField(fieldName: String, value: $fieldType): Field = {
              ${fieldExpressions.reduce(concatFieldExpressionsWithAdd(context)(_, _))}
            }

            override def fromEntityFieldWithoutContext(fieldName: String, entity: Entity): Either[DatastoreError, $fieldType] = {
              for (
                ..$fieldFormats
              ) yield $companion.apply(..$companionNamedArguments)
            }
          }
        """)
  }

  private def concatFieldExpressionsWithAdd(context: blackbox.Context)(fieldExpression1: context.universe.Tree,
                                                                       fieldExpression2: context.universe.Tree): context.universe.Tree = {
    import context.universe._
    q"$fieldExpression1 + $fieldExpression2"
  }

}
