package com.wire.data

import com.wire.macros.logging
import com.wire.macros.logging.LogTag
import org.json.JSONObject

import scala.util.Try

sealed trait Event

case class GenericEvent(str: String) extends Event

object Event {
//  type CallProperties = Set[CallProperty]

//  val UnknownDateTime = MessageData.UnknownInstant.javaDate

  implicit object EventDecoder extends JsonDecoder[Event] {
//    val Joined = "joined"
//    val Idle = "idle"
//
//    private implicit val tag: LogTag = logging.logTagFor(EventDecoder)
//
//    import com.wire.data.JsonDecoder._
//
//    import scala.collection.JavaConverters._
//
//    def connectionEvent(id: Uid)(implicit js: JSONObject, name: Option[String]) = UserConnectionEvent(id, 'conversation, 'from, 'to, 'message, ConnectionStatus('status), 'last_update, fromUserName = name)
//
//    def contactJoinEvent(id: Uid)(implicit js: JSONObject) = ContactJoinEvent(id, 'id, 'name)
//
//    def gcmTokenRemoveEvent(id: Uid)(implicit js: JSONObject) = GcmTokenRemoveEvent(id, token = 'token, senderId = 'app, client = 'client)
//
//    def joined(d: JSONObject): Boolean = d.getString("state") == Joined
//
//    def cause(d: JSONObject): CauseForCallStateEvent = if (d.has("cause")) try CauseForCallStateEvent.fromJson(d.getString("cause")) catch {
//      case e: IllegalArgumentException =>
//        warn("unknown cause for call state event: " + e)
//        CauseForCallStateEvent.REQUESTED
//    } else CauseForCallStateEvent.REQUESTED
//
//    def callParticipants(js: JSONObject): Set[CallParticipant] = {
//      val parts = js.getJSONObject("participants")
//      parts.keys().asInstanceOf[java.util.Iterator[String]].asScala.map { key => // cast is needed since some android versions don't return generic iterator
//        val d = parts.getJSONObject(key)
//        CallParticipant(UserId(key), joined(d), callProperties(d))
//      }.toSet
//    }
//
//    def callDeviceState(js: JSONObject) = CallDeviceState(joined(js), callProperties(js))
//
//    def callProperties(js: JSONObject): CallProperties = {
//      CallProperty.values.filter(p => js.optBoolean(p.asJson)).toSet
//    }
//
//    def callStateEvent(id: Uid)(implicit js: JSONObject) = {
//      CallStateEvent(id, 'conversation,
//        participants = if (js.has("participants") && !js.isNull("participants")) Some(callParticipants(js)) else None,
//        device = if (js.has("self") && !js.isNull("self")) Some(callDeviceState(js.getJSONObject("self"))) else None,
//        cause = cause(js),
//        sessionId = JsonDecoder.decodeOptId('session)(js, CallSessionId.Id),
//        sequenceNumber = JsonDecoder.decodeOptCallSequenceNumber('sequence)(js))
//    }
//
//    val CallEventType = """call\.(.+)""".r
//
//    override def apply(implicit js: JSONObject): Event = LoggedTry {
//
//      lazy val data = if (js.has("data") && !js.isNull("data")) Try(js.getJSONObject("data")).toOption else None
//      lazy val id = data.flatMap(decodeOptUid('nonce)(_)).getOrElse(Uid())
//
//      val evType = decodeString('type)
//      if (evType.startsWith("conversation")) ConversationEventDecoder(js)
//      else evType match {
//        case "user.update" => UserUpdateEvent(id, JsonDecoder[UserInfo]('user))
//        case "user.connection" => connectionEvent(id)(js.getJSONObject("connection"), JsonDecoder.opt('user, _.getJSONObject("user")) flatMap (JsonDecoder.decodeOptString('name)(_)))
//        case "user.contact-join" => contactJoinEvent(id)(js.getJSONObject("user"))
//        case "user.push-remove" => gcmTokenRemoveEvent(id)(js.getJSONObject("token"))
//        case "user.properties-set" => UserPropertiesSetEvent(id, 'key, 'value)
//        case "user.delete" => UserDeleteEvent(id, user = 'id)
//        case "user.client-add" => OtrClientAddEvent(id, OtrClient.ClientsResponse.client(js.getJSONObject("client")))
//        case "user.client-remove" => OtrClientRemoveEvent(id, decodeId[ClientId]('id)(js.getJSONObject("client"), implicitly))
//        case "call.state" => callStateEvent(id)
//        case "call.info" => IgnoredEvent(Uid(), js)
//        case CallEventType(kind) => UnknownCallEvent(id, kind, js)
//        case _ =>
//          error(s"unhandled event: $js")
//          UnknownEvent(id, js)
//      }
//    }.getOrElse(UnknownEvent(Uid(), js))
    override def apply(implicit js: JSONObject): Event = GenericEvent(js.toString)
  }

}
