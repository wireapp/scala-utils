package com.wire.storage

import com.wire.data.Managed
import com.wire.db.Database
import com.wire.reactive.{EventStream, Signal}

import scala.collection._
import scala.concurrent.Future

trait Storage[K, V] {
  val onAdded:   EventStream[Seq[V]]
  val onUpdated: EventStream[Seq[(V, V)]]
  val onDeleted: EventStream[Seq[K]]

  val onChanged = onAdded.union(onUpdated.map(_.map(_._2)))

  protected def load(key: K): Option[V]

  protected def load(keys: Set[K]): Seq[V]

  protected def save(values: Seq[V]): Unit

  protected def delete(keys: Iterable[K]): Unit

  def find[A, B](predicate: V => Boolean, search: Database => Managed[TraversableOnce[V]], mapping: V => A): Future[B]

  def filterCached(f: V => Boolean): Future[Vector[V]]

  def foreachCached(f: V => Unit): Future[Unit]

  def deleteCached(predicate: V => Boolean): Future[Unit]

  def onChanged(key: K): EventStream[V]

  def onRemoved(key: K): EventStream[K]

  def optSignal(key: K): Signal[Option[V]]

  def signal(key: K): Signal[V]

  def insert(v: V): Future[V]

  def insert(vs: Traversable[V]): Future[Set[V]]

  def get(key: K): Future[Option[V]]

  def getOrCreate(key: K, creator: => V): Future[V]

  def list(): Future[Seq[V]]

  def getAll(keys: Traversable[K]): Future[Seq[Option[V]]]

  def update(key: K, updater: V => V): Future[Option[(V, V)]]

  def updateAll(updaters: scala.collection.Map[K, V => V]): Future[Seq[(V, V)]]

  def updateAll2(keys: Iterable[K], updater: V => V): Future[Seq[(V, V)]]

  def updateOrCreate(key: K, updater: V => V, creator: => V): Future[V]

  def updateOrCreateAll(updaters: K Map (Option[V] => V)): Future[Set[V]]

  def updateOrCreateAll2(keys: Iterable[K], updater: ((K, Option[V]) => V)): Future[Set[V]]

  protected def updateInternal(key: K, updater: V => V)(current: V): Future[Option[(V, V)]]

  def put(key: K, value: V): Future[V]

  def getRawCached(key: K): Option[V]

  def remove(key: K): Future[Unit]

  def remove(keys: Iterable[K]): Future[Unit]

  def cacheIfNotPresent(key: K, value: V): Future[Option[V]]
}
