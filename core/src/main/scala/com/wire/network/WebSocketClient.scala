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
import java.nio.ByteBuffer
import java.util
import javax.websocket.ClientEndpointConfig.Configurator
import javax.websocket.MessageHandler.Whole
import javax.websocket._

import com.wire.logging.Logging.{debug, error}
import com.wire.macros.logging.ImplicitTag._
import com.wire.network.ContentEncoder.{BinaryRequestContent, EmptyRequestContent}
import com.wire.reactive.{EventStream, Signal}
import com.wire.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import org.glassfish.tyrus.client.ClientManager
import org.json.JSONObject

import scala.collection.JavaConverters._
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Try

trait WebSocketClient {

  def connected: Signal[Boolean]

  def onError: EventStream[Exception]

  def onMessage: EventStream[ResponseContent]

  def send[A: ContentEncoder](msg: A): CancellableFuture[Unit]

  def close(): CancellableFuture[Unit]

  def ping(msg: String): CancellableFuture[Boolean]

}


class TyrusWebSocketClient(serverUri: URI, accessTokenProvider: AccessTokenProvider) extends WebSocketClient { self =>

  private implicit val dispatcher = new SerialDispatchQueue(Threading.ThreadPool)

  override val connected = Signal(false)
  override val onError   = EventStream[Exception]()
  override val onMessage = EventStream[ResponseContent]()

  private val onPong    = EventStream[String]()

  private var init: CancellableFuture[RemoteEndpoint.Basic] = connect()
  private var closed = false

  private def connect() = accessTokenProvider.currentToken().flatMap {
    case Right(token) if closed => CancellableFuture.failed(new Exception("WebSocket client closed"))
    case Right(token) =>
      val p = Promise[RemoteEndpoint.Basic]()
      debug(s"Sending webSocket request: $serverUri")

      val config = createEndpointConfig(token.headers ++ Map(
        "Accept-Encoding" -> "identity" // XXX: this is a hack for Backend In The Box problem: 'Accept-Encoding: gzip' header causes 500
//        "User-Agent", client.userAgent
      ))

      val client = ClientManager.createClient()

      client.connectToServer(new Endpoint {
        override def onOpen(session: Session, config: EndpointConfig): Unit = {
          p.tryComplete(Try(session.getBasicRemote))
        }
        override def onClose(session: Session, closeReason: CloseReason): Unit = {
          closed = true
        }
      }, config, serverUri)

      new CancellableFuture(p).withTimeout(30.seconds)
    case Left(status) =>
      CancellableFuture.failed(new Exception(s"Authentication returned error status: $status"))
  }

  private def onConnected(session: Session): Unit = {
    session.addMessageHandler(new Whole[String] {
      override def onMessage(message: String): Unit = {
        self.onMessage ! Try(JsonObjectResponse(new JSONObject(message))).getOrElse(StringResponse(message))
      }
    })

    session.addMessageHandler(new Whole[PongMessage] {
      override def onMessage(message: PongMessage): Unit = {
        self.onPong ! new String(message.getApplicationData.array(), "utf8")
      }
    })

    session.addMessageHandler(new Whole[ByteBuffer] {
      override def onMessage(bb: ByteBuffer): Unit = {
        self.onMessage ! Try(JsonObjectResponse(new JSONObject(new String(bb.array, "utf8")))).getOrElse(BinaryResponse(bb.array, ""))
      }
    })

    session.getBasicRemote
  }

  private def createEndpointConfig(headers: Map[String, String]) = ClientEndpointConfig.Builder.create().configurator(new Configurator() {
    override def beforeRequest(defHeaders: util.Map[String, util.List[String]]): Unit = {
      headers.foreach { case (key, value) => defHeaders.put(key, List(value).asJava) }
    }
  }).build()

  override def send[A: ContentEncoder](msg: A): CancellableFuture[Unit] = init flatMap { s =>
    implicitly[ContentEncoder[A]].apply(msg) match {
      case EmptyRequestContent =>
        error(s"Sending EmptyRequest with webSocket for msg: '$msg'")
        s.sendText("")
        CancellableFuture.successful({})
      case BinaryRequestContent(data, _) =>
        s.sendBinary(ByteBuffer.wrap(data))
        CancellableFuture.successful({})
      case req =>
        throw new UnsupportedOperationException(s"Unsupported request content: $req")
    }
  }
  override def close(): CancellableFuture[Unit] = ???
  override def ping(msg: String): CancellableFuture[Boolean] = ???
}
