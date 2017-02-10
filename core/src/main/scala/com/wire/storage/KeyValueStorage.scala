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

import com.wire.db.{Cursor, Dao, Database}
import com.wire.storage.KeyValueData.KeyValueDataDao
import com.wire.storage.KeyValueStorage.KeyValuePref
import com.wire.storage.Preference.PrefCodec

import scala.concurrent.{ExecutionContext, Future}

trait KeyValueStorage extends CachedStorage[String, KeyValueData] {
  def getPref(key: String):                         Future[Option[String]] = get(key).map(_.map(_.value))
  def setPref(key: String, value: String):          Future[KeyValueData]   = insert(KeyValueData(key, value))
  def delPref(key: String):                         Future[Unit]           = remove(key)
  def decodePref[A](key: String, dec: String => A): Future[Option[A]]      = getPref(key).map(_.map(dec))

  def keyValuePref[A: PrefCodec](key: String, default: A)(implicit executionContext: ExecutionContext) = new KeyValuePref[A](this, key, default)
}

object KeyValueStorage {

  class KeyValuePref[A](storage: KeyValueStorage, key: String, val default: A)(implicit val trans: PrefCodec[A], implicit val dispatcher: ExecutionContext) extends Preference[A] {
    def apply(): Future[A] = storage.decodePref(key, trans.decode).map(_.getOrElse(default))

    def :=(value: A): Future[Unit] = storage.setPref(key, trans.encode(value)).map { _ => signal ! value }
  }
}

case class KeyValueData(key: String, value: String)


object KeyValueData {

  implicit object KeyValueDataDao extends Dao[KeyValueData, String] {
    import com.wire.db.Col._
    val Key = text('key, "PRIMARY KEY")(_.key)
    val Value = text('value)(_.value)

    override val idCol = Key
    override val table = Table("KeyValues", Key, Value)

    override def apply(implicit cursor: Cursor): KeyValueData = KeyValueData(Key, Value)
  }

}

class DefaultKVStorage(db: Database) extends LRUCacheStorage[String, KeyValueData](128, KeyValueDataDao, db) with KeyValueStorage

