package com.wire.download

import com.wire.cache.{CacheEntry, Expiration}
import com.wire.download.ProgressIndicator.ProgressData
import com.wire.events.Signal
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



