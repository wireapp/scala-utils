package com.wire.events

import com.wire.data.{ClientId, Event, JsonDecoder, UId}
import com.wire.logging.Logging.warn
import com.wire.macros.logging.ImplicitTag._
import com.wire.network._
import com.wire.reactive.EventStream
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}
import com.wire.utils.ExponentialBackoff
import org.json.JSONObject

import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

class EventsClient(engine: ClientEngine, val backoff: ExponentialBackoff) {

  import EventsClient._

  implicit val dispatcher = new SerialDispatchQueue(name = "SingleRequestClient")

  val onPageLoaded = EventStream[LoadNotsResponse]()

  /**
    * NOTE: we should just drop any incoming triggers that occur while there is already a request underway.
    * There is no way a GCM notification can reach us before the end of the last request without that request
    * then coming back up-to-date. That then means that we can just ignore any duplicate triggers while there is a request.
    *
    * If we somehow get a GCM notification that is out-of-order with the last sync, worst case is that we send off
    * another request with the since id being the latest Id anyway, so we'll get back an empty list from BE. We could
    * alternatively check the timestamps to avoid this unnecessary request, but it's really not a big deal.
    */
  private var currentRequest = CancellableFuture.successful(Option.empty[UId], false)

  def loadNotifications(since: Option[UId], clientId: ClientId, pageSize: Int = 1000): CancellableFuture[Option[UId]] = {

    def loadNextPage(lastStableId: Option[UId], isFirstPage: Boolean, attempts: Int = 0): CancellableFuture[(Option[UId], Boolean)] =
      engine.fetch(RequestTag, Request.Get(notificationsPath(lastStableId, clientId, pageSize))) flatMap {
        case Response(status, _, _) if status.status == Response.Status.TimeoutCode =>
          if (attempts >= backoff.maxRetries) {
            CancellableFuture.failed(new Exception("Request timed out after too many retries"))
          } else {
            warn(s"Request from backend timed out: attempting to load last page since id: $lastStableId again")
            CancellableFuture.delay(backoff.delay(attempts))
              .flatMap(_ => loadNextPage(lastStableId, isFirstPage, attempts + 1)) //try last page again
          }

        case Response(status, _, PagedNotsResponse(ns, hasMore)) =>
          println(s"got somethign: $attempts, ns: $ns, hasMore: $hasMore")
          onPageLoaded ! LoadNotsResponse(ns, lastIdFound = !isFirstPage || status.isSuccess)

          if (hasMore) loadNextPage(ns.lastOption.map(_.id), isFirstPage = false)
          else CancellableFuture.successful(ns.lastOption.map(_.id), false)

        //TODO handle failing
        case _ =>
          println(s"other failure: $attempts")
          CancellableFuture.failed(new Exception("TODO - handle failing"))
      }

    currentRequest = if (currentRequest.isCompleted) loadNextPage(since, isFirstPage = true) else currentRequest
    currentRequest.map(_._1)
  }
}

case class PushNotification(id: UId, events: Seq[Event], transient: Boolean = false)

object PushNotification {
  implicit lazy val NotificationDecoder: JsonDecoder[PushNotification] = new JsonDecoder[PushNotification] {

    import com.wire.data.JsonDecoder._

    override def apply(implicit js: JSONObject) = PushNotification('id, array[Event](js.getJSONArray("payload")), 'transient)
  }
}

object EventsClient {
  /**
    * @param lastIdFound = whether the lastStableId was included in the first page of notifications received from the BE.
    *                   if not, the BE has probably already removed that history and we have to trigger a slow sync.
    */
  case class LoadNotsResponse(nots: Vector[PushNotification], lastIdFound: Boolean)

  def notificationsPath(since: Option[UId], client: ClientId, pageSize: Int) = {
    val args = Seq("since" -> since, "client" -> Some(client), "size" -> Some(pageSize)) collect { case (key, Some(v)) => key -> v }
    Request.query(NotificationsPath, args: _*)
  }

  val NotificationsPath = "/notifications"
  val RequestTag = "loadNotifications"

  object PagedNotsResponse {

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

