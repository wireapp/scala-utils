package com.wire.data

import com.wire.data.ProtoBuffer.GenericMessage
import com.wire.macros.logging
import com.wire.macros.logging.LogTag
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.util.Try

sealed trait Event

case class GenericEvent(str: String) extends Event

object Event {
  implicit object EventDecoder extends JsonDecoder[Event] {
    //TODO event decoding
    override def apply(implicit js: JSONObject): Event = GenericEvent(js.toString)
  }
}

sealed trait MessageEvent extends Event
case class GenericMessageEvent(id: UId, convId: RConvId, time: Instant, from: UserId, content: GenericMessage) extends MessageEvent