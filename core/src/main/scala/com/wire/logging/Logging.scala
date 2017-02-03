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
package com.wire.logging

import com.wire.macros.logging.LogTag
import org.slf4j.LoggerFactory.getLogger

object ZLog {

  private var zLog: ZLog = _

  val ImplicitTag = com.wire.macros.logging.ImplicitTag

  def setZLog(zLog: ZLog) = Option(this.zLog) match {
    case None    => this.zLog = zLog
    case Some(_) => throw new IllegalStateException("Logging already defined for application")
  }

  private def withLogging(f: ZLog => Unit) = Option(zLog) match {
    case Some(z) => f(z)
    case None => throw new IllegalStateException("No logging defined for application")
  }

  def error(message: String, cause: Throwable)(implicit tag: LogTag) = withLogging(_.error(message, cause))
  def error(message: String)(implicit tag: LogTag): Unit             = withLogging(_.error(message))
  def warn(message: String, cause: Throwable)(implicit tag: LogTag)  = withLogging(_.warn(message, cause))
  def warn(message: String)(implicit tag: LogTag)                    = withLogging(_.warn(message))
  def info(message: String)(implicit tag: LogTag)                    = withLogging(_.info(message))
  def debug(message: String)(implicit tag: LogTag)                   = withLogging(_.debug(message))
  def verbose(message: String)(implicit tag: LogTag)                 = withLogging(_.verbose(message))
}

trait ZLog {

  def error(message: String, cause: Throwable)(implicit tag: LogTag): Unit
  def error(message: String)(implicit tag: LogTag): Unit
  def warn(message: String, cause: Throwable)(implicit tag: LogTag): Unit
  def warn(message: String)(implicit tag: LogTag): Unit
  def info(message: String)(implicit tag: LogTag): Unit
  def debug(message: String)(implicit tag: LogTag): Unit
  def verbose(message: String)(implicit tag: LogTag): Unit
}

class ScalaLoggingZLog extends ZLog {
  import com.wire.macros.logging._

  def error(message: String, cause: Throwable)(implicit tag: LogTag): Unit = getLogger(tag).error(message, cause)
  def error(message: String)(implicit tag: LogTag): Unit                   = getLogger(tag).error(message)
  def warn(message: String, cause: Throwable)(implicit tag: LogTag): Unit  = getLogger(tag).warn(message, cause)
  def warn(message: String)(implicit tag: LogTag): Unit                    = getLogger(tag).warn(message)
  def info(message: String)(implicit tag: LogTag): Unit                    = getLogger(tag).info(message)
  def debug(message: String)(implicit tag: LogTag): Unit                   = getLogger(tag).debug(message)
  def verbose(message: String)(implicit tag: LogTag): Unit                 = getLogger(tag).trace(message)
}
