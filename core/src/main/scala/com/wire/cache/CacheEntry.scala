package com.wire.cache

import java.io.{File, OutputStream}
import java.net.URI

import com.wire.data.{AssetId, IdGen, UId}

trait CacheEntry extends LocalData {
  def outputStream: OutputStream

  def copyDataToFile(): File
}

case class CacheKey(str: String)

object CacheKey extends (String => CacheKey) {
  //any appended strings should be url friendly
  def decrypted(key: CacheKey) = CacheKey(s"${key.str}_decr_")
  def fromAssetId(id: AssetId) = CacheKey(s"${id.str}")
  def fromUri(uri: URI) = CacheKey(uri.toString)
}
