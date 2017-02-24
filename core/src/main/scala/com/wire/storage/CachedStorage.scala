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

import com.wire.data.Managed
import com.wire.db.{Dao, Database}
import com.wire.logging.ZLog.verbose
import com.wire.macros.logging.LogTag
import com.wire.macros.returning
import com.wire.reactive.{AggregatingSignal, EventStream, Signal}
import com.wire.threading.SerialDispatchQueue
import org.apache.commons.collections4.map.LRUMap

import scala.collection.breakOut
import scala.collection.JavaConverters._
import scala.collection.generic.CanBuild
import scala.concurrent.Future


trait CachedStorage[K, V] {

  protected implicit def tag: LogTag
  protected implicit val dispatcher = new SerialDispatchQueue()

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
  def find[A, B](predicate: V => Boolean, search: Database => Managed[TraversableOnce[V]], mapping: V => A)(implicit cb: CanBuild[A, B]): Future[B]
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

  def optSignal(key: K): Signal[Option[V]]

  def signal(key: K): Signal[V]

  def insert(v: V): Future[V]
  //
  //  def insert(vs: Traversable[V]): Future[Set[V]]

  def get(key: K): Future[Option[V]]

  //  def getOrCreate(key: K, creator: => V): Future[V]
  //
  //  def list(): Future[Seq[V]]
  //
  def getAll(keys: Traversable[K]): Future[Seq[Option[V]]]
  //
  def update(key: K, updater: V => V): Future[Option[(V, V)]]
  //
  //  def updateAll(updaters: scala.collection.Map[K, V => V]): Future[Seq[(V, V)]]
  //
  //  def updateAll2(keys: Iterable[K], updater: V => V): Future[Seq[(V, V)]]
  //
  def updateOrCreate(key: K, updater: V => V, creator: => V): Future[V]
  //
  def updateOrCreateAll(updaters: K Map (Option[V] => V)): Future[Set[V]]
  //
  def updateOrCreateAll2(keys: Iterable[K], updater: ((K, Option[V]) => V)): Future[Set[V]]
  //
  //  protected def updateInternal(key: K, updater: V => V)(current: V): Future[Option[(V, V)]]
  //
  //  def put(key: K, value: V): Future[V]
  //
  //  def getRawCached(key: K): Option[V]
  //
  def remove(key: K): Future[Unit]
  //
  //  def remove(keys: Iterable[K]): Future[Unit]
  //
  //  def cacheIfNotPresent(key: K, value: V): Future[Option[V]]
}

/**
  * Note, any operation that hits the database should also hit the cache to ensure that the LRU ordering
  * changes
  */
abstract class LRUCacheStorage[K, V](cacheSize: Int, dao: Dao[V, K], db: Database)(implicit val tag: LogTag) extends CachedStorage[K, V] {

  override val onAdded   = EventStream[Seq[V]]()
  override val onUpdated = EventStream[Seq[(V, V)]]()
  override val onDeleted = EventStream[Seq[K]]()
  override val onChanged = onAdded.union(onUpdated.map(_.map(_._2)))

  protected val cache = new LRUMap[K, Option[V]](cacheSize)

  def get(key: K) = cachedOrElse(key, Future {cachedOrElse(key, loadFromDb(key))}.flatMap(identity))

  def insert(value: V) = put(dao.idExtractor(value), value)

  def remove(key: K): Future[Unit] = Future {
    cache.put(key, None)
    returning(db { delete(Seq(key))(_) }) { _ => onDeleted ! Seq(key) }
  }

  def updateOrCreateAll(updaters: K Map (Option[V] => V)) =
    updateOrCreateAll2(updaters.keys.toVector, { (key, v) => updaters(key)(v)})

  def updateOrCreateAll2(keys: Iterable[K], updater: ((K, Option[V]) => V)) =
    if (keys.isEmpty) Future successful Set.empty[V]
    else {
      verbose(s"updateOrCreateAll($keys)")
      getAll(keys) flatMap { values =>
        val loaded: Map[K, Option[V]] = keys.iterator.zip(values.iterator).map { case (k, v) => k -> Option(cache.get(k)).flatten.orElse(v) }.toMap
        val toSave = Vector.newBuilder[V]
        val added = Vector.newBuilder[V]
        val updated = Vector.newBuilder[(V, V)]

        val result = keys .map { key =>
          val current = loaded.get(key).flatten
          val next = updater(key, current)
          current match {
            case Some(c) if c != next =>
              cache.put(key, Some(next))
              toSave += next
              updated += (c -> next)
            case None =>
              cache.put(key, Some(next))
              toSave += next
              added += next
            case Some(_) => // unchanged, ignore
          }
          next
        } .toSet

        val addedResult = added.result
        val updatedResult = updated.result

        returning (db { save(toSave.result)(_) } .map { _ => result }) { _ =>
          if (addedResult.nonEmpty) onAdded ! addedResult
          if (updatedResult.nonEmpty) onUpdated ! updatedResult
        }
      }
    }

