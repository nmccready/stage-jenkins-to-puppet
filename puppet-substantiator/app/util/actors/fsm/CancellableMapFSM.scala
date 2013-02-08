package util.actors.fsm

import akka.actor.FSM
import akka.actor.Cancellable
import concurrent.duration._

trait ICancellableMapFSMDomain {

  case class Cancel(name: String)

}

trait ICancellableDelay extends Cancellable{
  def delayMillisecond:Int
}

case class CancellableDelay(override val delayMillisecond:Int,cancel:Cancellable) extends ICancellableDelay{
  def isCancelled = cancel.isCancelled
}

object CancellableMapFSMDomainProvider extends IMapFSMDomainProvider[ICancellableDelay] {
  val domain = new MapFSMDomain[ICancellableDelay] with ICancellableMapFSMDomain
}

class CancellableMapFSM
(additionalTimeoutMilli: Int,
 override val domain: MapFSMDomain[ICancellableDelay] with ICancellableMapFSMDomain = CancellableMapFSMDomainProvider.domain)
  extends MapFSM[ICancellableDelay](domain) {

  import domain._

  //http://tumblr.teamon.eu/post/2863302230/scala-combine-several-partial-functions-into-one
  override protected def partialUnHandled: StateFunction = {
    val localPartialUnhandled: StateFunction = {
      case Event(Cancel(key), Todo(ref, currentMap)) =>
        cancelTimer(key) //if called directly and before a timer
        currentMap(key).cancel()
        goto(Active) using Todo(ref, currentMap - key)
    }
    List(localPartialUnhandled, super.partialUnHandled) reduceLeft (_ orElse _)
  }

  override def handelExtraAdd(key: String,obj:ICancellableDelay) {
    setTimer(key, Cancel(key), obj.delayMillisecond + additionalTimeoutMilli millisecond, false)
  }

  override def handelExtraRemove(key: String) {
    cancelTimer(key)
  }
}


