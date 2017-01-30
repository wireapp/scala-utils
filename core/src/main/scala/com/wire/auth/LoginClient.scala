package com.wire.auth

import com.wire.auth.AuthenticationManager.{Cookie, Token}
import com.wire.auth.LoginClient.LoginResult
import com.wire.data.{AccountId, JsonEncoder}
import com.wire.logging.Logging.{verbose, warn}
import com.wire.macros.logging.ImplicitTag._
import com.wire.network.ContentEncoder.JsonContentEncoder
import com.wire.network.{ErrorResponse, Response}
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}
import com.wire.utils.ExponentialBackoff
import org.json.JSONObject

import scala.concurrent.duration._
import scala.util.control.NonFatal

trait LoginClient {

  def login(accountId: AccountId, credentials: Credentials): CancellableFuture[LoginResult]

  def access(cookie: Option[String], token: Option[Token]): CancellableFuture[LoginResult]

}

class DefaultLoginClient extends LoginClient {
  import LoginClient._

  private implicit val dispatcher = new SerialDispatchQueue(name = "LoginClient")

  override def login(accountId: AccountId, credentials: Credentials) = ???

  override def access(cookie: Option[String], token: Option[Token]) = ???
}

object LoginClient {

  type LoginResult = Either[ErrorResponse, (Token, Cookie)]
  type AccessToken = (String, Int, String)

  val SetCookie = "Set-Cookie"
  val Cookie = "Cookie"
  val CookieHeader = ".*zuid=([^;]+).*".r
  val LoginPath = "/login"
  val AccessPath = "/access"
  val ActivateSendPath = "/activate/send"

  val Throttling = new ExponentialBackoff(1000.millis, 10.seconds)

  def loginRequestBody(user: AccountId, credentials: Credentials) = JsonContentEncoder(JsonEncoder { o =>
    o.put("label", user.str)  // this label can be later used for cookie revocation
    credentials.addToLoginJson(o)
  })

  def getCookieFromHeaders(headers: Response.Headers): Cookie = headers(SetCookie) flatMap {
    case header @ CookieHeader(cookie) =>
      verbose(s"parsed cookie from header: $header, cookie: $cookie")
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