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
