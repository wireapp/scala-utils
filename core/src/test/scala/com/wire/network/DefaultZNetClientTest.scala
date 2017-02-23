package com.wire.network

import java.io.File

import com.wire.accounts.AccountData.AccountDataDao
import com.wire.accounts.{AccountData, AccountStorage}
import com.wire.auth.AuthenticationManager.Cookie
import com.wire.auth.Credentials.EmailCredentials
import com.wire.auth._
import com.wire.config.BackendConfig
import com.wire.data.{AccountId, UserId}
import com.wire.db.SQLiteDatabase
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

class DefaultZNetClientTest extends FullFeatureSpec {

  scenario("ZNetClient test") {

    val oldCookie = "oldCookie"
    val oldToken = "oldToken"

    val newCookie = "newCookie"
    val newToken = "newToken"

    object MemMockStorage {
      private implicit val dispatcher = new SerialDispatchQueue()("StorageQueue")

      @volatile private var savedToken = Option(Token(oldToken, "Bearer", Instant.now + 10.seconds))
      @volatile private var savedCookie = Option(Cookie(oldCookie))

      private def delay[A](f: => A) = Future {
        Thread.sleep(500)
        f
      }

      def getToken = Future(savedToken)

      def getCookie = delay(savedCookie)

      def setToken(token: Option[Token]) = delay(savedToken = token)

      def setCookie(cookie: Option[Cookie]) = delay(savedCookie = cookie)

    }

    val credentials = new CredentialsHandler {
      override def credentials = EmailCredentials(Some(EmailAddress("dean+1@wire.com")))

      private implicit val dispatcher = new SerialDispatchQueue()("DatabaseQueue")

      override val accessToken = Preference[Option[Token]](None, MemMockStorage.getToken, MemMockStorage.setToken)
      override val cookie = Preference[Option[Cookie]](None, MemMockStorage.getCookie, MemMockStorage.setCookie)
      override val userId = AccountId("77cc30d6-8790-4258-bf04-b2bfdcd9642a")
    }

    val config = BackendConfig.StagingBackend

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

    val emptyNotifications = JsonObjectResponse(new JSONObject(
      """
        |{
        |  "time": "2017-02-14T13:39:45Z",
        |  "has_more": false,
        |  "notifications": []
        |}
      """.stripMargin
    ))

    var accessCalled = false
    var callCount = 0
    (mockAsync.apply _)
      .expects(*, *, *, *, *, *, *, *)
      .anyNumberOfTimes()
      .onCall { (uri, method, body, headers, followRedirect, timeout, decoder, downloadProgressCallback)  =>
        println(s"${callCount + 1}th call made to endpoint: ${uri.getPath} ${uri.getQuery}")

        def assertTokenAndCookie(token: String, cookie: String) = {
          println(s"Call made with ${headers.get("Authorization")} and ${headers.get("Cookie")}")
          assert(headers.get("Authorization").contains(s"Bearer $token") && headers.get("Cookie").forall(_.contains(cookie)), "Incorrect cookie or token")
        }

        assertTokenAndCookie(if (accessCalled) newToken else oldToken, if (accessCalled) newCookie else oldCookie)

        val response = uri.getPath match {
          case LoginClient.AccessPath if !accessCalled =>
            accessCalled = true
            Response(HttpStatus(Response.Status.Success), newCookieHeaders, newTokenBody)
          case LoginClient.AccessPath =>
            println("Unecessary second call to /access....")
            Response(HttpStatus(Response.Status.Success), Response.EmptyHeaders, newTokenBody)
          //          case LoginClient.AccessPath =>
          //            Response(HttpStatus(Response.Status.Unauthorized), Response.EmptyHeaders, EmptyResponse)
          case EventsClient.NotificationsPath =>
            Response(HttpStatus(Response.Status.Success), Response.EmptyHeaders, emptyNotifications)
          case _ => fail(s"Unexpected endpoint called: ${uri.getPath}")
        }

        callCount += 1
        CancellableFuture {
          Thread.sleep(2000)
          response
        }(new SerialDispatchQueue()("TestAsyncClient"))
      }

    val client = new DefaultZNetClient(credentials, mockAsync, config, new DefaultLoginClient(mockAsync, config))

    import com.wire.threading.Threading.Implicits.Background
    println("Triggering test now...")
    val res = Future.sequence(Seq(
      client.apply(Request.Get(EventsClient.NotificationsPath, EventsClient.notificationsQuery(None, None, 100))).future,
      client.apply(Request.Get(EventsClient.NotificationsPath, EventsClient.notificationsQuery(None, None, 100))).future,
      client.apply(Request.Get(EventsClient.NotificationsPath, EventsClient.notificationsQuery(None, None, 100))).future,
      client.apply(Request.Get(EventsClient.NotificationsPath, EventsClient.notificationsQuery(None, None, 100))).future,
      client.apply(Request.Get(EventsClient.NotificationsPath, EventsClient.notificationsQuery(None, None, 100))).future,
      client.apply(Request.Get(EventsClient.NotificationsPath, EventsClient.notificationsQuery(None, None, 100))).future
    ))

    println(s"result: ${Await.result(res, 1.minute)}")

    println(s"token: ${Await.result(MemMockStorage.getToken, 20.seconds)}")
    println(s"cookie: ${Await.result(MemMockStorage.getCookie, 20.seconds)}")

  }

