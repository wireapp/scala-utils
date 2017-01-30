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
  package com.wire.messages


import com.wire.data.ProtoFactory.GenericMsg
import com.wire.data.{AssetId, ConvId, MessageId, UserId}
import org.threeten.bp.Instant

case class MessageData(id:        MessageId           = MessageId(),
                       convId:    ConvId              = ConvId(),
                       senderId:    UserId              = UserId(),
                       msgType:   MessageType         = MessageType.Text,
                       protos:    Seq[GenericMsg] = Seq.empty,
                       localTime: Instant             = Instant.now
                      ) {
  override def toString: String =
    s"""
      |MessageData:
      | id:         $id
      | convId:     $convId
      | senderId:   $senderId
      | msgType:    $msgType
      | protos:     ${protos.toString().replace("\n", "")}
      | localTime:  $localTime
    """.stripMargin

  val assetId = AssetId(id.str)
}

sealed trait MessageType

object MessageType {
  case object Text        extends MessageType
  case object AudioAsset  extends MessageType
  case object ImageAsset  extends MessageType
  case object VideoAsset  extends MessageType
  case object OtherAsset  extends MessageType
  case object Unknown     extends MessageType
}
