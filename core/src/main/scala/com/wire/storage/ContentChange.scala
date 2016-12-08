package com.wire.storage

sealed trait ContentChange[K, +V]

object ContentChange {
  case class Added  [K, V] (id: K, item: V)             extends ContentChange[K, V]
  case class Updated[K, V] (id: K, prev: V, current: V) extends ContentChange[K, V]
  case class Removed[K, V] (id: K)                      extends ContentChange[K, V]
}