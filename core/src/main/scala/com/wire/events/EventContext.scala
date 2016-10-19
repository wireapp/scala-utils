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
package com.wire.events

import com.wire.logging.Logging.verbose
import com.wire.macros.logging.{LogTag, logTagFor}

trait EventContext {
  private implicit val logTag: LogTag = logTagFor[EventContext]

  private object lock

  private[this] var started = false
  private[this] var destroyed = false
  private[this] var observers = Set.empty[Subscription]

  protected implicit def eventContext: EventContext = this

  override protected def finalize(): Unit = {
    lock.synchronized {
      if (!destroyed) onContextDestroy()
    }
    super.finalize()
  }

  def onContextStart(): Unit = {
    lock.synchronized {
      if (!started) {
        started = true
        observers foreach (_.subscribe()) // XXX during this, subscribe may call Observable#onWire with in turn may call register which will change observers
      }
    }
  }

  def onContextStop(): Unit = {
    lock.synchronized {
      if (started) {
        started = false
        observers foreach (_.unsubscribe())
      }
    }
  }

  def onContextDestroy(): Unit = {
    lock.synchronized {
      destroyed = true
      val observersToDestroy = observers
      observers = Set.empty
      observersToDestroy foreach (_.destroy())
    }
  }

  def register(observer: Subscription): Unit = {
    lock.synchronized {
      assert(!destroyed, "context already destroyed")

      if (!observers.contains(observer)) {
        observers += observer
        if (started) observer.subscribe()
      }
    }
  }

  def unregister(observer: Subscription): Unit = {
    verbose(s"unregister, observers count: ${observers.size}")
    lock.synchronized {
      observers -= observer
    }
    verbose(s"removed, new count: ${observers.size}")
  }

  def isContextStarted: Boolean = lock.synchronized(started && !destroyed)
}

object EventContext {

  object Implicits {
    implicit val global: EventContext = EventContext.Global
  }

  object Global extends EventContext {
    override def register(observer: Subscription): Unit = ()

    // do nothing, global context will never need the observers (can not be stopped)
    override def unregister(observer: Subscription): Unit = ()

    override def onContextStart(): Unit = ()

    override def onContextStop(): Unit = ()

    override def onContextDestroy(): Unit = ()

    override def isContextStarted: Boolean = true
  }

}

