package com.wire.assets

import com.wire.cryptography.{AESKey, Sha256}
import com.wire.data.{AssetId, RAssetDataId}

class AssetData {

}

case class RemoteKey(str: String) extends AnyVal

case class AssetToken(str: String) extends AnyVal

case class AssetKey(remoteId: Either[RAssetDataId, RemoteKey], token: Option[AssetToken], otrKey: AESKey, sha256: Sha256) {
  val cacheKey = remoteId.fold(_.str, _.str)
  val assetId = AssetId(cacheKey)
}
