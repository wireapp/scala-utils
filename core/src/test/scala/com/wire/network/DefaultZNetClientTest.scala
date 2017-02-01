package com.wire.network

import java.io.{File, PrintWriter}

import com.wire.auth.{Credentials, CredentialsHandler, DefaultLoginClient, EmailAddress}
import com.wire.config.BackendConfig
import com.wire.data.AccountId
import com.wire.events.EventsClient
import com.wire.macros.returning
import com.wire.network.AccessTokenProvider.Token
import com.wire.network.Response.{DefaultHeaders, HttpStatus}
import com.wire.storage.Preference
import com.wire.testutils.FullFeatureSpec
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}
import com.wire.utils.RichInstant
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

class DefaultZNetClientTest extends FullFeatureSpec {

  val credentials = new CredentialsHandler {
    override def credentials = new Credentials {
      override val email = Some(EmailAddress("dean+1@wire.com"))
      override val password = None//Some("aqa123456")
    }

    private implicit val dispatcher = new SerialDispatchQueue(name = "DatabaseQueue")

    override val accessToken = Preference[Option[Token]](None, MemMockStorage.getToken, MemMockStorage.setToken)
    override val cookie      = Preference[Option[String]](None, MemMockStorage.getCookie, MemMockStorage.setCookie)
    override val userId      = AccountId("77cc30d6-8790-4258-bf04-b2bfdcd9642a")
  }

  val config = BackendConfig.StagingBackend

  scenario("ZNetClient test") {

    val newCookie = "newCookie"
    val newToken = "newToken"

    val mockAsync = mock[AsyncClient]
    val newCookieHeaders = DefaultHeaders(Map(
      "set-cookie" -> s"zuid=$newCookie"
    ))

    val newTokenBody = JsonObjectResponse(new JSONObject(
      s"""
         |{
         |  "expires_in": 86400,
         |  "access_token": "$newToken",
         |  "token_type": "Bearer"
         |}
      """.stripMargin
    ))

    val invalidBody = JsonObjectResponse(new JSONObject(
      """
        |{
        |   "code":403,
        |   "message":"unknown user token",
        |   "label":"invalid-credentials"
        |}
      """.stripMargin
    ))

    var callCount = 0
    (mockAsync.apply _)
      .expects(*, *, *, *, *, *, *, *)
      .anyNumberOfTimes()
      .onCall { (uri, method, body, headers, followRedirect, timeout, decoder, downloadProgressCallback)  =>
        println(s"${callCount + 1}th call made")

        val response = callCount match {
          case 0 => Response(HttpStatus(Response.Status.Success), newCookieHeaders, newTokenBody)
          case _ =>
            val token = headers.get("Authorization")
            val cookie = headers.get("Cookie")

            val validToken = token.contains(s"Bearer $newToken")
            val validCookie = cookie.forall(_ == newCookie)

            if (validToken && validCookie) {
              println("Everything was solid")
              Response(HttpStatus(Response.Status.Success))
            } else {
              if (!validToken) println(s"${callCount + 1}th request had wrong token: $token")
              if (cookie.isDefined && !validCookie) println(s"${callCount + 1}th incorrect cookie: $cookie")
              Response(HttpStatus(Response.Status.Forbidden), body = invalidBody)
            }
        }

        callCount += 1
        CancellableFuture {
          Thread.sleep(500)
          response
        }(new SerialDispatchQueue(name = "TestAsyncClient"))
      }

    val client = new DefaultZNetClient(credentials, mockAsync, config, new DefaultLoginClient(mockAsync, config))

    import com.wire.threading.Threading.Implicits.Background
    val res = Future.sequence(Seq(
      client.apply(Request.Get(EventsClient.NotificationsPath, EventsClient.notificationsQuery(None, None, 100))).future,
      client.apply(Request.Get(EventsClient.NotificationsPath, EventsClient.notificationsQuery(None, None, 100))).future
    ))

    println(s"result: ${Await.result(res, 10.seconds)}")

    println(s"token: ${Await.result(MemMockStorage.getToken, 10.seconds)}")
    println(s"cookie: ${Await.result(MemMockStorage.getCookie, 10.seconds)}")

    Thread.sleep(4000)
  }

  object MemMockStorage {
    private implicit val dispatcher = new SerialDispatchQueue(name = "StorageQueue")

    private var savedToken = Option(Token("oldToken", "Bearer", Instant.now  + 10.seconds))
    private var savedCookie = Option("oldCookie")

    private def delay[A](f: => A) = Future {
      Thread.sleep(500)
      f
    }

    def getToken = delay(savedToken)
    def getCookie = delay(savedCookie)

    def setToken(token: Option[Token]) = delay(savedToken = token)
    def setCookie(cookie: Option[String]) = delay(savedCookie = cookie)

  }

  object FileMockStorage {

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

    def clear = {
      setToken(None)
      setCookie(None)
    }
  }

  scenario("Test mock storage") {
    FileMockStorage.setCookie(Some("test cookie: alkdfj"))
    FileMockStorage.setToken(Some(Token("token", "type", Instant.now)))

    println(Await.result(FileMockStorage.getCookie, 5.seconds))
    println(Await.result(FileMockStorage.getToken, 5.seconds))
  }

  scenario("Test with actual backend") { //useful for collecting real-world responses

    val async = new ApacheHTTPAsyncClient()
    val znet = new DefaultZNetClient(credentials, async, config, new DefaultLoginClient(async, config))

    Await.result(znet(Request.Get(EventsClient.NotificationsPath, EventsClient.notificationsQuery(None, None, 100))).future, 5.seconds)

  }

}
