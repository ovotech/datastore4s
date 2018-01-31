package com.datastore4s.core

import com.google.cloud.datastore.Entity

import scala.annotation.Annotation
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context
import scala.util.Try

final case class EntityKind(kind:String) extends Annotation

trait DatastoreEntity[KeyType] {
  def key: KeyType
}

trait ToEntity[EntityType <: DatastoreEntity[KeyType], KeyType] {
  def toEntity(record: EntityType)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory): Entity
}

object ToEntity {

  def apply[EntityType <: DatastoreEntity[KeyType], KeyType](): ToEntity[EntityType, KeyType] = macro applyImpl[EntityType, KeyType]

  def applyImpl[EntityType <: DatastoreEntity[KeyType] : context.WeakTypeTag, KeyType: context.WeakTypeTag](context: Context)(): context.Expr[ToEntity[EntityType, KeyType]] = {
    import context.universe._

    val entityType = weakTypeTag[EntityType].tpe
    require(entityType.typeSymbol.asClass.isCaseClass, s"Entity classes must be a case class but $entityType is not")

    val kind = entityType.typeSymbol.annotations.collect {
      case annotation if annotation.tree.tpe <:< context.weakTypeOf[EntityKind] =>
        annotation.tree.children.tail match {
          case Literal(Constant(kind: String)) :: Nil => EntityKind(kind)
        }
    } match {
      case Nil => context.abort(context.enclosingPosition, "Entity case class must be annotated with @Kind")
      case ann :: Nil => TermName(ann.kind)
      case more => context.abort(context.enclosingPosition, s"Entity case class must be annotated with @Kind precisely once, but annotations were: $more")
    }

    val keyType = weakTypeTag[KeyType].tpe

    val keyExpression =
            q"""val keyFactory = new com.datastore4s.core.KeyFactoryFacade(keyFactorySupplier().setKind(${kind.decodedName.toString}))
               implicitly[com.datastore4s.core.ToKey[${keyType.typeSymbol}]].toKey(value.key, keyFactory)"""

    // TODO this relies on entity mutation. Is this avoidable? If not is it acceptable??
    // TODO is there some way to store the format as val ${fieldName}Format = implicitly[FieldFormat[A]]
    // TODO can we remove the empty q"" in fold left?
    val builderExpression = entityType.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten.foldLeft(q"": context.universe.Tree) {
      case (expression, field) =>
        val fieldName = field.asTerm.name
        q"""$expression
            implicitly[com.datastore4s.core.FieldFormat[${field.typeSignature.typeSymbol}]].addField(value.${fieldName}, ${fieldName.toString}, builder)
          """
    }

    // TODO why does builder expression open a new scope??
    val expression =
      q"""new com.datastore4s.core.ToEntity[$entityType, $keyType] {
            override def toEntity(value: $entityType)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory): com.google.cloud.datastore.Entity = {
              val key = $keyExpression
              val builder = com.google.cloud.datastore.Entity.newBuilder(key)
              $builderExpression
              builder.build()
            }
          }
        """

    println(expression)

    context.Expr[ToEntity[EntityType, KeyType]](
      expression
    )
  }
}

trait FromEntity[EntityType <: DatastoreEntity[KeyType], KeyType] {
  def fromEntity(entity: Entity): Try[EntityType]
}

object FromEntity {
  def apply[EntityType <: DatastoreEntity[KeyType], KeyType](): FromEntity[EntityType, KeyType] = macro applyImpl[EntityType, KeyType]

  def applyImpl[EntityType <: DatastoreEntity[KeyType] : context.WeakTypeTag, KeyType: context.WeakTypeTag](context: Context)(): context.Expr[FromEntity[EntityType, KeyType]] = {
    import context.universe._

    val entityType = weakTypeTag[EntityType].tpe
    require(entityType.typeSymbol.asClass.isCaseClass, s"Entity classes must be a case class but $entityType is not")

    // TODO can we store the implicit format?
    val constructionExpressions = entityType.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten.map { field =>
      (q"implicitly[com.datastore4s.core.FieldFormat[${field.typeSignature.typeSymbol}]].fromField(entity, ${field.asTerm.name.toString})", field)
    }

    val args = constructionExpressions.map {
      case (expression, field) => AssignOrNamedArg(Ident(field.name), expression)
    }

    val companion = entityType.typeSymbol.companion
    val keyType = weakTypeTag[KeyType].tpe

    val expression =
      q"""new com.datastore4s.core.FromEntity[$entityType, $keyType] {
            override def fromEntity(entity: com.google.cloud.datastore.Entity): scala.util.Try[$entityType] = scala.util.Try {
              $companion.apply(..$args)
            }
          }
        """
    println(expression)
    context.Expr[FromEntity[EntityType, KeyType]](
      expression
    )
  }
}

//trait EntityFormat[EntityType, KeyType] {
//  val kind: String
//
//  def toEntity(record: EntityType)(implicit keyFactorySupplier: () => KeyFactory): Entity
//
//  def fromEntity(entity: Entity): Try[EntityType]
//}
