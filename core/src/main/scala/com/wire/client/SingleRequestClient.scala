package com.wire.client

import com.wire.client.ClientEngine.{ErrorOr, ErrorResponse, Request, Response}
import com.wire.client.SingleRequestClient.NotId
import com.wire.events.EventStream
import com.wire.logging.Logging
import com.wire.macros.logging.ImplicitTag._
import com.wire.threading.SerialDispatchQueue

import scala.concurrent.Future

class SingleRequestClient(client: ClientEngine) {

  implicit val dispatcher = new SerialDispatchQueue(name = "SingleRequestClient")

  val onPageLoaded = EventStream[Seq[NotId]]()

  /**
    * NOTE: we should just drop any incoming triggers that occur while there is already a request underway.
    * There is no way a GCM notification can reach us before the end of the last request without that request
    * then coming back up-to-date. That then means that we can just ignore any duplicate triggers while there is a request.
    *
    * If we somehow get a GCM notification that is out-of-order with the last sync, worst case is that we send off
    * another request with the since id being the latest Id anyway, so we'll get back an empty list from BE. We could
    * alternatively check the timestamps to avoid this unnecessary request, but it's really not a big deal.
    */
  private var currentRequest = Future.successful((Seq.empty[NotId], false))

  def loadNotifications(lastStableId: Option[NotId]): Future[Option[NotId]] = {

    def loadNextPage(lastStableId: Option[NotId]): Future[(Seq[NotId], Boolean)] = {
      client.fetch(Request(since = lastStableId)).flatMap {
        case Right(Response(ns, hasMore)) =>
          onPageLoaded ! ns
          if (hasMore) loadNextPage(ns.lastOption)
          else Future.successful(ns, false)
        case Left(ErrorResponse(code)) =>
          Logging.error(s"Load notifications failed with response: $code")
          Future.successful(Seq.empty, false)
      }
    }

    currentRequest = if (currentRequest.isCompleted) loadNextPage(lastStableId).flatMap {
      case (ns, hasMore) =>
        if (hasMore) loadNextPage(ns.lastOption)
        else Future.successful(ns, false)
    }
    else currentRequest

    currentRequest.map { case (ns, _) => ns.lastOption }
  }
}

object SingleRequestClient {

  case class NotId(str: String)

  object NotId {
    def apply(int: Int): NotId = NotId(int.toString)
  }

}

trait ClientEngine {
  def fetch(r: Request): ErrorOr[Response]
}


object ClientEngine {
  type ErrorOr[A] = Future[Either[ErrorResponse, A]]

  case class Request(since: Option[NotId])

  case class Response(ns: Seq[NotId], hasMore: Boolean)

  case class ErrorResponse(code: Int)

}
