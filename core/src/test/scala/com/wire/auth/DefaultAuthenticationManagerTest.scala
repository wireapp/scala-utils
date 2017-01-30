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

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class DefaultAuthenticationManagerTest extends FullFeatureSpec {


  scenario("Let's get started") {

    var updatecount = 0
    var savedToken = Option(Token("token0", "token", Instant.now))
    var savedCookie = Option("cookie0")

    val mockAync = mock[AsyncClient]
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

    (mockAync.apply _)
      .expects(*, *, *, *, *, *, *, *)
//      .twice()
      .once()
      .returning(CancellableFuture {
        updatecount += 1
        Response(HttpStatus(Response.Status.Success), returnHeaders(updatecount), returnBody(updatecount))
      }(new SerialDispatchQueue(name = "TestAsyncClient")))

    val authManager = new DefaultAuthenticationManager(new DefaultLoginClient(mockAync, BackendConfig("https://www.someurl.com")), new CredentialsHandler {
      override def credentials = new Credentials {
        override def maybeEmail = Some(EmailAddress("test@test.com"))
        override def maybeUsername = None
        override def addToLoginJson(o: JSONObject) = ()
        override def maybePhone = None
        override def canLogin = true
        override def autoLoginOnRegistration = true
        override def addToRegistrationJson(o: JSONObject) = ()
        override def maybePassword = Some("password")
      }

      private implicit val dispatcher = new SerialDispatchQueue(name = "DatabaseQueue")

      private def delay[A](f: => A) = Future {
        Thread.sleep(500)
        f
      }

      override val accessToken = Preference[Option[Token]](None, delay(savedToken), {t => delay(savedToken = t)})
      override val cookie      = Preference[Option[String]](None, delay(savedCookie), {c => delay(savedCookie = c)})
      override val userId      = AccountId("account123")
    })


    Await.result ({
      authManager.currentToken()
//      authManager.currentToken()
    }, 5.seconds)

    Thread.sleep(2000)

    println(s"token: $savedToken")
    println(s"cookie: $savedCookie")

  }


}