  def getAll(keys: Traversable[K]) = {
    val cachedEntries = keys.flatMap { key => Option(cache.get(key)) map { value => (key, value) } }.toMap
    val missingKeys = keys.toSet -- cachedEntries.keys

    db.read { db => load(missingKeys)(db) } map { loadedEntries =>
      val loadedMap: Map[K, Option[V]] = loadedEntries.map { value =>
        val key = dao.idExtractor(value)
        Option(cache.get(key)).map(m => (key, m)).getOrElse {
          cache.put(key, Some(value))
          (key, Some(value))
        }
      }(breakOut)

      keys .map { key =>
        returning(Option(cache.get(key)).orElse(loadedMap.get(key).orElse(cachedEntries.get(key))).getOrElse(None)) { cache.put(key, _) }
      } (breakOut) : Vector[Option[V]]
    }
  }

  //  protected def load(key: K): Option[V]
  def find[A, B](predicate: (V) => Boolean, search: (Database) => Managed[TraversableOnce[V]], mapping: (V) => A)(implicit cb: CanBuild[A, B]) = Future {
    val matches = cb.apply()
    val snapshot = cache.clone().asScala

    snapshot.foreach {
      case (k, Some(v)) if predicate(v) => matches += mapping(v)
      case _ =>
    }
    (snapshot.keySet, matches)
  } flatMap { case (wasCached, matches) =>
    db.read { database =>
      val uncached = Map.newBuilder[K, V]
      search(database).acquire { rows =>
        rows.foreach { v =>
          val k = dao.idExtractor(v)
          if (! wasCached(k)) {
            matches += mapping(v)
            uncached += k -> v
          }
        }

        (matches.result, uncached.result)
      }
    }
  } map { case (results, uncached) =>
    // cache might have changed already at this point, but that would mean the write would have been issued after this read anyway, so we can safely return the outdated values here

    uncached.foreach { case (k, v) =>
      if (cache.get(k) eq null) cache.put(k, Some(v))
    }

    results
  }

  def update(key: K, updater: V => V) = get(key) flatMap { loaded =>
    val prev = Option(cache.get(key)).getOrElse(loaded)
    prev.fold(Future successful Option.empty[(V, V)]) { updateInternal(key, updater)(_) }
  }

  def updateOrCreate(key: K, updater: (V) => V, creator: => V) = get(key) flatMap { loaded =>
    val prev = Option(cache.get(key)).getOrElse(loaded)
    prev.fold { put(key, creator) } { v => updateInternal(key, updater)(v).map(_.fold(v)(_._2)) }
  }

  def optSignal(key: K) = {
    val changeOrDelete = onChanged(key).map(Option(_)).union(onRemoved(key).map(_ => Option.empty[V]))
    new AggregatingSignal[Option[V], Option[V]](changeOrDelete, get(key), { (_, v) => v })
  }

  def signal(key: K) = optSignal(key).collect { case Some(v) => v }

  private def cachedOrElse(key: K, default: => Future[Option[V]]): Future[Option[V]] =
    Option(cache.get(key)).fold(default)(Future.successful)

  private def loadFromDb(key: K) = db.read {load(key)(_)} map { value =>
    Option(cache.get(key)).getOrElse {
      cache.put(key, value)
      value
    }
  }

  private def load(key: K)(implicit db: Database) = dao.getById(key)

  private def load(keys: Set[K])(implicit db: Database) = dao.getAll(keys)

  private def save(values: Seq[V])(implicit db: Database) = dao.insertOrReplace(values)

  private def put(key: K, value: V) = {
    verbose(s"Inserting: $key, $value")
    cache.put(key, Some(value))
    returning(db {save(Seq(value))(_)}.map( _ => value)) { _ =>
      onAdded ! Seq(value)
    }
  }

  protected def updateInternal(key: K, updater: V => V)(current: V): Future[Option[(V, V)]] = {
    val updated = updater(current)
    if (updated == current) Future.successful(None)
    else {
      cache.put(key, Some(updated))
      returning(db { save(Seq(updated))(_) }.map { _ => Some((current, updated)) }) { _ =>
        onUpdated ! Seq((current, updated))
      }
    }
  }

  private def delete(keys: Iterable[K])(implicit db: Database): Unit = dao.deleteEvery(keys)

  private def onChanged(key: K): EventStream[V] = onChanged.map(_.view.filter(v => dao.idExtractor(v) == key).lastOption).collect { case Some(v) => v }

  private def onRemoved(key: K): EventStream[K] = onDeleted.map(_.view.filter(_ == key).lastOption).collect { case Some(k) => k }

}

