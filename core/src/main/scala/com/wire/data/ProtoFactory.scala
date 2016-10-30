package com.wire.data

import com.waz.model.nano.Messages
import com.waz.model.nano.Messages.Asset.Original
import com.wire.assets.AssetStatus.{UploadCancelled, UploadDone, UploadFailed, UploadInProgress}
import com.wire.assets.{AssetKey, AssetStatus, AssetToken, RemoteKey}
import com.wire.cryptography.{AESKey, Sha256}
import com.wire.macros.returning
import com.wire.utils.RichOption

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
    }

    object Status

    object ImageMetaData

    object VideoMetaData

    object AudioMetaData

    type Preview = Messages.Asset.Preview

    object Preview

    object Uploaded {
      def unapply(r: Messages.Asset.RemoteData): Option[(Option[RemoteKey], Option[AssetToken], AESKey, Sha256)] = {
        if (r.otrKey == null || r.sha256 == null) None
        else Some((Option(RemoteKey(r.assetId)), Option(AssetToken(r.assetToken)).filter(_.str.nonEmpty), AESKey(r.otrKey), Sha256(r.sha256)))
      }
    }

    override def set(msg: GenericMsg): (Asset) => GenericMsg = msg.setAsset

    def apply(original: Original): Asset = returning(new Asset()) { msg =>
      msg.original = original
    }

    def apply(mime: Mime, size: Long, name: Option[String]): Asset =
      apply(Original(mime, size, name))

    def unapply(proto: Asset): Option[(Option[Messages.Asset.Original], Option[Preview], AssetStatus)] = {
      val status = proto.getStatusCase match {
        case Messages.Asset.UPLOADED_FIELD_NUMBER =>
          proto.getUploaded match {
            case Uploaded(id, token, aesKey, sha) => UploadDone(AssetKey(id.fold2(Left(RAssetDataId.Empty), Right(_)), token, aesKey, sha))
            case _ => UploadFailed
          }
        case Messages.Asset.NOT_UPLOADED_FIELD_NUMBER =>
          proto.getNotUploaded match {
            case Messages.Asset.CANCELLED => UploadCancelled
            case Messages.Asset.FAILED => UploadFailed
            case _ => UploadInProgress
          }
        case _ => UploadInProgress
      }

      Some(Option(proto.original), Option(proto.preview), status)
    }
  }

  case object Unknown

  implicit object UnkownContent extends ProtoFactory[Unknown.type] {
    override def set(msg: GenericMsg) = { _ => msg }
  }

}
