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
package com.wire

import java.util.Random
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.wire.events.{Signal, SourceSignal}
import com.wire.threading.Threading

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationLong}
import scala.language.implicitConversions

package object testutils {

  type Print = Boolean

  implicit lazy val ec = Threading.Background
  implicit lazy val printVals: Print = false
  implicit lazy val duration = 3.seconds

  implicit class TestInt(val a: Int) extends AnyVal {
    def times(f: => Unit): Unit = {
      require(a >= 1, "number of times should be at least 1")
      (1 to a) foreach (_ => f)
    }
  }

  implicit class RichLatch(val l: CountDownLatch) extends AnyVal {
    def awaitDefault() = l.await(duration.toMillis, TimeUnit.MILLISECONDS)
    def awaitDuration(d: Duration) = l.await(d.toMillis, TimeUnit.MILLISECONDS)
  }

  implicit class RichAtomicReference[V](val ref: AtomicReference[V]) extends AnyVal {
    @tailrec
    final def update(updater: V => V): V = {
      val current = ref.get
      val updated = updater(current)
      if (ref.compareAndSet(current, updated)) updated
      else ref.update(updater)
    }
  }

  def signalTest[A](signal: Signal[A])(test: A => Boolean)(trigger: => Unit)(implicit printVals: Print): Unit = {
    signal.disableAutowiring()
    trigger
    if (printVals) println("****")
    Await.result(signal.filter { value =>
      if (printVals) println(value)
      test(value)
    }.head, duration)
    if (printVals) println("****")
  }

  object Uncontended {
    private val localRandom = new ThreadLocal[Random] {
      override def initialValue: Random = new Random
    }

    def random: Random = localRandom.get
  }

  class IntSignal(v: Int = 0) extends SourceSignal[Int](Some(v)) {
    var isWired = false
    override protected def onWire(): Unit = isWired = true
    override protected def onUnwire(): Unit = isWired = false
  }
}
