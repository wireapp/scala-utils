package com.wire.testutils

import com.wire.threading.{Threading, UiDispatchQueue}
import org.scalatest.{BeforeAndAfter, FeatureSpec, Matchers, OptionValues}

trait TestSpec extends FeatureSpec with Matchers with OptionValues with BeforeAndAfter {

  Threading.setUiDispatchQueue(new UiDispatchQueue {
    override def execute(runnable: Runnable): Unit = runnable.run()
  })
}
