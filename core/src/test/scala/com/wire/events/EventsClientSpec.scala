/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH

 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
  package com.wire.events

import java.util.concurrent.CountDownLatch

import com.wire.data.{ClientId, UId}
import com.wire.events.EventsClient.{NotificationsPath, notificationsQuery}
import com.wire.network.Response.HttpStatus
import com.wire.network.{JsonObjectResponse, Request, Response, ZNetClient}
import com.wire.reactive.EventContext
import com.wire.testutils.{BackendResponses, FullFeatureSpec, RichLatch}
import com.wire.threading.{CancellableFuture, Threading}
import com.wire.utils.ExponentialBackoff

import scala.concurrent.Await
import scala.concurrent.duration._

class EventsClientSpec extends FullFeatureSpec {

  implicit val executionContext = Threading.Background
  implicit val eventContext = new EventContext {}

  var pageSize: Int = _
  var lastNot: Int = _
  var roundTime: Int = _

  val testBackoff = new ExponentialBackoff(1.second, 3.seconds)

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
    scenario("download one page of notifications up to the last stable id") {

      val pageSize = 5

      //generate a page of pageSize notifications with ids from 1 to pageSize, and indicate there are no more
      val nots = (1 to pageSize).map(i => BackendResponses.conversationOtrMessageAdd(notificationId = UId(i)))
      val jsonResponse = JsonObjectResponse(BackendResponses.notificationsPageResponse(hasMore = false, nots))

      val mockClientEngine = mock[ZNetClient]

      (mockClientEngine.apply[Unit] _)
        .expects(Request.Get(NotificationsPath, notificationsQuery(Some(UId(0)), Some(clientId), 1000)))
        .once()
        .returning(CancellableFuture {
          Response(HttpStatus(Response.Status.Success), body = jsonResponse)
        })

      val client = new EventsClient(mockClientEngine, testBackoff)

      val latch = new CountDownLatch(1)
      client.onPageLoaded { ns =>
        ns.nots should have size pageSize
        ns.lastIdFound shouldEqual true
        latch.countDown()
      }
      loadNotifications(client, Some(UId(0))) shouldEqual Some(UId(pageSize))
      latch.awaitDefault() shouldEqual true
    }

    scenario("download two pages of notifications up to the last stable id") {

      val pageSize = 5
      val totalToFetch = 10

      //generate two pages of pageSize notifications with ids from 1 to totalToFetch. Indicate there are more notifications to come after
      //the first page
      val nots = (1 to totalToFetch).map(i => BackendResponses.conversationOtrMessageAdd(notificationId = UId(i)))
      val jsonResponsePage1 = JsonObjectResponse(BackendResponses.notificationsPageResponse(hasMore = true, nots.take(pageSize)))
      val jsonResponsePage2 = JsonObjectResponse(BackendResponses.notificationsPageResponse(hasMore = false, nots.drop(pageSize)))

      val mockClientEngine = mock[ZNetClient]

      val expectedPathFirstPage = s"/notifications?since=${UId(0).str}&client=${clientId.str}&size=1000"
      val expectedPathSecondPage = s"/notifications?since=${UId(5).str}&client=${clientId.str}&size=1000"
      (mockClientEngine.apply[Unit] _)
        .expects(*)
        .twice()
        .onCall { req: Request[Unit] =>
          req match {
            case NotificationsRequestPath(`expectedPathFirstPage`) => CancellableFuture(Response(HttpStatus(Response.Status.Success), body = jsonResponsePage1))
            case NotificationsRequestPath(`expectedPathSecondPage`) => CancellableFuture(Response(HttpStatus(Response.Status.Success), body = jsonResponsePage2))
            case _ => CancellableFuture(Response(HttpStatus(Response.Status.Success)))
          }
        }

      val client = new EventsClient(mockClientEngine, testBackoff)

      val latch = new CountDownLatch(2)
      client.onPageLoaded { ns =>
        ns.nots should have size pageSize
        ns.lastIdFound shouldEqual true
        latch.countDown()
      }
      loadNotifications(client, Some(UId(0))) shouldEqual Some(UId(totalToFetch))
      latch.awaitDefault() shouldEqual true
    }
  }

  feature("Retry") {
    scenario("Retry on timeout after delay") {

      val testBackoff = new ExponentialBackoff(1.second, 1.second) //test depends on only one retry attempt

      val pageSize = 5
      //generate a page of pageSize notifications with ids from 1 to pageSize, and indicate there are no more
      val nots = (1 to pageSize).map(i => BackendResponses.conversationOtrMessageAdd(notificationId = UId(i)))
      val jsonResponse = JsonObjectResponse(BackendResponses.notificationsPageResponse(hasMore = false, nots))

      val mockClientEngine = mock[ZNetClient]

      val expectedPath = s"/notifications?since=${UId(0).str}&client=${clientId.str}&size=1000"
      var attempts = 0
      (mockClientEngine.apply[Unit] _)
        .expects(*)
        .twice()
        .onCall { req: Request[Unit] =>
          req match {
            case NotificationsRequestPath(`expectedPath`) if attempts == 0 =>
              attempts = 1
              CancellableFuture(Response(HttpStatus(Response.Status.TimeoutCode)))
            case NotificationsRequestPath(`expectedPath`) if attempts == 1 =>
              attempts = 2
              CancellableFuture(Response(HttpStatus(Response.Status.Success), body = jsonResponse))
            case _ => fail()
          }
        }

      val client = new EventsClient(mockClientEngine, testBackoff)

      val latch = new CountDownLatch(1)
      client.onPageLoaded { ns =>
        ns.nots should have size pageSize
        ns.lastIdFound shouldEqual true
        latch.countDown()
      }
      loadNotifications(client, Some(UId(0))) shouldEqual Some(UId(pageSize))
      attempts shouldEqual (testBackoff.maxRetries + 1)
      latch.awaitDefault() shouldEqual true
    }

    //TODO Dean - make exception meaningful
    scenario("Too many attempts should throw exception") {
      val mockClientEngine = mock[ZNetClient]

      var attempts = 0
      //continual timeouts from BE
      (mockClientEngine.apply[Unit] _)
        .expects(*)
        .anyNumberOfTimes()
        .onCall((req: Request[Unit]) => {
          attempts += 1
          println(s"attempts: $attempts")
          CancellableFuture(Response(HttpStatus(Response.Status.TimeoutCode)))
        })

      an [Exception] should be thrownBy loadNotifications(new EventsClient(mockClientEngine, testBackoff), Some(UId(0)))
      attempts shouldEqual (testBackoff.maxRetries + 1)
    }
  }

  feature("Multiple triggers") {
    scenario("Should reject request for second trigger while still processing first") {

    }
  }

  def loadNotifications(eventsClient: EventsClient, since: Option[UId])(implicit clientId: ClientId) =
    Await.result(eventsClient.loadNotifications(since, clientId), eventsClient.backoff.maxDelay * 2)

  object NotificationsRequestPath {
    def unapply(req: Request[_]): Option[String] = req match {
      case Request(Request.GetMethod, path, _, _, _, _, _, _, _, _, _, _, _) => path
      case _ => None
    }
  }
}
