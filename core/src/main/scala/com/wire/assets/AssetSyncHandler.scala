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

import com.wire.messages.MessageData
import com.wire.network.ErrorResponse
import com.wire.network.Response.InternalError
import com.wire.threading.CancellableFuture

import scala.concurrent.Future

trait AssetSyncHandler {
  def uploadAsset(msg: MessageData): Future[Any]
}

class AssetSyncHandlerImpl(storage: AssetStorage) extends AssetSyncHandler {

  import com.wire.threading.Threading.Implicits.Background

  override def uploadAsset(msg: MessageData): Future[Any] = {



    CancellableFuture.lift(storage.getAsset(msg.assetId)).flatMap {
      case None => CancellableFuture successful Left(InternalError(s"no asset found for msg: $msg"))
      case Some(data) if data.status == AssetStatus.UploadCancelled => CancellableFuture.successful(Left(ErrorResponse.Cancelled))
      case Some(data) =>




        CancellableFuture.successful(())
    }
  }
}
