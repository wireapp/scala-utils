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

  def error(message: String, cause: Throwable)(implicit tag: LogTag): Unit = getLogger(tag).error(message, cause)

  def error(message: String)(implicit tag: LogTag): Unit = getLogger(tag).error(message)

  def warn(message: String, cause: Throwable)(implicit tag: LogTag): Unit = getLogger(tag).warn(message, cause)

  def warn(message: String)(implicit tag: LogTag): Unit = getLogger(tag).warn(message)

  def info(message: String)(implicit tag: LogTag): Unit = getLogger(tag).info(message)

  def debug(message: String)(implicit tag: LogTag): Unit = getLogger(tag).debug(message)

  def verbose(message: String)(implicit tag: LogTag): Unit = getLogger(tag).trace(message)

}
