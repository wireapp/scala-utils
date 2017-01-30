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
  package com.wire.network

import com.wire.network.Response.Status
import com.wire.utils.ExponentialBackoff

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

trait RetryPolicy {
  val backoff: ExponentialBackoff

  def shouldRetry(status: Response.Status, retry: Int): Boolean
}

object RetryPolicy {

  object NeverRetry extends RetryPolicy {
    override val backoff: ExponentialBackoff = new ExponentialBackoff(1.second, 1.second)

    override def shouldRetry(status: Status, retry: Int): Boolean = false
  }

  def apply(maxRetries: Int, initialDelay: FiniteDuration = 250.millis, maxDelay: FiniteDuration = 15.seconds) = new RetryPolicy {
    override val backoff = new ExponentialBackoff(initialDelay, maxDelay)

    override def shouldRetry(status: Status, retry: Int): Boolean = {
      retry < maxRetries
    }
  }
}
