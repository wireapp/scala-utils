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
import com.wire.assets.AssetMetaData.Image
import com.wire.assets.AssetStatus.UploadDone
import com.wire.data.{AssetId, Mime}
import com.wire.db.Database
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.reactive.EventStream
import com.wire.storage.{CachedStorage, LRUCacheStorage}
import com.wire.utils.RichFutureOpt

import scala.concurrent.Future

trait AssetStorage extends CachedStorage[AssetId, AssetData] {
  def getAsset(id: AssetId): Future[Option[AssetData]]

  def mergeOrCreateAsset(newData: AssetData): Future[Option[AssetData]]
  def mergeOrCreateAsset(newData: Option[AssetData]): Future[Option[AssetData]]

}

class DefaultAssetStorage(db: Database) extends LRUCacheStorage[AssetId, AssetData](100, AssetDataDao, db) with AssetStorage {
  override def getAsset(id: AssetId) = ???

  val onUploadFailed = EventStream[AssetData]()

  //allows overwriting of asset data
  def updateAsset(id: AssetId, updater: AssetData => AssetData): Future[Option[AssetData]] = update(id, updater).mapOpt {
    case (_, updated) => Some(updated)
  }

  def mergeOrCreateAsset(newData: AssetData): Future[Option[AssetData]] = mergeOrCreateAsset(Some(newData))
  def mergeOrCreateAsset(newData: Option[AssetData]): Future[Option[AssetData]] = newData.map(nd => updateOrCreate(nd.id, cur => merge(cur, nd), nd).map(Some(_))).getOrElse(Future.successful(None))

  //Useful for receiving parts of an asset message or remote data. Note, this only merges non-defined properties, any current data remaining as is.
  private def merge(cur: AssetData, newData: AssetData): AssetData = {

    val metaData = cur.metaData match {
      case None => newData.metaData
      case Some(AssetMetaData.Image(w, h, tag)) if tag == Image.Tag.Empty => Image(w, h, newData.tag)
      case _ => cur.metaData
    }

    val res = cur.copy(
      mime        = if (cur.mime == Mime.Unknown)  newData.mime         else cur.mime,
      sizeInBytes = if (cur.sizeInBytes == 0)      newData.sizeInBytes  else cur.sizeInBytes,
      remoteId    = if (cur.remoteId.isEmpty)      newData.remoteId     else cur.remoteId,
      token       = if (cur.token.isEmpty)         newData.token        else cur.token,
      otrKey      = if (cur.otrKey.isEmpty)        newData.otrKey       else cur.otrKey,
      sha         = if (cur.sha.isEmpty)           newData.sha          else cur.sha,
      name        = if (cur.name.isEmpty)          newData.name         else cur.name,
      previewId   = if (cur.previewId.isEmpty)     newData.previewId    else cur.previewId,
      metaData    = if (cur.metaData.isEmpty)      newData.metaData     else cur.metaData,
      proxyPath   = if (cur.proxyPath.isEmpty)     newData.proxyPath    else cur.proxyPath,
      source      = if (cur.source.isEmpty)        newData.source       else cur.source,
      data        = if (cur.data.isEmpty)          newData.data         else cur.data
      //TODO Dean: giphy source and caption
    )
    //After merging the two asset data objects, update the resulting status if we now have remote data
    res.copy(status = res.remoteData.fold(res.status)(_ => UploadDone))
  }

}
