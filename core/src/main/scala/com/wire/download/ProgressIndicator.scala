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
