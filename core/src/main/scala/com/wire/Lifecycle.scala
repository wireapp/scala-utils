package com.wire

import com.wire.Lifecycle.LifecycleState
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.logging.ZLog.debug
import com.wire.reactive.{EventContext, Signal}
import com.wire.threading.Threading

class Lifecycle extends EventContext {

  val lifecycleState = Signal(LifecycleState.Stopped)
  val uiActive = lifecycleState.map(_ == LifecycleState.UiActive)

  val loggedIn = lifecycleState.map(_ != LifecycleState.Stopped)

  private var _loggedIn = false
  private var syncCount = 0
  private var pushCount = 0
  private var uiCount = 0

  def isUiActive = lifecycleState.currentValue.contains(LifecycleState.UiActive)

  def acquireSync(source: String = ""): Unit = acquire('sync, syncCount += 1, source)
  def acquirePush(source: String = ""): Unit = acquire('push, pushCount += 1, source)
  def acquireUi(source: String = ""): Unit = acquire('ui, uiCount += 1, source)

  def setLoggedIn(loggedIn: Boolean) = {
    Threading.assertUiThread()
    _loggedIn = loggedIn
    updateState()
  }

  private def acquire(name: Symbol, action: => Unit, source: String): Unit = {
    Threading.assertUiThread()
    action
    if ((syncCount + pushCount + uiCount) == 1) onContextStart()
    updateState()
    debug(s"acquire${name.name.capitalize}, syncCount: $syncCount, pushCount: $pushCount, uiCount: $uiCount, source: '$source'")
  }

  def releaseSync(source: String = ""): Unit = release('sync, syncCount > 0, syncCount -= 1, source)
  def releasePush(source: String = ""): Unit = release('push, pushCount > 0, pushCount -= 1, source)
  def releaseUi(source: String = ""): Unit = release('ui, uiCount > 0, uiCount -= 1, source)

  private def release(name: Symbol, predicate: => Boolean, action: => Unit, source: String): Unit = {
    Threading.assertUiThread()
    val id = name.name.capitalize
    assert(predicate, s"release$id should be called exactly once for each acquire$id")
    action
    updateState()
    if ((syncCount + pushCount + uiCount) == 0) onContextStop()
    debug(s"release$id syncCount: $syncCount, pushCount: $pushCount, uiCount: $uiCount, source: '$source'")
  }

  private def updateState() = lifecycleState ! {
    if (!_loggedIn) LifecycleState.Stopped
    else if (uiCount > 0) LifecycleState.UiActive
    else if (pushCount > 0) LifecycleState.Active
    else LifecycleState.Idle
  }
}

object Lifecycle {
  object LifecycleState extends Enumeration {
    val Stopped, Idle, Active, UiActive = Value
  }
  type LifecycleState = LifecycleState.Value
}