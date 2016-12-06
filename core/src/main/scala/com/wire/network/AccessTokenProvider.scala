package com.wire.network

import com.wire.data.{JsonDecoder, JsonEncoder}
import com.wire.network.Response.Status
import com.wire.threading.CancellableFuture
import org.json.JSONObject
import org.threeten.bp.Instant

trait AccessTokenProvider {
  import AccessTokenProvider._
  def currentToken(): CancellableFuture[Either[Status, Token]]
}

object AccessTokenProvider {
  case class Token(accessToken: String, tokenType: String, expiresAt: Instant) {
    val headers = Map(Token.AuthorizationHeader -> s"$tokenType $accessToken")
  }

  object Token extends ((String, String, Instant) => Token ){
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
