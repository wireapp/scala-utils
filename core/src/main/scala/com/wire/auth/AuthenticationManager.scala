package com.wire.auth

import com.wire.auth.AuthenticationManager.Token
import com.wire.auth.LoginClient.LoginResult
import com.wire.data.{JsonDecoder, JsonEncoder}
import com.wire.network.ErrorResponse
import com.wire.network.Response.Status
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}
import org.json.JSONObject

import scala.concurrent.Future

trait AuthenticationManager {

  def currentToken(): Future[Either[ErrorResponse, Token]]

  def invalidateToken(): Future[Unit]

  def close(): CancellableFuture[Boolean]

}

class DefaultAuthenticationManager(client: LoginClient, user: CredentialsHandler) extends AuthenticationManager {

  implicit val dispatcher = new SerialDispatchQueue(name = "AuthenticationManager")

  private var closed = false

  private val tokenPref = user.accessToken


  override def currentToken() = ???
  override def invalidateToken() = ???
  override def close() = ???
}


object AuthenticationManager {

  val MaxRetryCount = 3
  val bgRefreshThreshold = 15 * 1000 // refresh access token on background if it is close to expire

  type ResponseHandler = PartialFunction[LoginResult, CancellableFuture[Either[Status, Token]]]

  type Cookie = Option[String]

  case class Token(accessToken: String, tokenType: String, expiresAt: Long = 0) {
    val headers = Map(Token.AuthorizationHeader -> s"$tokenType $accessToken")
//    def prepare(req: AsyncHttpRequest) = req.addHeader(Token.AuthorizationHeader, s"$tokenType $accessToken")
  }

  object Token extends ((String, String, Long) => Token ){
    val AuthorizationHeader = "Authorization"

    implicit lazy val Encoder: JsonEncoder[Token] = new JsonEncoder[Token] {
      override def apply(v: Token): JSONObject = JsonEncoder { o =>
        o.put("token", v.accessToken)
        o.put("type", v.tokenType)
        o.put("expires", v.expiresAt)
      }
    }

    implicit lazy val Decoder: JsonDecoder[Token] = new JsonDecoder[Token] {
      import JsonDecoder._
      override def apply(implicit js: JSONObject): Token = Token('token, 'type, 'expires)
    }
  }
}