  scenario("What happens if we call /access even with a valid token/cookie pair?") {
    //Answer: We get the same access token back and no cookie

    import com.wire.threading.Threading.Implicits.Background

    val dbFile = returning(new File("core/src/test/resources/databases/global.db"))(_.delete())
    val db = new SQLiteDatabase(dbFile, Seq(AccountDataDao))

    val accStorage = new AccountStorage(db)

    val account: AccountData = AccountData(AccountId(), Some(EmailAddress("dean+2@wire.com")), password = Some("aqa123456"))

    Await.ready(accStorage.insert(account), 5.seconds)

    val credentialsHandler = new CredentialsHandler {
      override val userId: AccountId = account.id

      override def credentials: Credentials = EmailCredentials(account.email, account.password)

      override val cookie: Preference[Option[Cookie]] = Preference[Option[Cookie]](None, accStorage.get(userId).map(_.flatMap(_.cookie)), { c => accStorage.update(userId, _.copy(cookie = c)) })
      override val accessToken: Preference[Option[Token]] = Preference[Option[Token]](None, accStorage.get(userId).map(_.flatMap(_.accessToken)), { token => accStorage.update(userId, _.copy(accessToken = token)) })

      override def onInvalidCredentials(): Unit = println("invalid credentials")
    }

    val async = new ApacheHTTPAsyncClient()
    val login = new DefaultLoginClient(async, BackendConfig.StagingBackend)
    val auth = new DefaultAuthenticationManager(login, credentialsHandler)

    Await.ready(auth.currentToken(), 5.seconds)

    val (cookie, token) = Await.result(for {
      cookie <- credentialsHandler.cookie()
      token <- credentialsHandler.accessToken()
    } yield (cookie, token), 5.seconds)

    println(s"Second token/cookie received: ${Await.result(login.accessNow(cookie.get, token), 5.seconds)}")

  }

  scenario("What's the response for invalid credentials/tokens/cookies to the backend") {
    //Answer - no token: missing access token/missing token or cookie

    import com.wire.threading.Threading.Implicits.Background

    val dbFile = returning(new File("core/src/test/resources/databases/global.db"))(_ => {})//(_.delete())
    val db = new SQLiteDatabase(dbFile, Seq(AccountDataDao))

    val accStorage = new AccountStorage(db)

    val account: AccountData = AccountData(AccountId(), Some(EmailAddress("dean+2@wire.com")), password = Some("aqa123456"), activated = true)

    Await.ready(accStorage.insert(account), 5.seconds)

    val credentialsHandler = new CredentialsHandler {
      override val userId: AccountId = account.id

      override def credentials: Credentials = EmailCredentials(account.email, account.password)

      override val cookie: Preference[Option[Cookie]] = Preference[Option[Cookie]](None, accStorage.get(userId).map(_.flatMap(_.cookie)), { c => accStorage.update(userId, _.copy(cookie = c)) })
      override val accessToken: Preference[Option[Token]] = Preference[Option[Token]](None, accStorage.get(userId).map(_.flatMap(_.accessToken)), { token => accStorage.update(userId, _.copy(accessToken = token)) })

      override def onInvalidCredentials(): Unit = println("invalid credentials")
    }

    val async = new ApacheHTTPAsyncClient()
    val login = new DefaultLoginClient(async, BackendConfig.StagingBackend)
    val auth = new DefaultAuthenticationManager(login, credentialsHandler)

    Await.ready(auth.currentToken(), 20.seconds)

    val (cookie, token) = Await.result(for {
      cookie <- credentialsHandler.cookie()
      token <- credentialsHandler.accessToken()
    } yield (cookie, token), 5.seconds)

    println(s"Second token/cookie received: ${Await.result(login.accessNow(Cookie("123"), token), 5.seconds)}")

  }

  scenario("Cookie expiry") {


    val cookie = Cookie("TPyYf0ZpqW5sNw8pG18fiKATb3LIjoqg4i9coaHXKoPf0mLPOJuKNfAfIY8tEp8-A38yrsl44yh0dUL_ssrtBA==.v=1.k=1.d=1493310228.t=u.l=.u=fffeb02e-38ec-4110-9048-96d5404ddbd1.r=dd6f28e8")

    println(cookie)
    println(cookie.str)
    println(cookie.headers)
    println(cookie.expiry)
    println(cookie.userId)
    println(cookie.isValid)

  }

}