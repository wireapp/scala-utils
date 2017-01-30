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
  package com.wire.network

import java.io.{ByteArrayOutputStream, OutputStream}

import com.wire.cache.{CacheEntry, CacheService}
import com.wire.network.ResponseConsumer.ConsumerState
import org.json.{JSONArray, JSONObject}

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

import scala.concurrent.duration._

sealed trait ResponseContent
sealed trait JsonResponse extends ResponseContent
case object EmptyResponse extends ResponseContent
case class StringResponse(value: String) extends ResponseContent
case class JsonObjectResponse(value: JSONObject) extends JsonResponse
case class JsonArrayResponse(value: JSONArray) extends JsonResponse
case class BinaryResponse(value: Array[Byte], mime: String) extends ResponseContent {
  override def toString: String = s"BinaryResponse(${new String(value.take(1024))}, $mime)"
}
case class FileResponse(value: CacheEntry, mime: String) extends ResponseContent

trait ResponseConsumer[T <: ResponseContent] {
  def consume(bb: ByteBufferList): ConsumerState
  def result: Try[T]
}

object ResponseConsumer {

  sealed trait ConsumerState
  object ConsumerState {
    case object Default extends ConsumerState
    case object Done extends ConsumerState // returned when consumer received all required data and further processing can be stopped
  }

  private def copyToStream(bb: ByteBufferList, out: OutputStream): Long = {
    var count = 0L
    while (bb.size > 0) {
      val b = bb.remove()
      count += b.remaining
      out.write(b.array, b.arrayOffset + b.position, b.remaining)
      ByteBufferList.reclaim(b)
    }
    count
  }

  object EmptyResponseConsumer extends ResponseConsumer[ResponseContent] {
    override def result = Success(EmptyResponse)
    override def consume(bb: ByteBufferList): ConsumerState = {
      bb.recycle()
      ConsumerState.Done
    }
  }

  trait InMemoryConsumer[T <: ResponseContent] extends ResponseConsumer[T] {
    val length: Long
    val data = if (length > 0) new ByteArrayOutputStream(length.toInt) else new ByteArrayOutputStream()

    override def consume(bb: ByteBufferList): ConsumerState = {
      copyToStream(bb, data)
      ConsumerState.Default
    }
  }

  class ByteArrayConsumer(val length: Long, mime: String) extends InMemoryConsumer[BinaryResponse] {
    override def result = Success(BinaryResponse(data.toByteArray, mime))
  }

  class StringConsumer(val length: Long) extends InMemoryConsumer[StringResponse] {
    override def result = Success(StringResponse(data.toString("utf8")))
  }

  class JsonConsumer(val length: Long) extends InMemoryConsumer[JsonResponse] {
    override def result = Try {
      val json = data.toString("utf8").trim
      if (json.startsWith("[")) JsonArrayResponse(new JSONArray(json))
      else JsonObjectResponse(new JSONObject(json))
    }
  }

  class FileConsumer(mime: String)(cache: CacheService) extends ResponseConsumer[FileResponse] {
    lazy val entry = cache.createManagedFile()(10.minutes)
    lazy val out = entry.outputStream

    var ex = None: Option[Throwable]

    override def consume(bb: ByteBufferList): ConsumerState = {
      try {
        copyToStream(bb, out)
      } catch {
        case NonFatal(e) =>
          bb.recycle()
          ex = ex.orElse(Some(e))
      }
      ConsumerState.Default
    }

    override def result: Try[FileResponse] =
      try {
        out.close()
        ex.fold(Try(FileResponse(entry, mime))) { e => Failure(e)}
      } catch {
        case NonFatal(e) => Failure(ex.getOrElse(e))
      }
  }
}
