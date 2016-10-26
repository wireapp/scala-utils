package com.wire.download

import com.wire.cache.CacheEntry
import com.wire.threading.CancellableFuture

sealed trait Downloader[-A <: DownloadRequest] {
  def load(request: A): CancellableFuture[Option[CacheEntry]]
}
