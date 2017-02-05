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

import com.wire.assets.AssetData
import com.wire.data.AssetId
import com.wire.db.{Dao, DaoIdOps, Database}
import com.wire.logging.ZLog
import com.wire.logging.ZLog.verbose
import com.wire.macros.logging.LogTag
import com.wire.macros.returning
import com.wire.reactive.EventStream
import com.wire.threading.SerialDispatchQueue
import org.apache.commons.collections4.map.LRUMap

import scala.collection._
import scala.concurrent.Future

trait CachedStorage[K, V] {
  val onAdded:   EventStream[Seq[V]]
  val onUpdated: EventStream[Seq[(V, V)]]
  val onDeleted: EventStream[Seq[K]]
  val onChanged: EventStream[Seq[V]]

  //  protected def load(key: K): Option[V]
  //
  //  protected def load(keys: Set[K]): Seq[V]
  //
  //  protected def save(values: Seq[V]): Unit
  //
  //  protected def delete(keys: Iterable[K]): Unit
  //
  //  def find[A, B](predicate: V => Boolean, search: Database => Managed[TraversableOnce[V]], mapping: V => A): Future[B]
  //
  //  def filterCached(f: V => Boolean): Future[Vector[V]]
  //
  //  def foreachCached(f: V => Unit): Future[Unit]
  //
  //  def deleteCached(predicate: V => Boolean): Future[Unit]
  //
  //  def onChanged(key: K): EventStream[V]
  //
  //  def onRemoved(key: K): EventStream[K]
  //
  //  def optSignal(key: K): Signal[Option[V]]
  //
  //  def signal(key: K): Signal[V]
  //
  def insert(v: V): Future[V]
  //
  //  def insert(vs: Traversable[V]): Future[Set[V]]

  def get(key: K): Future[Option[V]]

  //  def getOrCreate(key: K, creator: => V): Future[V]
  //
  //  def list(): Future[Seq[V]]
  //
  //  def getAll(keys: Traversable[K]): Future[Seq[Option[V]]]
  //
  //  def update(key: K, updater: V => V): Future[Option[(V, V)]]
  //
  //  def updateAll(updaters: scala.collection.Map[K, V => V]): Future[Seq[(V, V)]]
  //
  //  def updateAll2(keys: Iterable[K], updater: V => V): Future[Seq[(V, V)]]
  //
  //  def updateOrCreate(key: K, updater: V => V, creator: => V): Future[V]
  //
  //  def updateOrCreateAll(updaters: K Map (Option[V] => V)): Future[Set[V]]
  //
  //  def updateOrCreateAll2(keys: Iterable[K], updater: ((K, Option[V]) => V)): Future[Set[V]]
  //
  //  protected def updateInternal(key: K, updater: V => V)(current: V): Future[Option[(V, V)]]
  //
  //  def put(key: K, value: V): Future[V]
  //
  //  def getRawCached(key: K): Option[V]
  //
  //  def remove(key: K): Future[Unit]
  //
  //  def remove(keys: Iterable[K]): Future[Unit]
  //
  //  def cacheIfNotPresent(key: K, value: V): Future[Option[V]]
}

/**
  * Note, any operation that hits the database should also hit the cache to ensure that the LRU ordering
  * changes
  */
abstract class LRUCacheStorage[K, V](cacheSize: Int, dao: Dao[V, K], db: Database)(implicit tag: LogTag) extends CachedStorage[K, V] {

  private implicit val dispatcher = new SerialDispatchQueue()

  override val onAdded   = EventStream[Seq[V]]()
  override val onUpdated = EventStream[Seq[(V, V)]]()
  override val onDeleted = EventStream[Seq[K]]()
  override val onChanged = onAdded.union(onUpdated.map(_.map(_._2)))

  protected val cache = new LRUMap[K, Option[V]](cacheSize)

  def get(key: K) = cachedOrElse(key, Future {cachedOrElse(key, loadFromDb(key))}.flatMap(identity))

  def insert(value: V) = addInternal(dao.idExtractor(value), value)

  private def cachedOrElse(key: K, default: => Future[Option[V]]): Future[Option[V]] =
    Option(cache.get(key)).fold(default)(Future.successful)

  private def loadFromDb(key: K) = db.read {load(key)(_)} map { value =>
    Option(cache.get(key)).getOrElse {
      cache.put(key, value)
      value
    }
  }

  private def load(key: K)(implicit db: Database) = dao.getById(key)

  private def save(values: Seq[V])(implicit db: Database) = dao.insertOrReplace(values)

  private def addInternal(key: K, value: V) = {
    verbose(s"Inserting: $key, $value")
    cache.put(key, Some(value))
    returning(db {save(Seq(value))(_)}.map( _ => value)) { _ =>
      onAdded ! Seq(value)
    }
  }

}

