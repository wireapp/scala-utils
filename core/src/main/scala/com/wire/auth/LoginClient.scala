package com.wire.auth

import java.net.URI

import com.wire.auth.AuthenticationManager.Cookie
import com.wire.auth.LoginClient.LoginResult
import com.wire.config.BackendConfig
import com.wire.data.{AccountId, JsonEncoder}
import com.wire.logging.ZLog._
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.network.AccessTokenProvider.Token
import com.wire.network.ContentEncoder.{EmptyRequestContent, JsonContentEncoder}
import com.wire.network.Response.{Status, SuccessHttpStatus}
import com.wire.network._
import com.wire.threading.CancellableFuture.CancelException
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}
import com.wire.utils.{ExponentialBackoff, RichUri}
import org.json.JSONObject
import org.threeten.bp.Instant

import com.wire.utils.RichInstant

import scala.concurrent.duration._
import scala.util.control.NonFatal

trait LoginClient {

  def login(accountId: AccountId, credentials: Credentials): CancellableFuture[LoginResult]

  def access(cookie: Option[String], token: Option[Token]): CancellableFuture[LoginResult]

}

class DefaultLoginClient(client: AsyncClient, backend: BackendConfig) extends LoginClient {
  import LoginClient._

  private implicit val dispatcher = new SerialDispatchQueue

  private var lastRequestTime = 0L
  private var failedAttempts = 0
  private var lastResponse = Status.Success
  private var loginFuture = CancellableFuture.successful[LoginResult](Left(ErrorResponse.Cancelled))

  def requestDelay =
    if (failedAttempts == 0) Duration.Zero
    else {
      val minDelay = if (lastResponse == Status.RateLimiting || lastResponse == Status.LoginRateLimiting) 5.seconds else Duration.Zero
      val nextRunTime = lastRequestTime + Throttling.delay(failedAttempts, minDelay).toMillis
      math.max(nextRunTime - System.currentTimeMillis(), 0).millis
    }

  override def login(accountId: AccountId, credentials: Credentials): CancellableFuture[LoginResult] = {
    warn(s"logging in: $accountId")
    throttled(loginNow(accountId, credentials))
  }

  override def access(cookie: Option[String], token: Option[Token]) = throttled(accessNow(cookie, token))

  def throttled(request: => CancellableFuture[LoginResult]): CancellableFuture[LoginResult] = dispatcher {
    loginFuture = loginFuture.recover {
      case e: CancelException => Left(ErrorResponse.Cancelled)
      case ex: Throwable =>
        error("Unexpected error when trying to log in.", ex)
        Left(ErrorResponse.internalError("Unexpected error when trying to log in: " + ex.getMessage))
    } flatMap { _ =>
      info(s"throttling, delay: $requestDelay")
      CancellableFuture.delay(requestDelay)
                  } flatMap { _ =>
      info(s"starting request")
      lastRequestTime = System.currentTimeMillis()
      request map {
        case Left(error) =>
          failedAttempts += 1
          lastResponse = error.getCode
          Left(error)
        case resp =>
          failedAttempts = 0
          lastResponse = Status.Success
          resp
      }
                  }
    loginFuture
  }.flatten

  def loginNow(userId: AccountId, credentials: Credentials) = {
    info(s"trying to login: $credentials")
    client(loginUri, Request.PostMethod, loginRequestBody(userId, credentials), timeout = timeout).map(responseHandler)
  }
  def accessNow(cookie: Option[String], token: Option[Token]) = {
    val headers = token.fold(Request.EmptyHeaders)(_.headers) ++ cookie.fold(Request.EmptyHeaders)(c => Map(Cookie -> s"zuid=$c"))
    info(s"accessNow with headers: $headers")
    client(accessUri, Request.PostMethod, EmptyRequestContent, headers, timeout = timeout).map(responseHandler)
  }

  def requestVerificationEmail(email: EmailAddress): CancellableFuture[Either[ErrorResponse, Unit]] = {
    client(activateSendUri, Request.PostMethod, JsonContentEncoder(JsonEncoder(_.put("email", email.str)))) map {
      case Response(SuccessHttpStatus(), resp, _) => Right(())
      case Response(_, _, ErrorResponse(code, msg, label)) =>
        info(s"requestVerificationEmail failed with error: ($code, $msg, $label)")
        Left(ErrorResponse(code, msg, label))
      case resp =>
        error(s"Unexpected response from resendVerificationEmail: $resp")
        Left(ErrorResponse(400, resp.toString, "unknown"))
    }
  }

  protected val responseHandler: PartialFunction[Response, LoginResult] = {
    case Response(SuccessHttpStatus(), responseHeaders, JsonObjectResponse(TokenResponse(token, exp, ttype))) =>
      info(s"receivedAccessToken: '$token', headers: $responseHeaders")
      Right((Token(token, ttype, Instant.now + exp.seconds), getCookieFromHeaders(responseHeaders)))
    case r @ Response(status, _, ErrorResponse(code, msg, label)) =>
      warn(s"failed login attempt: $r")
      Left(ErrorResponse(code, msg, label))
    case r @ Response(status, _, _) =>
      warn("Unexpected login response")
      Left(ErrorResponse(status.status, s"unexpected login response: $r", ""))
  }

  protected val loginUri = new URI(s"${backend.baseUrl}$LoginPath").appendQuery("persist", "true")
  protected val accessUri = new URI(s"${backend.baseUrl}$AccessPath")
  protected val activateSendUri = new URI(s"${backend.baseUrl}$ActivateSendPath")
}

object LoginClient {

  type LoginResult = Either[ErrorResponse, (Token, Cookie)]
  type AccessToken = (String, Int, String)

  val timeout = 15.seconds

  val SetCookie = "Set-Cookie"
  val Cookie = "Cookie"
  val CookieHeader = ".*zuid=([^;]+).*".r
  val LoginPath = "/login"
  val AccessPath = "/access"
  val ActivateSendPath = "/activate/send"

  val Throttling = new ExponentialBackoff(1000.millis, 10.seconds)

  def loginRequestBody(user: AccountId, credentials: Credentials) = JsonContentEncoder(JsonEncoder { o =>
    o.put("label", user.str)  // this label can be later used for cookie revocation
    credentials.email.foreach(v => o.put("email", v.str))
    credentials.password.foreach(v => o.put("password", v))
  })

  def getCookieFromHeaders(headers: Response.Headers): Cookie = headers(SetCookie) flatMap {
    case header @ CookieHeader(cookie) =>
      info(s"parsed cookie from header: $header, cookie: $cookie")
      Some(cookie)
    case header =>
      warn(s"Unexpected content for Set-Cookie header: $header")
      None
  }

  object TokenResponse {
    def unapply(json: JSONObject): Option[AccessToken] =
      if (json.has("access_token") && json.has("expires_in") && json.has("token_type")) {
        try {
          Some((json.getString("access_token"), json.getInt("expires_in"), json.getString("token_type")))
        } catch {
          case NonFatal(_) => None
        }
      } else None
  }
}