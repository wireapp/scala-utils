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
