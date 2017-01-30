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
  package com.wire.assets

import java.net.URI

import com.wire.assets.AssetMetaData.Image
import com.wire.assets.AssetStatus.UploadDone
import com.wire.assets.DownloadRequest._
import com.wire.cache.CacheKey
import com.wire.cryptography.{AESKey, Sha256}
import com.wire.data.Dimensions.{H, W}
import com.wire.data._
import com.wire.logging.Logging.verbose
import com.wire.macros.logging.ImplicitTag._
import org.apache.commons.codec.binary.Base64
import org.json.JSONObject
import org.threeten.bp.Duration

case class AssetData(id:          AssetId               = AssetId(),
                     mime:        Mime                  = Mime.Unknown,
                     sizeInBytes: Long                  = 0L,
                     status:      AssetStatus           = AssetStatus.UploadNotStarted,
                     remoteId:    Option[RAssetId]      = None,
                     token:       Option[AssetToken]    = None,
                     otrKey:      Option[AESKey]        = None,
                     sha:         Option[Sha256]        = None,
                     name:        Option[String]        = None,
                     previewId:   Option[AssetId]       = None,
                     metaData:    Option[AssetMetaData] = None,
                     source:      Option[URI]           = None,
                     proxyPath:   Option[String]        = None,
                     //data only used for temporary caching and legacy reasons - shouldn't be stored in AssetsStorage where possible
                     data:        Option[Array[Byte]]   = None
                    ) {

  import AssetData._

  override def toString: String =
    s"""
       |AssetData:
       | id:            $id
       | mime:          $mime
       | sizeInBytes:   $sizeInBytes
       | status:        $status
       | rId:           $remoteId
       | token:         $token
       | otrKey:        $otrKey
       | sha:           $sha
       | preview:       $previewId
       | metaData:      $metaData
       | data (length): ${data.map(_.length).getOrElse(0)}
       | other fields:  $name, $source, $proxyPath
    """.stripMargin

  lazy val size = data.fold(sizeInBytes)(_.length)

  //be careful when accessing - can be expensive
  lazy val data64 = data.map(Base64.encodeBase64)

  lazy val fileExtension = mime.extension

  lazy val remoteData = (remoteId, token, otrKey, sha) match {
    case (None, None, None, None) => Option.empty[RemoteData]
    case _ => Some(RemoteData(remoteId, token, otrKey, sha))
  }

  lazy val cacheKey = {
    val key = proxyPath.map(CacheKey).orElse(source.map(cacheKeyFrom)).getOrElse(CacheKey(id.str))
    verbose(s"created cache key: $key for asset: $id")
    key
  }

  def loadRequest = {
    val req = (remoteData, source, proxyPath) match {
      case (Some(rData), _, _)                      => WireAssetRequest(cacheKey, id, rData, mime)
      case (_, Some(uri), _) if isExternalUri(uri)  => External(cacheKey, uri)
      case (_, Some(uri), _)                        => LocalAssetRequest(cacheKey, uri, mime, name)
      case (_, None, Some(path))                    => Proxied(cacheKey, path)
      case _                                        => CachedAssetRequest(cacheKey, mime, name)
    }
    verbose(s"loadRequest returning: $req")
    req
  }

  val (isImage, isVideo, isAudio) = this match {
    case IsImage() => (true, false, false)
    case IsVideo() => (false, true, false)
    case IsAudio() => (false, false, true)
    case _         => (false, false, false)
  }

  val tag = this match {
    case IsImageWithTag(t) => t
    case _ => Image.Tag.Empty
  }

  val (width, height) = this match {
    case WithDimensions(dim) => dim
    case _ => (W(0), H(0))
  }

  def copyWithRemoteData(remoteData: RemoteData) = {
    val res = copy(
      remoteId  = remoteData.remoteId,
      token     = remoteData.token,
      otrKey    = remoteData.otrKey,
      sha       = remoteData.sha256
    )
    res.copy(status = res.remoteData.fold(res.status)(_ => UploadDone))
  }
}

object AssetData {

  def decodeData(data64: String): Array[Byte] = Base64.decodeBase64(data64)

  //TODO WireContentProvider in non-android way...
  def cacheKeyFrom(uri: URI): CacheKey = CacheKey(uri.toString)
//  def cacheKeyFrom(uri: URI): CacheKey = WireContentProvider.CacheUri.unapply(ZMessaging.context)(uri).getOrElse(CacheKey(uri.toString))

  def isExternalUri(uri: URI): Boolean = Option(uri.getScheme).forall(_.startsWith("http"))

  //simplify handling remote data from asset data
  case class RemoteData(remoteId: Option[RAssetId]    = None,
                        token:    Option[AssetToken]  = None,
                        otrKey:   Option[AESKey]      = None,
                        sha256:   Option[Sha256]      = None
                       )

  //needs to be def to create new id each time. "medium" tag ensures it will not be ignored by MessagesService
  def newImageAsset(id: AssetId = AssetId(), tag: Image.Tag) = AssetData(id = id, metaData = Some(AssetMetaData.Image(W(0), H(0), tag)))

