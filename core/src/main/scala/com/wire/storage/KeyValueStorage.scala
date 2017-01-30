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
  package com.wire.storage

import com.wire.storage.KeyValueStorage.KeyValuePref
import com.wire.storage.Preference.PrefCodec

import scala.concurrent.{ExecutionContext, Future}

trait KeyValueStorage {
  def getPref(key: String): Future[Option[String]]
  def setPref(key: String, value: String): Future[KeyValueData]
  def delPref(key: String): Future[Unit]
  def decodePref[A](key: String, dec: String => A): Future[Option[A]]

  def keyValuePref[A: PrefCodec](key: String, default: A)(implicit executionContext: ExecutionContext) = new KeyValuePref[A](this, key, default)
}

object KeyValueStorage {

  class KeyValuePref[A](storage: KeyValueStorage, key: String, val default: A)(implicit val trans: PrefCodec[A], implicit val dispatcher: ExecutionContext) extends Preference[A] {
    def apply(): Future[A] = storage.decodePref(key, trans.decode).map(_.getOrElse(default))
    def :=(value: A): Future[Unit] = {
      storage.setPref(key, trans.encode(value)) .map { _ => signal ! value }
    }
  }
}

case class KeyValueData(key: String, value: String)
