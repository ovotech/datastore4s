package com.ovoenergy.datastore4s

import com.ovoenergy.datastore4s.internal.{DatastoreError, EntityBuilder, ValueFormat}

import scala.reflect.macros.blackbox.Context

trait FieldFormat[A] { // TODO is there a way to remove the need for this trait? It only exists for the customisation of NestedFieldFormat

  def addField(value: A, fieldName: String, builder: EntityBuilder): EntityBuilder

  def fromField(entity: com.ovoenergy.datastore4s.internal.Entity, fieldName: String): Either[DatastoreError, A]

}

object FieldFormat {

  // TODO format from EntityFormats
  // TODO Sealed trait formats using a dtype field
  implicit def fieldFormatFromValueFormat[A](implicit valueFormat: ValueFormat[A]): FieldFormat[A] = new FieldFormat[A] {
    override def addField(value: A, fieldName: String, builder: EntityBuilder): EntityBuilder =
      builder.addField(fieldName, valueFormat.toValue(value))

    override def fromField(entity: com.ovoenergy.datastore4s.internal.Entity, fieldName: String): Either[DatastoreError, A] =
      entity.field(fieldName).map(valueFormat.fromValue).getOrElse(DatastoreError.missingField(fieldName, entity))
  }

}

object NestedFieldFormat {

  import scala.language.experimental.macros

  def apply[A](): FieldFormat[A] = macro applyImpl[A]

  def applyImpl[A: context.WeakTypeTag](context: Context)(): context.Expr[FieldFormat[A]] = {
    import context.universe._
    val helper = MacroHelper(context)

    val fieldType = weakTypeTag[A].tpe
    helper.requireCaseClass(fieldType)

    val fields = helper.caseClassFieldList(fieldType)

    // TODO Two more abstractables here
    val builderExpressions = fields.map { field =>
      val fieldName = field.asTerm.name
      q"""implicitly[FieldFormat[${field.typeSignature.typeSymbol}]].addField(value.${fieldName}, fieldName + "." + ${fieldName.toString}, entityBuilder)"""
    }

    val companion = fieldType.typeSymbol.companion
    val companionNamedArguments = fields.map(field =>AssignOrNamedArg(Ident(field.name), q"${field.asTerm.name}"))

    val fieldFormats = fields.map { field =>
      val fieldName = field.asTerm.name
        fq"""${field.name} <- implicitly[FieldFormat[${field.typeSignature.typeSymbol}]].fromField(entity, fieldName + "." + ${fieldName.toString})"""
    }

    val expression =
      q"""import com.ovoenergy.datastore4s._
          import com.ovoenergy.datastore4s.internal._
          import com.ovoenergy.datastore4s.internal.Entity

          new FieldFormat[$fieldType] {
            override def addField(value: $fieldType, fieldName: String, entityBuilder: EntityBuilder): EntityBuilder = {
              ..$builderExpressions
              entityBuilder
            }

            override def fromField(entity: Entity, fieldName: String): Either[DatastoreError, $fieldType] = {
              for (
                ..$fieldFormats
              ) yield $companion.apply(..$companionNamedArguments)
            }
          }
        """
    context.info(context.enclosingPosition, expression.toString, false)

    context.Expr[FieldFormat[A]](
      expression
    )
  }

}
