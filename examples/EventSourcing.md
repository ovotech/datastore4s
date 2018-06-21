An simple example of event sourcing using ancestors to group events. In the example ancestors are used to ensure strong 
consistency between persisting an event and retrieving all events to calculate the new state.

```scala
import com.ovoenergy.datastore4s._
import scala.util.Try

object EventSourcingRepository extends DatastoreRepository {

  override def datastoreConfiguration = FromEnvironmentVariables

  implicit val idFormat = formatFrom(CharacterId.apply)(_.value)
  implicit val attackTypeFormat = FieldFormat[AttackType]
  implicit val eventFormat = FieldFormat[Event]

  implicit val characterIdAncestor = toLongAncestor[CharacterId]("Character")(_.value)
  implicit val eventKeyToKey = toKey[EventKey]((key, builder) => builder.addAncestor(key.character).buildWithName(key.eventId))
  implicit val characterEventFormat = EntityFormat[CharacterEvent, EventKey]("event")(ce => EventKey(ce.characterId, ce.eventId))

  implicit val characterIdToKey = toKey[CharacterId]((id, builder) => builder.buildWithId(id.value))
  implicit val stateFormat = EntityFormat[CharacterState, CharacterId]("initialState")(_.id)

  def storeInitialState(state: CharacterState): Try[Persisted[CharacterState]] = runF(put(state))

  def getCurrentState(id: CharacterId): Try[CharacterState] = runF(calculateState(id))
  
  def updateState(event: CharacterEvent): Try[CharacterState] = {
    val operation = for {
      _ <- put(event)
      state <- calculateState(event.characterId)
    } yield state
    runF(operation)
  }

  private def calculateState(id: CharacterId): DatastoreOperation[CharacterState] = for {
    initialState <- findOne[CharacterState, CharacterId](id)
    events <- list[CharacterEvent].withAncestor(id).orderByAsc("eventNumber").sequenced()
  } yield events.foldLeft(initialState.getOrElse(CharacterState.defaultState(id)))(CharacterState.transition)

}

// Model

case class EventKey(character: CharacterId, eventId: String)
case class CharacterEvent(eventId: String, characterId: CharacterId, eventNumber: Int, event: Event)

sealed trait Event
case object CastResurrection extends Event
case object DrinkHealthPotion extends Event
case object LevelUp extends Event
case class AttackEnemy(attackType: AttackType) extends Event
case class EnemyAttack(damage: Int) extends Event

sealed trait AttackType { def cost:Int }
case object Weak extends AttackType { override def cost: Int = 10 }
case object Strong extends AttackType { override def cost: Int = 20 }
case class Special(cost: Int) extends AttackType

case class CharacterId(value: Long)

sealed trait CharacterState {
  val id: CharacterId
}

case class DeceasedCharacter(id: CharacterId) extends CharacterState
case class LivingCharacter(
  id: CharacterId,
  maxHealth: Int,
  health:Int,
  maxStamina: Int,
  stamina: Int,
  level:Int
) extends CharacterState

object CharacterState {

  def defaultState(id: CharacterId): CharacterState = LivingCharacter(id, 100, 100, 50, 50, 1)

  def transition(state: CharacterState, ce: CharacterEvent): CharacterState = (state, ce.event) match {
    case (DeceasedCharacter(id), CastResurrection) => CharacterState.defaultState(id)
    case (living: LivingCharacter, DrinkHealthPotion) => living.copy(health = living.maxHealth.max(living.health + 20))
    case (LivingCharacter(id, maxHealth, _, maxStamina, _, level), LevelUp) =>
      val newMaxHealth = maxHealth + 30
      val newMaxStamina = maxStamina + 20
      LivingCharacter(id, newMaxHealth, newMaxHealth, newMaxStamina, newMaxStamina, level + 1)
    case (living:LivingCharacter, EnemyAttack(damage)) => damageCharacter(living, damage)
    case (living: LivingCharacter, AttackEnemy(attackType)) =>
      if(living.stamina > attackType.cost)
        living.copy(stamina = living.stamina - attackType.cost)
      else
        damageCharacter(living, 10)
    case _ => state
  }

  private def damageCharacter(character: LivingCharacter, damage: Int) =
    if(damage > character.health)
      DeceasedCharacter(character.id)
    else
      character.copy(health = character.health - damage)

}

```