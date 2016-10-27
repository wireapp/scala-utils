package com.wire.network

import java.io.File

//TODO implement/import JVM friendly version of this class from Koushikdutta
trait ByteBufferList {

  def size: Int

  def remove(): ByteBuffer

  def recycle()
}

object ByteBufferList {
  def reclaim(byteBuffer: ByteBuffer): Unit = ()
}

//TODO from Java.nio
trait ByteBuffer {
  def remaining: Int
  def array: Array[Byte]
  def arrayOffset: Int
  def position: Int
}

trait Part {

}

class FilePart(name: String, file: File) extends Part {

}

