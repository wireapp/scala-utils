package com.wire.env

import com.wire.reactive.Signal

trait LifeCycle {
  import LifeCycle._
  val lifecycleState: Signal[State]
}

object LifeCycle {
  trait State
  case object Stopped extends State
  case object Idle extends State
  case object Active extends State
  case object UiActive extends State
}