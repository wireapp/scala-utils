package com.wire.assets

import com.wire.data.AssetId

import scala.concurrent.Future

trait AssetStorage {
  def getAsset(id: AssetId): Future[Option[AssetData]]
}
