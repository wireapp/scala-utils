package com.wire.users

import com.wire.data.{JsonDecoder, JsonEncoder}
import com.wire.logging.Analytics.NoReporting
import com.wire.network.Response.SuccessHttpStatus
import com.wire.network._
import com.wire.network.ZNetClient.ErrorOrResponse
import com.wire.users.UsersClient.UserResponseExtractor
import org.json.JSONObject
import UsersClient._

import scala.util.Try

trait UsersClient {
  def loadSelf(): ErrorOrResponse[UserInfo]
}

class DefaultUsersClient(netClient: ZNetClient) extends UsersClient {

  import com.wire.threading.Threading.Implicits.Background

  override def loadSelf() =
    netClient.withErrorHandling("loadSelf", Request.Get(SelfPath)) {
      case Response(SuccessHttpStatus(), _, UserResponseExtractor(user)) => user
    }
}

object UsersClient {
  val UsersPath = "/users"
  val SelfPath = "/self"
  val ConnectionsPath = "/self/connections"
  val IdsCountThreshold = 45

  object UserResponseExtractor {
    def unapplySeq(resp: ResponseContent): Option[Seq[UserInfo]] = resp match {
      case JsonArrayResponse(js) => Try(JsonDecoder.array[UserInfo](js)).toOption
      case JsonObjectResponse(js) => Try(Vector(UserInfo.Decoder(js))).toOption
      case _ => None
    }
  }

  case class DeleteAccount(password: Option[String])

  implicit lazy val DeleteAccountEncoder: JsonEncoder[DeleteAccount] = new JsonEncoder[DeleteAccount] {
    override def apply(v: DeleteAccount): JSONObject = JsonEncoder { o =>
      v.password foreach (o.put("password", _))
    }
  }

  class FailedLoadUsersResponse(val error: ErrorResponse) extends RuntimeException(s"loading users failed with: $error") with NoReporting

}