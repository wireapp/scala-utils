package com.wire.network

import com.wire.auth.{Credentials, CredentialsHandler, DefaultLoginClient, EmailAddress}
import com.wire.config.BackendConfig
import com.wire.data.{AccountId, ClientId, UId}
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

class DefaultZNetClientTest extends FullFeatureSpec {


  scenario("ZNetClient test") {

    val ch = new CredentialsHandler {
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



      override val accessToken = Preference[Option[Token]](None, MockStorage.getToken, MockStorage.setToken)
      override val cookie      = Preference[Option[String]](None, MockStorage.getCookie, MockStorage.setCookie)
      override val userId      = AccountId("account123")
    }

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

    val config = BackendConfig("https://www.someurl.com")

    val client = new DefaultZNetClient(ch, mockAsync, config, new DefaultLoginClient(mockAsync, config))



    println(s"result: ${Await.result(client.apply(notificationsReq(Some(UId()), ClientId(), 1)).future, 5.seconds)}")

  }

  //and example request
  def notificationsReq(since: Option[UId], client: ClientId, pageSize: Int) = {
    val args = Seq("since" -> since, "client" -> Some(client), "size" -> Some(pageSize)) collect { case (key, Some(v)) => key -> v }
    Request.Get(Request.query("/notifications", args: _*))
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
