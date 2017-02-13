package com.wire.sync

import com.wire.network.ErrorResponse

sealed trait SyncResult {

  val isSuccess: Boolean

  // we should only retry if it makes sense (temporary network or server errors)
  // there is no point retrying requests which failed with 4xx status
  val shouldRetry: Boolean

  val error: Option[ErrorResponse]
}

object SyncResult {

  case object Success extends SyncResult {
    override val isSuccess = true
    override val shouldRetry = false
    override val error = None
  }

  case class Failure(error: Option[ErrorResponse], shouldRetry: Boolean = true) extends SyncResult {
    override val isSuccess = false
  }

  def apply(error: ErrorResponse) = Failure(Some(error), ! error.isFatal)

  def apply(success: Boolean) = if (success) Success else failed()

  def failed(): SyncResult = Failure(None, shouldRetry = true)

  def aborted(): SyncResult = Failure(None, shouldRetry = false)
}
