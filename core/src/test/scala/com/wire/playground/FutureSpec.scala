package com.wire.playground

import java.util.concurrent.CountDownLatch

import com.wire.testutils.{RichLatch, TestSpec}
import com.wire.threading.Threading
import org.threeten.bp.Instant

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class FutureSpec extends TestSpec {

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
    //TODO disable playground tests
    scenario("Starting futures on different threads") {
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
  }

}
