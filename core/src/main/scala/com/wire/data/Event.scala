/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH

 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
  package com.wire.data

import java.util.UUID

import com.wire.data.JsonDecoder._
import com.wire.data.ProtoFactory.GenericMsg
import com.wire.error.LoggedTry
import org.json.JSONObject
import org.threeten.bp.Instant
import com.wire.macros.logging.ImplicitTag._

import scala.util.Try

sealed trait Event {
  val id: UId
}

sealed trait RConvEvent extends Event {
  val convId: RConvId
}

sealed trait ConvEvent extends RConvEvent {
  val time: Instant
  val from: UserId
}

sealed trait OtrEvent extends ConvEvent {
  val sender    : ClientId
  val recipient : ClientId
  val cipherText: Array[Byte]
}

case class OtrMsgEvent(id:            UId,
                       convId:        RConvId,
                       time:          Instant,
                       from:          UserId,
                       sender:        ClientId,
                       recipient:     ClientId,
                       cipherText:    Array[Byte],
                       externalData:  Option[Array[Byte]] = None
                      ) extends OtrEvent

sealed trait MsgEvent extends ConvEvent

case class GenericMsgEvent(id:      UId,
                           convId:  RConvId,
                           time:    Instant,
                           from:    UserId,
                           content: GenericMsg
                          ) extends MsgEvent

case class UnknownConvEvent(id:         UId       = UId(),
                            convId:     RConvId   = RConvId(),
                            from:       UserId    = UserId(),
                            time:       Instant   = Instant.EPOCH,
                            jSONObject: JSONObject
                           ) extends ConvEvent


object Event {

  implicit object EventDecoder extends JsonDecoder[Event] {
    //TODO event decoding
    override def apply(implicit js: JSONObject): Event = ConvEvent.ConvEventDecoder(js)
  }

}

object ConvEvent {
  val EventId = """(\d+).([0-9a-fA-F]+)""".r

  implicit lazy val ConvEventDecoder: JsonDecoder[ConvEvent] = new JsonDecoder[ConvEvent] {

    override def apply(implicit js: JSONObject): ConvEvent = LoggedTry {

      lazy val data = if (js.has("data") && !js.isNull("data")) Try(js.getJSONObject("data")).toOption else None
      lazy val id = decodeOptString('id) match {
        case Some(EventId(seq, hex)) =>
          // id is generated from conversation id and eventId, this should give us pretty unique and stable id
          val uid = UUID.fromString('conversation)
          UId(uid.getMostSignificantBits ^ seq.toLong, java.lang.Long.parseLong(hex.substring(1), 16))
        case _ => UId()
      }

      decodeString('type) match {
        case "conversation.otr-message-add" => OtrMsgEvent(id, 'conversation, 'time, 'from, decodeClientId('sender)('data), decodeClientId('recipient)('data), 'text, 'data)
        case _ => UnknownConvEvent(jSONObject = js)
      }
    }.getOrElse {
      UnknownConvEvent(jSONObject = js)
    }
  }
}