  val Empty = AssetData()

  object WithMetaData {
    def unapply(asset: AssetData): Option[AssetMetaData] = asset.metaData
  }

  object IsImage {
    def unapply(asset: AssetData): Boolean = Mime.Image.unapply(asset.mime) || (asset.metaData match {
      case Some(AssetMetaData.Image(_, _, _)) => true
      case _ => false
    })
  }

  object IsVideo {
    def unapply(asset: AssetData): Boolean = Mime.Video.unapply(asset.mime) || (asset.metaData match {
      case Some(AssetMetaData.Video(_, _, _)) => true
      case _ => false
    })
  }

  object IsAudio {
    def unapply(asset: AssetData): Boolean = Mime.Audio.unapply(asset.mime) || (asset.metaData match {
      case Some(AssetMetaData.Audio(_, _)) => true
      case _ => false
    })
  }

  object WithDimensions {
    def unapply(asset: AssetData): Option[(W, H)] = asset.metaData match {
      case Some(Dimensions(w, h)) => Some((w, h))
      case _ => None
    }
  }

  object IsImageWithTag {
    def unapply(asset: AssetData): Option[Image.Tag] = asset.metaData match {
      case Some(AssetMetaData.Image(_, _, tag)) => Some(tag)
      case _ => None
    }
  }

  object WithDuration {
    def unapply(asset: AssetData): Option[Duration] = asset.metaData match {
      case Some(AssetMetaData.HasDuration(duration)) => Some(duration)
      case _ => None
    }
  }

  object WithPreview {
    def unapply(asset: AssetData): Option[AssetId] = asset.previewId
  }

  object WithRemoteId {
    def unapply(asset: AssetData): Option[RAssetId] = asset.remoteId
  }

  object WithStatus {
    def unapply(asset: AssetData): Option[AssetStatus] = Some(asset.status)
  }

  object WithSource {
    def unapply(asset: AssetData): Option[URI] = asset.source
  }

  val MaxAllowedAssetSizeInBytes = 26214383L
  // 25MiB - 32 + 15 (first 16 bytes are AES IV, last 1 (!) to 16 bytes are padding)
  val MaxAllowedBackendAssetSizeInBytes = 26214400L

  // 25MiB

  case class ProcessingTaskKey(id: AssetId)

  case class UploadTaskKey(id: AssetId)

//  implicit object AssetDataDao extends Dao[AssetData, AssetId] {
//    val Id    = id[AssetId]('_id, "PRIMARY KEY").apply(_.id)
//    val Asset = text[AssetType]('asset_type, _.name, AssetType.valueOf)(_ => AssetType.Empty)
//    val Data = text('data)(JsonEncoder.encodeString(_))
//
//    override val idCol = Id
//    override val table = Table("Assets", Id, Asset, Data)
//
//    override def apply(implicit cursor: Cursor): AssetData = {
//      val tpe: AssetType = Asset
//      tpe match {
//        case AssetType.Image => JsonDecoder.decode(Data)(ImageAssetDataDecoder)
//        case AssetType.Any   => JsonDecoder.decode(Data)(AnyAssetDataDecoder)
//        case _               => JsonDecoder.decode(Data)(AssetDataDecoder)
//      }
//    }
//  }

  implicit lazy val AssetDataEncoder: JsonEncoder[AssetData] = new JsonEncoder[AssetData] {
    override def apply(data: AssetData): JSONObject = JsonEncoder { o =>
      o.put("id",           data.id.str)
      o.put("mime",         data.mime.str)
      o.put("sizeInBytes",  data.sizeInBytes)
      o.put("status",       JsonEncoder.encode(data.status))
      data.remoteId     foreach (v => o.put("remoteId",     v.str))
      data.token        foreach (v => o.put("token",        v.str))
      data.otrKey       foreach (v => o.put("otrKey",       v.str))
      data.sha          foreach (v => o.put("sha256",       v.str))
      data.name         foreach (v => o.put("name",         v))
      data.previewId    foreach (v => o.put("preview",      v.str))
      data.metaData     foreach (v => o.put("metaData",     JsonEncoder.encode(v)))
      data.source       foreach (v => o.put("source",       v.toString))
      data.proxyPath    foreach (v => o.put("proxyPath",    v))
      data.data64       foreach (v => o.put("data64",       v))
    }
  }

  lazy val AssetDataDecoder: JsonDecoder[AssetData] = new JsonDecoder[AssetData] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): AssetData =
      AssetData(
        'id,
        Mime('mime),
        'sizeInBytes,
        JsonDecoder[AssetStatus]('status),
        'remoteId, 'token, 'otrKey, 'sha256,
        'name,
        'preview,
        opt[AssetMetaData]('metaData),
        'source,
        'proxyPath,
        decodeOptString('data).map(decodeData)
      )
  }
}

case class AssetToken(str: String) extends AnyVal

object AssetToken extends (String => AssetToken)
