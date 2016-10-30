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

import com.typesafe.scalalogging.Logger
import com.wire.macros.returning
import org.slf4j.LoggerFactory

object Logging {

  import com.wire.macros.logging._

  private var loggers = Map.empty[LogTag, Logger]

  private def getLogger(logTag: LogTag) = loggers.getOrElse(logTag, {
    returning(Logger(LoggerFactory.getLogger(logTag))) { l =>
      loggers += (logTag -> l)
    }
  })

  implicit def logTag = ImplicitTag.implicitLogTag

  def error(message: String, cause: Throwable)(implicit tag: LogTag): Unit = getLogger(tag).error(message, cause)

  def error(message: String)(implicit tag: LogTag): Unit = getLogger(tag).error(message)

  def warn(message: String, cause: Throwable)(implicit tag: LogTag): Unit = getLogger(tag).warn(message, cause)

  def warn(message: String)(implicit tag: LogTag): Unit = getLogger(tag).warn(message)

  def info(message: String)(implicit tag: LogTag): Unit = getLogger(tag).info(message)

  def debug(message: String)(implicit tag: LogTag): Unit = getLogger(tag).debug(message)

  def verbose(message: String)(implicit tag: LogTag): Unit = getLogger(tag).trace(message)

}
