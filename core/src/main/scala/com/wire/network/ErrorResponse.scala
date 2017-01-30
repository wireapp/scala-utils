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

case class ErrorResponse(code: Int, message: String, label: String) {
  /**
    * Returns true if retrying the request will always fail.
    * Non-fatal errors are temporary and retrying the request with the same parameters could eventually succeed.
    */
  def isFatal = Status.isFatal(code)

  // if this error should be reported to hockey
  def shouldReportError = isFatal && code != Status.CancelledCode && code != Status.UnverifiedCode
}


object ErrorResponse {

  val Cancelled = ErrorResponse(Status.CancelledCode, "Cancelled", "")
  def InternalError(msg: String) = ErrorResponse(Status.InternalErrorCode, msg, "internal-error")
}
