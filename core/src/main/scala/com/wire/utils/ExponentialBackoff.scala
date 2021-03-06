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
  package com.wire.utils

import scala.concurrent.duration.{Duration, FiniteDuration, _}
import scala.math._
import scala.util.Random

/**
  * Calculates retry delay using randomized exponential backoff strategy.
  */
class ExponentialBackoff(initialDelay: FiniteDuration, val maxDelay: FiniteDuration) {

  val maxRetries = ExponentialBackoff.bitsCount(maxDelay.toMillis / initialDelay.toMillis)

  def delay(retry: Int, minDelay: FiniteDuration = Duration.Zero): FiniteDuration = {
    if (retry <= 0) initialDelay
    else if (retry >= maxRetries) randomized(maxDelay)
    else {
      val expDelay = initialDelay * (1L << retry)
      randomized(maxDelay min expDelay max minDelay)
    }
  }

  def randomized(delay: Duration) = {
    val ms = delay.toMillis / 2d
    (ms + abs(Random.nextDouble()) * ms).millis
  }
}

object ExponentialBackoff {
  def bitsCount(v: Long): Int = if (v >= 2) 1 + bitsCount(v >> 1) else if (v >= 0) 1 else 0
}
