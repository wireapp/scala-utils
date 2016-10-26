package com.wire.download


trait ProgressIndicator {
  import ProgressIndicator._

  def getProgress: Long

  def getTotalSize: Long

  def isIndefinite: Boolean

  def getState: State

  def toString: String

  def cancel(): Unit
}

object ProgressIndicator {
  sealed trait State
  case object Unknown extends State
  case object Running extends State
  case object Completed extends State
  case object Failed extends State
  case object Cancelled extends State

  case class ProgressData(current: Long, total: Long, state: State)
  object ProgressData {
    val Indefinite = ProgressData(0, -1, Running)
    val Unknown = ProgressData(0, 0, ProgressIndicator.Unknown)
  }
}