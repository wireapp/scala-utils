package com.wire.network

import java.io.File
import java.nio.ByteBuffer

//TODO implement/import JVM friendly version of this class from Koushikdutta
trait ByteBufferList {

  def size: Int

  def remove(): ByteBuffer

  def recycle()
}

object ByteBufferList {
  def reclaim(byteBuffer: ByteBuffer): Unit = ()
}

trait Part {

}

class FilePart(name: String, file: File) extends Part {

}

