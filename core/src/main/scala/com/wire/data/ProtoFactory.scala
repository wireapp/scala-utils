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

import com.waz.model.nano.Messages
import com.waz.model.nano.Messages.Asset.Original
import com.wire.assets.AssetData.RemoteData
import com.wire.assets.AssetStatus.{UploadCancelled, UploadDone, UploadFailed, UploadInProgress}
import com.wire.assets.{AssetStatus, AssetToken}
import com.wire.cryptography.{AESKey, Sha256}
import com.wire.macros.returning

/**
  * @tparam A the type of the proto message's content (eg, Text, Asset etc.)
  */
trait ProtoFactory[-A] {

  import ProtoFactory._

  def set(msg: GenericMsg): A => GenericMsg
}

object ProtoFactory {

  type GenericMsg = Messages.GenericMessage

  object GenericMsg {
    def apply[F: ProtoFactory](id: UId, content: F): GenericMsg =
      returning(new Messages.GenericMessage()) { msg =>
        msg.messageId = id.str
        implicitly[ProtoFactory[F]].set(msg)(content)
      }

    def unapply(proto: GenericMsg): Option[(UId, Any)] =
      Some(UId(proto.messageId), content(proto))

    def content(msg: GenericMsg) = {
      import Messages.{GenericMessage => GM}
      msg.getContentCase match {
        case GM.TEXT_FIELD_NUMBER   => msg.getText
        case GM.ASSET_FIELD_NUMBER  => msg.getAsset
        case _ => Unknown
      }
    }
  }

  type Text = Messages.Text

  implicit object Text extends ProtoFactory[Text] {
    override def set(msg: GenericMsg) = msg.setText

    def apply(content: String): Text =
      returning(new Messages.Text()) { msg =>
        msg.content = content
      }

    def unapply(proto: Text): Option[String] =
      Some(proto.content)
  }

  type Asset = Messages.Asset

  implicit object Asset extends ProtoFactory[Asset] {

    object Original {
      def apply(mime: Mime, size: Long, name: Option[String]): Original =
        returning(new Original()) { orig =>
          orig.mimeType = mime.str
          orig.size = size
          name.foreach(orig.name = _)
        }

      def unapply(proto: Original): Option[(Mime, Long, Option[String])] = {
        val name = Option(proto.name).filter(_.nonEmpty)
        val mime = Option(proto.mimeType).filter(_.nonEmpty).fold(Mime.Unknown)(Mime(_)) //TODO get mime from (file)name if empty
        Some(mime, proto.size, name)
      }
    }

    object Status

    object ImageMetaData

    object VideoMetaData

    object AudioMetaData

    type Preview = Messages.Asset.Preview
    object Preview

    object Uploaded {
      def unapply(r: Messages.Asset.RemoteData): Option[RemoteData] = {
        //TODO can the assetId be null? It's optional in proto, but that doesn't make sense...
        if (r.assetId == null || r.otrKey == null || r.sha256 == null) None
        else Some(RemoteData(Option(RAssetId(r.assetId)), Option(AssetToken(r.assetToken)).filter(_.str.nonEmpty), Option(AESKey(r.otrKey)), Option(Sha256(r.sha256))))
      }
    }

    override def set(msg: GenericMsg): (Asset) => GenericMsg = msg.setAsset

    def apply(original: Original): Asset = returning(new Asset()) { msg =>
      msg.original = original
    }

    def apply(mime: Mime, size: Long, name: Option[String]): Asset =
      apply(Original(mime, size, name))

    def unapply(proto: Asset): Option[(Option[Messages.Asset.Original], Option[Preview], AssetStatus)] = None
  }

  case object Unknown

  implicit object UnkownContent extends ProtoFactory[Unknown.type] {
    override def set(msg: GenericMsg) = { _ => msg }
  }

}
