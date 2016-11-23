package com.wire.assets

import com.wire.assets.AssetClient.{Retention, UploadResponse}
import com.wire.cache.LocalData
import com.wire.data.{Mime, RAssetId}
import com.wire.network.ClientEngine.ErrorOrResponse
import org.threeten.bp.Instant

trait AssetClient {
  def uploadAsset(encryptedData: LocalData, mime: Mime, public: Boolean = false, retention: Retention = Retention.Persistent): ErrorOrResponse[UploadResponse]
}

object AssetClient {

  sealed abstract class Retention(val value: String)
  object Retention {
    case object Eternal extends Retention("eternal")
    case object Persistent extends Retention("persistent")
    case object Volatile extends Retention("volatile")
  }

  case class UploadResponse(key: RAssetId, expires: Option[Instant], token: Option[AssetToken])
}