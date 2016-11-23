package com.wire.assets

import com.wire.data.Dimensions.{H, W}
import com.wire.data.{Dimensions, JsonDecoder, JsonEncoder}
import org.json.JSONObject
import org.threeten.bp.Duration

import scala.util.Try

sealed abstract class AssetMetaData(val jsonTypeTag: Symbol)
object AssetMetaData {

  implicit lazy val AssetMetaDataEncoder: JsonEncoder[AssetMetaData] = new JsonEncoder[AssetMetaData] {
    override def apply(data: AssetMetaData): JSONObject = JsonEncoder { o =>
      o.put("type", data.jsonTypeTag.name)
      data match {
        case Empty => // nothing to add
        case Video(w, h, duration) =>
          o.put("width", w)
          o.put("height", h)
          o.put("duration", duration.toMillis)
        case Audio(duration, loudness) =>
          o.put("duration", duration.toMillis)
          loudness.foreach(l => o.put("levels", JsonEncoder.arrNum(l.levels)))
        case Image(w, h, tag) =>
          o.put("width", w)
          o.put("height", h)
          o.put("tag", tag)
      }
    }
  }

  implicit lazy val AssetMetaDataDecoder: JsonDecoder[AssetMetaData] = new JsonDecoder[AssetMetaData] {

    import JsonDecoder._

    override def apply(implicit o: JSONObject): AssetMetaData = decodeSymbol('type) match {
      case 'empty => Empty
      case 'video =>
        Video('width, 'height, 'duration)
      case 'audio =>
        Audio('duration, Try(JsonDecoder.array[Float]('levels)((arr, i) => arr.getDouble(i).toFloat)).toOption.map(Loudness))
      case 'image =>
        Image('width, 'height, Image.Tag(decodeString('tag)))
      case other =>
        throw new IllegalArgumentException(s"unsupported meta data type: $other")
    }
  }

  trait HasDuration {
    val duration: Duration
  }

  object HasDuration {
    def unapply(meta: HasDuration): Option[Duration] = Some(meta.duration)
  }

  case class Loudness(levels: Vector[Float])

  case class Video(width: W, height: H, duration: Duration) extends AssetMetaData('video) with Dimensions with HasDuration
  case class Image(width: W, height: H, tag: Image.Tag = Image.Tag.Empty) extends AssetMetaData('image) with Dimensions
  case class Audio(duration: Duration, loudness: Option[Loudness] = None) extends AssetMetaData('audio) with HasDuration
  case object Empty extends AssetMetaData('empty)


  object Image {

    sealed abstract class Tag(str: String) {
      override def toString: String = str
    }

    /**
      * An implementation note on Image tags:
      * V2 images often contain both a "preview" and a "medium" version, where we rely on the tag to drop the preview version
      * V3 images can also contain tags, but no client currently sends a "preview" version, so we don't need to worry.
      *
      * V2 profile pictures are stored in the "picture" field of user data with the tags "smallProfile" and "medium". The
      * Webapp team requires that we always upload a small version of the image, as they don't have their own caching (yet)
      * V3 profile pictures are stored in the "assets" field of user data with the tags "preview" or "complete". Again we
      * must upload both for webapp.
      *
      * To simplify implementations, I'm going with two internal tags, "preview" and "medium". Depending on where they're
      * used though, they may be translated into "smallProfile" or "complete"
      *
      * TODO Dean: it would be nice to sync up with other clients and steadily introduce a more uniform set of tags
      */

    object Tag {
      case object Preview      extends Tag("preview")
      case object Medium       extends Tag("medium")
      case object Empty        extends Tag("")

      def apply(tag: String): Tag = tag match {
        case "preview"      => Preview
        case "smallProfile" => Preview
        case "medium"       => Medium
        case "complete"     => Medium
        case _              => Empty
      }
    }

    val Empty = Image(W(0), H(0), Tag.Empty)

  }
}
