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
  package com.wire.playground

import java.util.concurrent.CountDownLatch

import com.wire.testutils.{RichLatch, FullFeatureSpec}
import com.wire.threading.Threading
import org.scalatest.Ignore
import org.threeten.bp.Instant

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

@Ignore
class FutureSpec extends FullFeatureSpec {

  feature("Testing futures") {

    /**
      * This test shows what happens if you start a future on one thread (say the UI thread) and then
      * use a for comprehension to map it, and another future started on a different thread (say the background)
      * into a common result.
      *
      * The for comprehension's result will, it seems, run on either of the two threads (?), dependent on the
      * timing of the two futures...
      *
      * Seems as though my understanding of futures could be stronger, especially when it comes to mapping/flatmapping
      * and using them in for-comprehensions
      */
    scenario("Starting futures on different threads - for loop") {
      implicit val ec = Threading.Background

      val latch = new CountDownLatch(2)
      def toDoOnUi = Future {
        Thread.sleep(1000)
        latch.countDown()
        info(s"toDoOnUi at ${Instant.now()} running on ${Thread.currentThread().getName}")
        5
      }

      def toDoOnBackground = Future {
        Thread.sleep(2000)
        info(s"toDoOnBackground at: ${Instant.now()} on ${Thread.currentThread().getName}")
        10
      }

      val res = Future {
        val uiFuture = toDoOnUi
        for {
          background <- toDoOnBackground
          onUi <- uiFuture
        } {
          info(s"background: $background, onUi: $onUi on ${Thread.currentThread().getName}")
          latch.countDown()
        }
        uiFuture
      }(Threading.Ui)

      latch.awaitDuration(5.seconds) shouldEqual true
      info(s"final res: ${Await.result(res, 5.seconds)}")
    }

    /**
      * Note, according to this post: http://stackoverflow.com/questions/27454798/is-future-in-scala-a-monad
      * Futures are not strictly monads, although they can be built like monads using for-comprehensions. This is
      * basically because they cannot be substituted (or inlined) with their definition, which leads to some
      * of the laws for monads being violated.
      *
      * Also, having to pass in execution contexts for map, flatmap, withFilter and foreach makes them very confusing.
      */
    scenario("Starting futures on different threads - translated out") {
      implicit val ec = Threading.Background

      val latch = new CountDownLatch(2)
      def toDoOnUi = Future {
        Thread.sleep(1000)
        latch.countDown()
        info(s"toDoOnUi at ${Instant.now()} running on ${Thread.currentThread().getName}")
        5
      }

      def toDoOnBackground = Future {
        Thread.sleep(2000)
        info(s"toDoOnBackground at: ${Instant.now()} on ${Thread.currentThread().getName}")
        10
      }

      val res = Future {
//        val uiFuture = toDoOnUi

        toDoOnBackground.foreach { background =>
//          uiFuture.foreach { onUi =>
            info(s"background: $background, onUi: ... on ${Thread.currentThread().getName}")
            latch.countDown()
//          }
        }

        toDoOnUi
      }(Threading.Ui)

      latch.awaitDuration(5.seconds) shouldEqual true
      info(s"final res: ${Await.result(res, 5.seconds)}")
    }
  }

}
