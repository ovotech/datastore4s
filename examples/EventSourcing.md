

```scala
import com.ovoenergy.datastore4s._

sealed trait Event {
  val id: String
  val aggregation: Agg
}
case class Event1(id: String, aggregation: Agg) extends Event
case class Event2(id: String, aggregation: Agg) extends Event

case class Agg(id: Long)

case class State(id: Agg) {
  def applyEvent(event: Event) : State = ???
}

object State {
  def defaultState(id:Agg): State = ???
}

object EventSourcingRepository extends DatastoreRepository {
    override def dataStoreConfiguration = FromEnvironmentVariables
    
    def updateState(event:Event): DatastoreOperation[State] = for {
      _ <- put(event)
      state <- calculateState(event.aggregation)
    } yield state
    
    def storeInitialState(state: State) = put(state)
    
    def calculateState(id: Agg): DatastoreOperation[State] = for {
       initialState <- findOne[State, Agg](id)
       events <- list[Event].withAncestor(id).sequenced()
    } yield events.foldLeft(initialState.getOrElse(State.defaultState(id)))(_ applyEvent _)
    
}

```