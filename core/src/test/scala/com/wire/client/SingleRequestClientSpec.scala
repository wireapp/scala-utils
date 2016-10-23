package com.wire.client

import java.util.concurrent.CountDownLatch

import com.wire.client.ClientEngine.{ErrorOr, ErrorResponse, Request, Response}
import com.wire.client.SingleRequestClient.NotId
import com.wire.events.EventContext
import com.wire.testutils.Implicits.RichLatch
import com.wire.testutils.Matchers.FutureSyntax
import com.wire.testutils.TestSpec
import com.wire.threading.Threading

import scala.concurrent.Future

class SingleRequestClientSpec extends TestSpec {

  implicit val executionContext = Threading.Background
  implicit val eventContext = new EventContext {}

  var pageSize: Int = _
  var lastNot: Int = _
  var roundTime: Int = _

  var testEngine: TestClientEngine = _

  before {
    //reset values
    pageSize = 25
    lastNot = 100
    roundTime = 100

    testEngine = new TestClientEngine
    eventContext.onContextStart()
  }

  after {
    eventContext.onContextStop()
  }

  feature("Download notifications after one trigger") {
    scenario("download last page of notifications") {
      clientTest(expectedPages = 1,
        pagesTest = { (ns, _) =>
          ns should have size pageSize
        },
        body = { client =>
          client.loadNotifications(Some(NotId(lastNot - pageSize))).await() shouldEqual Some(NotId(lastNot))
        })
    }

    scenario("Download a handful of notifications less than full page size") {
      val historyToFetch = 3
      clientTest(expectedPages = 1,
        pagesTest = { (ns, _) =>
          ns.size shouldEqual historyToFetch
        },
        body = { client =>
          client.loadNotifications(Some(NotId(lastNot - historyToFetch))).await() shouldEqual Some(NotId(lastNot))
        })
    }

    scenario("download last two pages of notifications") {
      clientTest(expectedPages = 2,
        pagesTest = { (ns, _) =>
          ns should have size pageSize
        },
        body = { client =>
          client.loadNotifications(Some(NotId(lastNot - pageSize * 2))).await() shouldEqual Some(NotId(lastNot))
        })
    }

    scenario("Download all notifications available since just before last page") {
      val historyToFetch = pageSize + 3
      clientTest(expectedPages = 2,
        pagesTest = { (ns, pageNumber) =>
          ns.size shouldEqual (if (pageNumber == 1) pageSize else historyToFetch - pageSize)
        },
        body = { client =>
          client.loadNotifications(Some(NotId(lastNot - historyToFetch))).await() shouldEqual Some(NotId(lastNot))
        })
    }

    scenario("download all available pages of notifications") {
      clientTest(expectedPages = lastNot / pageSize,
        pagesTest = { (ns, _) =>
          ns should have size pageSize
        },
        body = { client =>
          client.loadNotifications(None).await() shouldEqual Some(NotId(lastNot))
        })
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
          client.loadNotifications(Some(NotId(lastNot - 1)))

          //BE receives a new message sent to our client
          testEngine.newHistory(1)

          //while request is processing, we receive some delayed trigger
          client.loadNotifications(Some(NotId(lastNot - 1))).await() shouldEqual Some(NotId(lastNot + 1))
        })
    }
  }

  def clientTest(expectedPages: Int, pagesTest: (Seq[NotId], Int) => Unit, body: SingleRequestClient => Unit): Unit = {
    val client = new SingleRequestClient(testEngine)

    val latch = new CountDownLatch(expectedPages)
    client.onPageLoaded { ns =>
      pagesTest(ns, expectedPages - latch.getCount.toInt + 1)
      latch.countDown()
    }
    body(client)
    latch.awaitDefault() shouldEqual true
  }

  class TestClientEngine extends ClientEngine {

    @volatile private var history = (1 to lastNot) map (NotId(_))

    override def fetch(r: Request): ErrorOr[Response] = r match {
      case Request(None) => fetch(0)
      case Request(Some(nId)) if history.contains(nId) =>
        //+ 1 because the user tells us SINCE nId
        fetch(history.indexOf(nId) + 1)
      case _ => Future(Left(ErrorResponse(1)))
    }

    private def fetch(ind: Int) = {
      Future(Right {
        Thread.sleep(roundTime)
        //Response is calculated at the end of the delay, as triggers can't be generated and received from the BE
        //before the BE would know that the last message has moved forward
        Response(history.slice(ind, ind + pageSize), ind + pageSize < lastNot)
      })
    }

    def newHistory(count: Int) = history ++= ((history.size + 1) to (history.size + count)) map (NotId(_))
  }

}
