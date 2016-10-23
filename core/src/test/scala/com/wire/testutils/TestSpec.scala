package com.wire.testutils

import com.wire.threading.{Threading, UiDispatchQueue}
import org.scalamock.scalatest.MockFactory
import org.scalatest
import org.scalatest.{BeforeAndAfter, FeatureSpec, OneInstancePerTest, OptionValues}

abstract class TestSpec extends FeatureSpec with scalatest.Matchers with OptionValues with BeforeAndAfter with OneInstancePerTest with MockFactory {

  Threading.setUiDispatchQueue(new UiDispatchQueue {
    override def execute(runnable: Runnable): Unit = runnable.run()
  })
}
