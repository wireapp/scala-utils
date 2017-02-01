package com.wire.network

import java.io.{File, PrintWriter}

import com.wire.auth.{Credentials, CredentialsHandler, DefaultLoginClient, EmailAddress}
import com.wire.config.BackendConfig
import com.wire.data.{AccountId, ClientId, UId}
import com.wire.events.EventsClient
import com.wire.macros.returning
import com.wire.network.AccessTokenProvider.Token
import com.wire.network.Response.{DefaultHeaders, HttpStatus}
import com.wire.storage.Preference
import com.wire.testutils.FullFeatureSpec
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import com.wire.utils.RichInstant

import scala.util.Try

class DefaultZNetClientTest extends FullFeatureSpec {

  val credentials = new CredentialsHandler {
    override def credentials = new Credentials {
      override val email = Some(EmailAddress("dean+1@wire.com"))
      override val password = Some("aqa123456")
    }

    private implicit val dispatcher = new SerialDispatchQueue(name = "DatabaseQueue")

    override val accessToken = Preference[Option[Token]](None, MockStorage.getToken, MockStorage.setToken)
    override val cookie      = Preference[Option[String]](None, MockStorage.getCookie, MockStorage.setCookie)
    override val userId      = AccountId("77cc30d6-8790-4258-bf04-b2bfdcd9642a")
  }

  val config = BackendConfig.StagingBackend

  scenario("ZNetClient test") {

    val mockAsync = mock[AsyncClient]
    def returnHeaders(count: Int) = DefaultHeaders(Map(
      "content-encoding"                 -> "gzip",
      "request-id"                       -> "7DXR2gKdyyHPcadmGsbJnx",
      "access-control-allow-credentials" -> "true",
      "server"                           -> "nginx",
      "access-control-expose-headers"    -> "Request-Id, Location",
      "date"                             -> "Sat, 28 Jan 2017 14:15:20 GMT",
      "content-length"                   -> "229",
      "connection"                       -> "keep-alive",
      "set-cookie"                       -> s"zuid=cookie$count; Path=/access; Expires=Sat, 08-Apr-2017 14:15:20 GMT; Domain=wire.com; HttpOnly; Secure",
      "content-type"                     -> "application/json",
      "strict-transport-security"        -> "max-age=31536000; preload"
    ))

    def returnBody(count: Int) = JsonObjectResponse(new JSONObject(
      s"""
         |{
         |  "expires_in": 86400,
         |  "access_token": "token$count",
         |  "token_type": "token"
         |}
      """.stripMargin
    ))

    (mockAsync.apply _)
      .expects(*, *, *, *, *, *, *, *)
      .anyNumberOfTimes()
      .returning(CancellableFuture {
        Response(HttpStatus(Response.Status.Success), returnHeaders(1), returnBody(1))
      }(new SerialDispatchQueue(name = "TestAsyncClient")))

    val client = new DefaultZNetClient(credentials, mockAsync, config, new DefaultLoginClient(mockAsync, config))

    println(s"result: ${Await.result(client.apply(Request.Get(EventsClient.NotificationsPath, EventsClient.notificationsQuery(None, None, 100))).future, 5.seconds)}")

  }

  object MockStorage {

    private implicit val dispatcher = new SerialDispatchQueue(name = "StorageQueue")

    private val storageLoc = "resources/user.txt"

    private val cookieFile = returning(new File("core/src/test/resources/cookie.txt"))(_.createNewFile())
    private val tokenFile = returning(new File("core/src/test/resources/token.txt"))(_.createNewFile())

    def printToFile(f: File)(op: PrintWriter => Unit) = Future {
      val p = new PrintWriter(f)
      try op(p) finally p.close()
    }

    def getToken = Future(Try(Token.Decoder(new JSONObject(scala.io.Source.fromFile(tokenFile).mkString))).toOption)
    def getCookie = Future(Try(scala.io.Source.fromFile(cookieFile).mkString).toOption.filter(_.nonEmpty))

    def setToken(token: Option[Token]) = token match {
      case Some(t) => printToFile(tokenFile) { p => p.print(Token.Encoder(t).toString) }
      case None => printToFile(tokenFile) {_.print("")}
    }

    def setCookie(cookie: Option[String]) = cookie match {
      case Some(c) => printToFile(cookieFile) { p => p.print(c) }
      case None => printToFile(cookieFile) {_.print("")}
    }
  }

  scenario("Test mock storage") {
    MockStorage.setCookie(Some("test cookie: alkdfj"))
    MockStorage.setToken(Some(Token("token", "type", Instant.now)))

    println(Await.result(MockStorage.getCookie, 5.seconds))
    println(Await.result(MockStorage.getToken, 5.seconds))
  }

  scenario("Test with actual backend") { //useful for collecting real-world responses

    val async = new ApacheHTTPAsyncClient()
    val znet = new DefaultZNetClient(credentials, async, config, new DefaultLoginClient(async, config))

    Await.result(znet(Request.Get(EventsClient.NotificationsPath, EventsClient.notificationsQuery(None, None, 100))).future, 5.seconds)

  }

}
