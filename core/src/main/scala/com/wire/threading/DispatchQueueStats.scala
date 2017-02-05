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
package com.wire.threading

import org.threeten.bp.Instant

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import com.wire.utils.RichInstant
import org.threeten.bp.Instant.now

object DispatchQueueStats {

  val Verbose = 2
  val Debug = 1
  val Off = 0

  var LogLevel = Off //to be set by application

  val stats = new mutable.HashMap[String, QueueStats]

  def apply(queue: String, executor: ExecutionContext): ExecutionContext =
    if (LogLevel >= Debug) {
      new ExecutionContext {
        override def reportFailure(cause: Throwable): Unit = executor.reportFailure(cause)
        override def execute(runnable: Runnable): Unit = executor.execute(DispatchQueueStats(queue, runnable))
      }
    } else executor

  def apply(queue: String, task: Runnable): Runnable = if (LogLevel >= Debug) { new StatsRunnable(task, queue) } else task

  def debug[A](queue: String)(f: => A): A = {
    val start = now
    val res = f
    add(queue, start, start, now)
    res
  }

  def reset() = synchronized { stats.clear() }

  def add(queue: String, init: Instant, start: Instant, done: Instant) = DispatchQueueStats.synchronized {
    stats.getOrElseUpdate(queue, QueueStats(queue)).add(init, start, done)
  }

  def printStats(minTasks: Int = 10) = report(minTasks) foreach println

  def report(minTasks: Int = 10) = {
    stats.values.toSeq.sortBy(_.totalExecution).reverse.filter(s => s.count > 10 || s.total > 1000000).map(_.report)
  }

  case class QueueStats(queue: String) {

    var count = 0
    var total = 0L // total time in micro seconds
    var totalWait = 0L
    var totalExecution = 0L

    def add(init: Instant, start: Instant, done: Instant): Unit = {
      count += 1
      total += init.until(done).toMicros
      totalWait += init.until(start).toMicros
      totalExecution += start.until(done).toMicros
    }

    def report = QueueReport(queue, count, total, totalWait, totalExecution)
  }

  class StatsRunnable(task: Runnable, queue: String) extends Runnable {
    val init = now
    if (LogLevel >= Verbose) println(f"${"Added new task:"}%-15s ${hash(task)}%-10s on $queue%-40s at $init")

    override def run(): Unit = {
      val start = now
      if (LogLevel >= Verbose) println(f"${"Started task:"}%-15s ${hash(task)}%-10s on $queue%-40s at $start (waited ${init.until(start).toMillis}ms)")
      try {
        task.run()
      } finally {
        val finished = now
        if (LogLevel >= Verbose) println(f"${"Finished task:"}%-15s ${hash(task)}%-10s on $queue%-40s at $finished (took ${start.until(finished).toMillis}ms)")
        DispatchQueueStats.add(queue, init, start, finished)
      }
    }
  }

  private def hash(obj: Object) = s"@${Integer.toHexString(obj.hashCode())}"
}

case class QueueReport(queue: String, count: Int, total: Long, totalWait: Long, totalExecution: Long) {

  def time(us: Long) = f"${us / 1000000}'${us / 1000 % 1000}%03d'${us % 1000}%03d µs"

  def stat(label: String, sum: Long) =  s"\t$label ${time(sum)} [${time(sum/count)}]"

  override def toString: String =
    s"""QueueStats[$queue] - tasks: $count
        |   ${stat("total:     ", total)}
        |   ${stat("execution: ", totalExecution)}
        |   ${stat("wait:      ", totalWait)}
        |""".stripMargin
}
