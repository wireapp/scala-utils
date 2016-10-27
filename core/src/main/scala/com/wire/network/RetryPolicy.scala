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