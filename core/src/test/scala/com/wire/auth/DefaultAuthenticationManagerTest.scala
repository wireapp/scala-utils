package com.wire.auth

import com.wire.config.BackendConfig
import com.wire.data.AccountId
import com.wire.network.AccessTokenProvider.Token
import com.wire.network.{AsyncClient, JsonObjectResponse, Response}
import com.wire.network.Response.{DefaultHeaders, HttpStatus}
import com.wire.storage.Preference
import com.wire.testutils.FullFeatureSpec
import com.wire.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import org.json.JSONObject
import org.threeten.bp.Instant
import com.wire.utils.RichInstant

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class DefaultAuthenticationManagerTest extends FullFeatureSpec {


  scenario("Let's get started") {

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

    val authManager = new DefaultAuthenticationManager(new DefaultLoginClient(mockAsync, BackendConfig("https://www.someurl.com")), new CredentialsHandler {
      override def credentials = new Credentials {
        override val email = Some(EmailAddress("test@test.com"))
        override val password = Some("password")
      }

      private implicit val dispatcher = new SerialDispatchQueue(name = "DatabaseQueue")

      override val accessToken = Preference[Option[Token]](None, MockStorage.getToken, MockStorage.setToken)
      override val cookie      = Preference[Option[String]](None, MockStorage.getCookie, MockStorage.setCookie)
      override val userId      = AccountId("account123")
    })

    Await.result ({
      import Threading.Implicits.Ui
      Future.sequence(Seq(authManager.currentToken(), authManager.currentToken()))
    }, 10.seconds).foreach { res =>
      println(s"Current token: $res")
    }

    Thread.sleep(5000)

    println(s"token: ${Await.result(MockStorage.getToken, 5.seconds)}")
    println(s"cookie: ${Await.result(MockStorage.getCookie, 5.seconds)}")

  }

  object MockStorage {
    private implicit val dispatcher = new SerialDispatchQueue(name = "StorageQueue")

    private var savedToken = Option(Token("token0", "token", Instant.now  + 10.seconds))
    private var savedCookie = Option("cookie0")

    private def delay[A](f: => A) = Future {
      Thread.sleep(200)
      f
    }

    def getToken = delay(savedToken)
    def getCookie = delay(savedCookie)

    def setToken(token: Option[Token]) = delay(savedToken = token)
    def setCookie(cookie: Option[String]) = delay(savedCookie = cookie)

  }

}

