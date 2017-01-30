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

import com.wire.data.{Mime, UId}
import com.wire.cryptography.AESKey
import com.wire.threading.{CancellableFuture, Threading}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

trait CacheService {
  // create new cache entry for file, return the entry immediately
  def createManagedFile(key: Option[AESKey] = None)(implicit timeout: Expiration = CacheService.DefaultExpiryTime): CacheEntry

  def createForFile(key: String = UId().str, mime: Mime = Mime.Unknown, name: Option[String] = None, cacheLocation: Option[File] = None, length: Option[Long] = None)(implicit timeout: Expiration = CacheService.DefaultExpiryTime)

  def addData(key: String, data: Array[Byte])(implicit timeout: Expiration = CacheService.DefaultExpiryTime)

  def addStream[A](key: String, in: => InputStream, mime: Mime = Mime.Unknown, name: Option[String] = None, cacheLocation: Option[File] = None, length: Int = -1, execution: ExecutionContext = Threading.Background)(implicit timeout: Expiration = CacheService.DefaultExpiryTime): Future[CacheEntry]

  def addFile(key: String, src: File, moveFile: Boolean = false, mime: Mime = Mime.Unknown, name: Option[String] = None, cacheLocation: Option[File] = None)(implicit timeout: Expiration = CacheService.DefaultExpiryTime): Future[CacheEntry]

  def move(key: String, entry: LocalData, mime: Mime = Mime.Unknown, name: Option[String] = None, cacheLocation: Option[File] = None)(implicit timeout: Expiration = CacheService.DefaultExpiryTime)

  def extCacheDir: Option[File]

  def intCacheDir: File

  def cacheDir: File = extCacheDir.getOrElse(intCacheDir)

  def getEntry(key: String): Future[Option[CacheEntry]]

  def getDecryptedEntry(key: String)(implicit timeout: Expiration = CacheService.TempDataExpiryTime): Future[Option[CacheEntry]]

  def getOrElse(key: String, default: => Future[CacheEntry])

  def remove(key: String): Future[Unit]

  def remove(entry: CacheEntry): Future[Unit]

  def deleteExpired(): CancellableFuture[Unit]
}

case class Expiration(timeout: Long)

object Expiration {
  import scala.language.implicitConversions

  implicit def in(d: Duration) : Expiration = if (d.isFinite()) Expiration(d.toMillis) else Expiration(1000L * 3600L * 24L * 365L * 1000L) // 1000 years (don't use Long.MaxValue due to overflow dangers)
}

object CacheService {
  val TempDataExpiryTime = 12.hours
  val DefaultExpiryTime = 7.days
}
