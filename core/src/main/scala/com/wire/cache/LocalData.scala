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
  package com.wire.cache

import java.io._

/**
  * Common interface to access locally available data.
  * Unifies access to file, in memory byte array and cache entry.
  */
trait LocalData {
  def file: Option[File] = None
  def byteArray: Option[Array[Byte]] = None
  def delete(): Unit = ()

  def inputStream: InputStream
  def length: Int
}

object LocalData {
  case object Empty extends LocalData {
    override def inputStream: InputStream = new ByteArrayInputStream(Array.empty)
    override def length: Int = 0
  }

  def apply(file: File): LocalData = new LocalFile(file)
  def apply(bytes: Array[Byte]): LocalData = new ArrayData(bytes)
  def apply(stream: => InputStream, len: Int): LocalData = new LocalStream(() => stream, len)

  private class LocalFile(f: File) extends LocalData {
    override def inputStream = new BufferedInputStream(new FileInputStream(f))
    override lazy val length = f.length().toInt
    override def file = Some(f)
    override def delete() = f.delete()

    override def equals(o: scala.Any): Boolean = o match {
      case lf: LocalFile => file == lf.file
      case _ => false
    }
  }

  private class LocalStream(stream: () => InputStream, len: Int) extends LocalData {
    override def length = len
    override def inputStream: InputStream = stream()
  }

  private class ArrayData(bytes: Array[Byte]) extends LocalData {
    override def byteArray: Option[Array[Byte]] = Some(bytes)
    override def inputStream: InputStream = new ByteArrayInputStream(bytes)
    override def length: Int = bytes.length
  }
}
