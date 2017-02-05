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

import com.wire.assets.AssetData.AssetDataDao
import com.wire.data.AssetId
import com.wire.db.Database
import com.wire.storage.LRUCacheStorage
import com.wire.logging.ZLog.ImplicitTag._

import scala.concurrent.Future

trait AssetStorage {
  def getAsset(id: AssetId): Future[Option[AssetData]]
}

class DefaultAssetStorage(db: Database) extends LRUCacheStorage[AssetId, AssetData](100, AssetDataDao, db) with AssetStorage {
  override def getAsset(id: AssetId) = ???

}
