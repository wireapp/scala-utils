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
  package com.wire.network

import java.net.URI
import java.util.concurrent.CountDownLatch

import com.wire.data.ClientId
import com.wire.network.AccessTokenProvider.Token
import com.wire.network.Response.Status
import com.wire.testutils.FullFeatureSpec
import com.wire.threading.CancellableFuture
import org.threeten.bp.Instant
import com.wire.utils.RichInstant

import scala.concurrent.Await
import scala.concurrent.duration._

class WebSocketClientSpec extends FullFeatureSpec {


  scenario("Connect to awesome echo server: ws://echo.websocket.org/") {

    val clientId = ClientId("fd0e9652D93CCE27") //dean test 2 on nexus 5

//    val uri = new URI(s"wss://staging-nginz-ssl.zinfra.io/await?client=${clientId.str}")

    val uri = new URI("ws://echo.websocket.org/")

    val latch = new CountDownLatch(2)
    val ws = new TyrusWebSocketClient(uri, new AccessTokenProvider {
      override def currentToken(): CancellableFuture[Either[Status, Token]] = CancellableFuture.successful(Right(Token("Bearer", "abc", Instant.now + 1.minute)))
    })

    println(Await.result(ws.send("Test"), 10.seconds))

  }
}
