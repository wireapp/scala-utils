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

import com.wire.assets.AssetClient.{Retention, UploadResponse}
import com.wire.cache.LocalData
import com.wire.data.{Mime, RAssetId}
import com.wire.network.ZNetClient.ErrorOrResponse
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
