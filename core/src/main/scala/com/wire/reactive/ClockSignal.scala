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
   package com.wire.reactive

import com.wire.threading.CancellableFuture.delayed
import com.wire.threading.Threading
import org.threeten.bp.Instant
import org.threeten.bp.Instant.now

import scala.concurrent.duration.FiniteDuration

case class ClockSignal(interval: FiniteDuration) extends SourceSignal[Instant](Some(now)) {
  def refresh(): Unit = if (wired) {
    publish(now)
    delayed(interval)(refresh())(Threading.Background)
  }

  override def onWire(): Unit = refresh()
}
