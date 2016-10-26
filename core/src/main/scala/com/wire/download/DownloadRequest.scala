package com.wire.download

sealed trait DownloadRequest {
  val cacheKey: String
}
