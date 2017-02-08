package com.wire.network

import java.net.URI
import java.util.concurrent.TimeUnit

import com.wire.auth.Credentials.EmailCredentials
import com.wire.auth.{Credentials, CredentialsHandler, DefaultLoginClient, EmailAddress}
import com.wire.config.BackendConfig
import com.wire.data.AccountId
import com.wire.network.AccessTokenProvider.Token
import com.wire.network.ContentEncoder.RequestContent
import com.wire.network.Request.ProgressCallback
import com.wire.network.Response.{HttpStatus, ResponseBodyDecoder}
import com.wire.storage.Preference
import com.wire.testutils.FullFeatureSpec
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}

class ZNetClientSpec extends FullFeatureSpec {

  val credentials = new CredentialsHandler {
    override def credentials = EmailCredentials(Some(EmailAddress("dean+1@wire.com")))

    private implicit val dispatcher = new SerialDispatchQueue()("DatabaseQueue")

    override val accessToken = Preference[Option[Token]](None, Future(Some(Token("Token", "Bearer", Instant.now))), _ => Future.successful())
    override val cookie      = Preference[Option[String]](None, Future(Some("Cookie")), _ => Future.successful())
    override val userId      = AccountId("77cc30d6-8790-4258-bf04-b2bfdcd9642a")
  }

  var asyncApply: (URI, String, RequestContent, Map[String, String], Boolean, FiniteDuration, Option[ResponseBodyDecoder], Option[ProgressCallback]) => CancellableFuture[Response] = {case _ => CancellableFuture.successful(Response(Response.HttpStatus(200)))}

  val async = mock[AsyncClient]
  (async.apply _)
    .expects(*, *, *, *, *, *, *, *)
    .anyNumberOfTimes()
    .onCall {asyncApply}

  val config = BackendConfig("https://somebackend.com")

  val client = new DefaultZNetClient(credentials, async, config, new DefaultLoginClient(async, config))

  def get(path: String, auth: Boolean = false) = Await.result(client(Request[Unit]("GET", Some(path), requiresAuthentication = auth)), Duration(1000, TimeUnit.MILLISECONDS))

  def post[A: ContentEncoder](path: String, data: A, auth: Boolean = false) = Await.result(client(Request("POST", Some(path), data = Some(data), requiresAuthentication = auth)), Duration(500, TimeUnit.MILLISECONDS))

  feature("GET request") {

    scenario("Empty http GET request - 200") {
      get("/get/empty200") match {
        case Response(HttpStatus(200, _), _, EmptyResponse) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(200))")
      }
    }

    scenario("Empty http GET request - 400") {
      asyncApply = {case _ => CancellableFuture.successful(Response(Response.HttpStatus(400)))}
      get("/get/empty400")match {
        case Response(HttpStatus(400, _), _, EmptyResponse) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(400))")
      }
    }

    scenario("Perform json GET request") {
      val json = """{"result": "ok"}"""
      get("/get/json200") match {
        case Response(HttpStatus(200, _), _, JsonObjectResponse(js)) if js.toString == new JSONObject(json).toString => //fine
        case r => fail(s"got: $r instead of Response(HttpStatus(200), JsonBody($json))")
      }
    }
  }

  feature("POST request") {

    scenario("Empty http POST request - 200") {
      post("/post/empty200", {}) match {
        case Response(HttpStatus(200, _), _, EmptyResponse) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(200))")
      }
    }

    scenario("Post json, receive empty 200") {
      post("/post/json_empty200", new JSONObject("""{ "key": "value"}""")) match {
        case Response(HttpStatus(200, _), _, EmptyResponse) => // expected
        case resp => fail(s"got: $resp when expected Response(HttpStatus(200))")
      }
    }

    scenario("Post json, receive json 200") {
      val json = """{"result": "ok"}"""
      post("/post/json_json200", new JSONObject("""{ "key": "value"}""")) match {
        case Response(HttpStatus(200, _), _, JsonObjectResponse(js)) if js.toString == new JSONObject(json).toString => //fine
        case r => fail(s"got: $r instead of Response(HttpStatus(200), JsonBody($json))")
      }
    }
  }

  feature("Authentication") {

    scenario("Call /self with access token") {
      get("/self", auth = true) match {
        case Response(HttpStatus(200, _), _, JsonObjectResponse(js)) if js.getString("email") == "test@mail.com" => //fine
        case r => fail(s"got: $r instead of Response(HttpStatus(200), JsonBody({email: test@mail.com ..}))")
      }
    }
  }
}
