package com.wire.users

import com.wire.assets.AssetMetaData.Image
import com.wire.assets.AssetMetaData.Image.Tag
import com.wire.assets.{AssetData, AssetMetaData}
import com.wire.assets.AssetMetaData.Image.Tag.{Medium, Preview}
import com.wire.assets.AssetStatus.UploadDone
import com.wire.auth.{EmailAddress, Handle, PhoneNumber}
import com.wire.data.Dimensions.{H, W}
import com.wire.data._
import com.wire.network.ContentEncoder
import com.wire.network.ContentEncoder.JsonContentEncoder
import org.json
import org.json.{JSONArray, JSONObject}

import scala.util.Try

case class UserInfo(id:           UserId,
                    name:         Option[String]          = None,
                    accentId:     Option[Int]             = None,
                    email:        Option[EmailAddress]    = None,
                    phone:        Option[PhoneNumber]     = None,
                    handle:       Option[Handle]          = None,
                    picture:      Option[Seq[AssetData]]  = None, //the empty sequence is used to delete pictures
                    trackingId:   Option[TrackingId]      = None,
                    deleted:      Boolean                 = false,
                    privateMode:  Option[Boolean]         = None) {
  //TODO Dean - this will actually prevent deleting profile pictures, since the empty seq will be mapped to a None,
  //And so in UserData, the current picture will be used instead...
  def mediumPicture = picture.flatMap(_.collectFirst { case a@AssetData.IsImageWithTag(Medium) => a })
}

object UserInfo {
  import com.wire.data.JsonDecoder._

  implicit object Decoder extends JsonDecoder[UserInfo] {

    def imageData(userId: UserId, js: JSONObject) = {
      val mime = decodeString('content_type)(js)
      val size = decodeInt('content_length)(js)
      val data = decodeOptString('data)(js)
      val id = RAssetId(decodeString('id)(js))
      implicit val info = js.getJSONObject("info")

      AssetData(
        status = UploadDone,
        sizeInBytes = size,
        mime = Mime(mime),
        metaData = Some(AssetMetaData.Image('width, 'height, Image.Tag('tag))),
        data = data.map(AssetData.decodeData)
      )

    }

    def getAssets(implicit js: JSONObject): Option[AssetData] = fromArray(js, "assets") flatMap { assets =>
      Seq.tabulate(assets.length())(assets.getJSONObject).map { js =>
        AssetData(
          remoteId = decodeOptRAssetId('key)(js),
          metaData = Some(AssetMetaData.Image(W(0), H(0), Image.Tag(decodeString('size)(js))))
        )
      }.collectFirst { case a@AssetData.IsImageWithTag(Tag.Medium) => a } //discard preview
    }

    def getPicture(userId: UserId)(implicit js: JSONObject): Option[AssetData] = fromArray(js, "picture") flatMap { pic =>
      val id = decodeOptString('correlation_id)(pic.getJSONObject(0).getJSONObject("info")).fold(AssetId())(AssetId(_))

      Seq.tabulate(pic.length())(i => imageData(userId, pic.getJSONObject(i))).collectFirst {
        case a@AssetData.IsImageWithTag(Medium) => a //discard preview
      }.map(_.copy(id = id))
    }

    private def fromArray(js: JSONObject, name: String) = Try(js.getJSONArray(name)).toOption.filter(_.length() > 0)

    override def apply(implicit js: JSONObject): UserInfo = {
      val accentId = decodeOptInt('accent_id).orElse {
        decodeDoubleSeq('accent) match {
          case Seq(r, g, b, a) => Some(AccentColor(r, g, b, a).id)
          case _ => None
        }
      }
      val id = UserId('id)
      //prefer v3 ("assets") over v2 ("picture") - this will prevent unnecessary uploading of v3 if a v2 also exists.
      val pic = getAssets.orElse(getPicture(id)).toSeq
      val privateMode = decodeOptBoolean('privateMode)
      UserInfo(id, 'name, accentId, 'email, 'phone, 'handle, Some(pic), decodeOptString('tracking_id) map (TrackingId(_)), deleted = 'deleted, privateMode = privateMode)
    }
  }

  def encodePicture(assets: Seq[AssetData]): JSONArray = {

    val medium = assets.collectFirst { case a@AssetData.IsImageWithTag(Medium) => a }
    val arr = new json.JSONArray()
    assets.collect {
      case a@AssetData.IsImage() =>
        val tag = a.tag match {
          case Preview => "smallProfile"
          case Medium => "medium"
          case _ => ""
        }
        JsonEncoder { o =>
          o.put("content_type", a.mime.str)
          o.put("content_length", a.size)
          o.put("info", JsonEncoder { info =>
            info.put("tag", tag)
            info.put("width", a.width)
            info.put("height", a.height)
            info.put("original_width", medium.map(_.width).getOrElse(a.width))
            info.put("original_height", medium.map(_.height).getOrElse(a.height))
            info.put("correlation_id", medium.map(_.id.str).getOrElse(a.id.str))
            info.put("nonce", a.id.str)
            info.put("public", true)
          })
        }
    }.foreach(arr.put)
    arr
  }


  def encodeAsset(assets: Seq[AssetData]): JSONArray = {
    val arr = new json.JSONArray()
    assets.collect {
      case a@AssetData.WithRemoteId(rId) =>
        val size = a.tag match {
          case Preview => "preview"
          case Medium => "complete"
          case _ => ""
        }
        JsonEncoder { o =>
          o.put("size", size)
          o.put("key", rId.str)
          o.put("type", "image")
        }
    }.foreach(arr.put)
    arr
  }

  implicit lazy val Encoder: JsonEncoder[UserInfo] = new JsonEncoder[UserInfo] {
    override def apply(info: UserInfo): JSONObject = JsonEncoder { o =>
      o.put("id", info.id.str)
      info.name.foreach(o.put("name", _))
      info.phone.foreach(p => o.put("phone", p.str))
      info.email.foreach(e => o.put("email", e.str))
      info.accentId.foreach(o.put("accent_id", _))
      info.trackingId.foreach(id => o.put("tracking_id", id.str))
      info.picture.foreach(ps => o.put("assets", encodeAsset(ps)))
      info.picture.foreach(ps => o.put("picture", encodePicture(ps)))
    }
  }

  implicit lazy val ContentEncoder: ContentEncoder[UserInfo] = JsonContentEncoder.map { (info: UserInfo) =>
    JsonEncoder { o =>
      info.name.foreach(o.put("name", _))
      info.accentId.foreach(o.put("accent_id", _))
      info.picture.foreach(ps => o.put("assets", encodeAsset(ps)))
      info.picture.foreach(ps => o.put("picture", encodePicture(ps)))
    }
  }
}