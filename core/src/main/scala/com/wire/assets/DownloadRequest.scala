package com.wire.assets

import java.io.InputStream
import java.net.URI

import com.wire.cache.CacheKey
import com.wire.data.{AssetId, Mime}
import com.wire.network.Request

sealed trait DownloadRequest {
  val cacheKey: CacheKey
}

object DownloadRequest {

  sealed trait AssetRequest extends DownloadRequest {
    val mime: Mime
    val name: Option[String]
  }

  case class CachedAssetRequest(cacheKey: CacheKey, mime: Mime, name: Option[String]) extends AssetRequest

  case class LocalAssetRequest(cacheKey: CacheKey, uri: URI, mime: Mime, name: Option[String]) extends AssetRequest

  sealed trait ExternalAssetRequest extends AssetRequest {
    def request: Request[Unit]

    override val mime: Mime = Mime.Unknown
    override val name: Option[String] = None
  }

  case class WireAssetRequest(cacheKey: CacheKey, assetId: AssetId, remoteData: AssetData.RemoteData, mime: Mime, name: Option[String] = None) extends AssetRequest

  case class AssetFromInputStream(cacheKey: CacheKey, stream: () => InputStream, mime: Mime = Mime.Unknown, name: Option[String] = None) extends DownloadRequest

  case class VideoAsset(cacheKey: CacheKey, uri: URI, mime: Mime = Mime.Unknown, name: Option[String] = None) extends DownloadRequest

  case class UnencodedAudioAsset(cacheKey: CacheKey, name: Option[String]) extends DownloadRequest

  case class External(cacheKey: CacheKey, uri: URI) extends ExternalAssetRequest {
    override def request: Request[Unit] = Request[Unit](absoluteUri = Some(uri), requiresAuthentication = false)
  }

  // external asset downloaded from wire proxy, path is relative to our proxy endpoint
  case class Proxied(cacheKey: CacheKey, path: String) extends ExternalAssetRequest {
    override def request: Request[Unit] = Request.Get(path)
  }
}
