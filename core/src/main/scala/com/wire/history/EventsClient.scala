package com.wire.history

import com.wire.data.{ClientId, Event, JsonDecoder, Uid}
import com.wire.events.EventStream
import com.wire.logging.Logging.warn
import com.wire.macros.logging.ImplicitTag._
import com.wire.network._
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}
import org.json.JSONObject

import scala.util.control.NonFatal

class EventsClient(engine: ClientEngine) {

  import EventsClient._

  implicit val dispatcher = new SerialDispatchQueue(name = "SingleRequestClient")

  val onPageLoaded = EventStream[Seq[PushNotification]]()

  /**
    * NOTE: we should just drop any incoming triggers that occur while there is already a request underway.
    * There is no way a GCM notification can reach us before the end of the last request without that request
    * then coming back up-to-date. That then means that we can just ignore any duplicate triggers while there is a request.
    *
    * If we somehow get a GCM notification that is out-of-order with the last sync, worst case is that we send off
    * another request with the since id being the latest Id anyway, so we'll get back an empty list from BE. We could
    * alternatively check the timestamps to avoid this unnecessary request, but it's really not a big deal.
    */
  private var currentRequest = CancellableFuture.successful(Option.empty[Uid], false)

  def loadNotifications(since: Option[Uid], clientId: ClientId, pageSize: Int = 1000): CancellableFuture[Option[Uid]] = {

    def loadNextPage(lastStableId: Option[Uid]): CancellableFuture[(Option[Uid], Boolean)] =
      engine.fetch(RequestTag, Request.Get(notificationsPath(lastStableId, clientId, pageSize))) flatMap { r =>
        println(s"response: $r")
        r match {
          case Response(status, _, NotificationsResponse(ns, hasMore)) =>
            onPageLoaded ! ns
            if (hasMore) loadNextPage(ns.lastOption.map(_.id))
            else CancellableFuture.successful(ns.lastOption.map(_.id), false)

          //TODO handle failing
          case _ => CancellableFuture.failed(new Exception("TODO - handle failing"))
        }
      }

    currentRequest = if (currentRequest.isCompleted) loadNextPage(since) else currentRequest
    currentRequest.map(_._1)
  }
}

case class PushNotification(id: Uid, events: Seq[Event], transient: Boolean = false)

object PushNotification {
  implicit lazy val NotificationDecoder: JsonDecoder[PushNotification] = new JsonDecoder[PushNotification] {

    import com.wire.data.JsonDecoder._

    override def apply(implicit js: JSONObject) = PushNotification('id, array[Event](js.getJSONArray("payload")), 'transient)
  }
}

object EventsClient {

  def notificationsPath(since: Option[Uid], client: ClientId, pageSize: Int) = {
    val args = Seq("since" -> since, "client" -> Some(client), "size" -> Some(pageSize)) collect { case (key, Some(v)) => key -> v }
    Request.query(NotificationsPath, args: _*)
  }

  val NotificationsPath = "/notifications"
  val RequestTag = "loadNotifications"

  object NotificationsResponse {

    import com.wire.data.JsonDecoder._

    def unapply(response: ResponseContent): Option[(Vector[PushNotification], Boolean)] = try response match {
      case JsonObjectResponse(js) if js.has("notifications") => Some((arrayColl[PushNotification, Vector](js.getJSONArray("notifications")), decodeBool('has_more)(js)))
      case JsonArrayResponse(js) => Some((arrayColl[PushNotification, Vector](js), false))
      case _ => None
    } catch {
      case NonFatal(e) =>
        warn(s"couldn't parse paged push notifications from response: $response", e)
        None
    }
  }

}

