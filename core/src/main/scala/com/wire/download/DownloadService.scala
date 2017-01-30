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
  package com.wire.download

import com.wire.cache.{CacheEntry, Expiration}
import com.wire.download.ProgressIndicator.ProgressData
import com.wire.reactive.Signal
import com.wire.threading.CancellableFuture

import scala.concurrent.Future
import scala.concurrent.duration._

trait DownloadService {

  def getDownloadState(key: String): Signal[ProgressData]

  def cancel(req: DownloadRequest): Future[Unit] = cancel(req.cacheKey)

  def cancel(key: String): Future[Unit]

  def download[A <: DownloadRequest](req: A, force: Boolean = false)(implicit loader: Downloader[A], expires: Expiration = DownloadService.DefaultExpiryTime): CancellableFuture[Option[CacheEntry]]
}

object DownloadService {
  val DefaultExpiryTime = 7.days
}
