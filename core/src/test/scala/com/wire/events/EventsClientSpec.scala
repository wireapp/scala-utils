package com.wire.events

import java.util.concurrent.CountDownLatch

import com.wire.data.{ClientId, UId}
import com.wire.network.Response.HttpStatus
import com.wire.network.{ClientEngine, JsonObjectResponse, Request, Response}
import com.wire.reactive.EventContext
import com.wire.testutils.Matchers.FutureSyntax
import com.wire.testutils.{BackendResponses, FullFeatureSpec, RichLatch, jsonFrom}
import com.wire.threading.{CancellableFuture, Threading}

class EventsClientSpec extends FullFeatureSpec {

  implicit val executionContext = Threading.Background
  implicit val eventContext = new EventContext {}

  var pageSize: Int = _
  var lastNot: Int = _
  var roundTime: Int = _

  implicit val clientId = ClientId("123")

  before {
    //reset values
    pageSize = 25
    lastNot = 100
    roundTime = 100

    eventContext.onContextStart()
  }

  after {
    eventContext.onContextStop()
  }

  feature("Download notifications after one trigger") {
    scenario("download last page of notifications") {

      val lastStableId = UId(1)
      val pageSize = 25

      //generate a page of pageSize notifications with ids from 1 to pageSize, and indicate there are no more
      val nots = (1 to pageSize).map(i => BackendResponses.conversationOtrMessageAdd(notificationId = UId(i)))
      val jsonResponse = JsonObjectResponse(BackendResponses.notificationsPageResponse(hasMore = false, nots))

      val mockClientEngine = mock[ClientEngine]

      (mockClientEngine.fetch[Unit] _)
        .expects(EventsClient.RequestTag, Request.Get(EventsClient.notificationsPath(Some(lastStableId), clientId, 1000)))
        .once()
        .returning(CancellableFuture {
          Response(HttpStatus(Response.Status.Success), body = jsonResponse)
        })

      val client = new EventsClient(mockClientEngine)

      val latch = new CountDownLatch(1)
      client.onPageLoaded { ns =>
        ns.nots should have size pageSize
        ns.lastIdFound shouldEqual true
        latch.countDown()
      }
      loadNotifications(client, Some(lastStableId)).await() shouldEqual Some(UId(pageSize))
      latch.awaitDefault() shouldEqual true
    }

    scenario("Download a handful of notifications less than the full page size") {
      val historyToFetch = 3
      clientTest(expectedPages = 1,
        pagesTest = { (ns, _) =>
          ns.size shouldEqual historyToFetch
        },
        body = loadNotifications(_, Some(UId(lastNot - historyToFetch))).await() shouldEqual Some(UId(lastNot))
      )
    }

    scenario("download last two pages of notifications") {
      clientTest(expectedPages = 2,
        pagesTest = { (ns, _) =>
          ns should have size pageSize
        },
        body = loadNotifications(_, Some(UId(lastNot - pageSize * 2))).await() shouldEqual Some(UId(lastNot))
      )
    }

    scenario("Download all notifications available since just before last page") {
      val historyToFetch = pageSize + 3
      clientTest(expectedPages = 2,
        pagesTest = { (ns, pageNumber) =>
          ns.size shouldEqual (if (pageNumber == 1) pageSize else historyToFetch - pageSize)
        },
        body = loadNotifications(_, Some(UId(lastNot - historyToFetch))).await() shouldEqual Some(UId(lastNot))
      )
    }

    scenario("download all available pages of notifications") {
      clientTest(expectedPages = lastNot / pageSize,
        pagesTest = { (ns, _) =>
          ns should have size pageSize
        },
        body = loadNotifications(_, None).await() shouldEqual Some(UId(lastNot))
      )
    }
  }

  feature("Multiple triggers") {
    scenario("Should reject request for second trigger while still processing first") {

      //We only expect one page, because the second request will be ignored
      clientTest(expectedPages = 1,
        pagesTest = { (ns, _) =>
          //But we should still get the second notification that came through to BE while it's processing our first request
          ns.size shouldEqual 2
        },
        body = { client =>
          loadNotifications(client, Some(UId(lastNot - 1)))

          //BE receives a new message sent to our client
          //          testEngine.newHistory(1)

          //while request is processing, we receive some delayed trigger
          loadNotifications(client, Some(UId(lastNot - 1))).await() shouldEqual Some(UId(lastNot + 1))
        })
    }
  }

  def loadNotifications(eventsClient: EventsClient, since: Option[UId])(implicit clientId: ClientId) =
    eventsClient.loadNotifications(since, clientId)


  def clientTest(expectedPages: Int, pagesTest: (Seq[PushNotification], Int) => Unit, body: EventsClient => Unit): Unit = {

    val mockClientEngine = mock[ClientEngine]

    //    (mockClientEngine.fetch[Unit] _).expects(EventsClient.RequestTag, Request.Get(EventsClient.notificationsPath()))

    val client = new EventsClient(mockClientEngine)

    val latch = new CountDownLatch(expectedPages)
    client.onPageLoaded { ns =>
      pagesTest(ns.nots, expectedPages - latch.getCount.toInt + 1)
      latch.countDown()
    }
    body(client)
    latch.awaitDefault() shouldEqual true
  }

  //  class TestClientEngine extends ClientEngine {
  //
  //    @volatile private var history = (1 to lastNot) map (i => PushNotification(Uid(i.toString)))
  //
  //    override def fetch[A](r: Request[A]): CancellableFuture[Response] =
  //      CancellableFuture.successful(Response(Response.Cancelled))
  //
  //    override def withErrorHandling[A, T](name: String, r: Request[A])(pf: PartialFunction[Response, T])(implicit ec: ExecutionContext): ErrorOrResponse[T] =
  //      CancellableFuture.successful(Left(ErrorResponse(-1, "", "")))
  //
  //
  //    override def chainedFutureWithErrorHandling[A, T](name: String, r: Request[A])(pf: PartialFunction[Response, ErrorOr[T]])(implicit ec: ExecutionContext): ErrorOr[T] = {
  //      r.
  //    }
  //
  //    private def fetch(ind: Int) = {
  //      Future(Right {
  //        Thread.sleep(roundTime)
  //        //Response is calculated at the end of the delay, as triggers can't be generated and received from the BE
  //        //before the BE would know that the last message has moved forward
  //        Response(history.slice(ind, ind + pageSize), ind + pageSize < lastNot)
  //      })
  //    }
  //
  //    def newHistory(count: Int) = history ++= ((history.size + 1) to (history.size + count)) map (i => PushNotification(Uid(i.toString)))
  //
  //  }

}
