package com.wire.cache

import java.io.{File, OutputStream}

trait CacheEntry extends LocalData {
  def outputStream: OutputStream

  def copyDataToFile(): File
}
