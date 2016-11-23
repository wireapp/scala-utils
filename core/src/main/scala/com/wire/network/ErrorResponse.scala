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
