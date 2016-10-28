package com.wire.testutils

import com.wire.threading.{Threading, UiDispatchQueue}
import org.scalamock.scalatest.MockFactory
import org.scalatest
import org.scalatest._

/**
  * Note, the inclusion of OneInstancePerTest will mean that all instances within the test spec's scope will be re-created before each test. For that reason, anything that
  * needs to be done just once should go in the beforeAll method.
  */
abstract class FullFeatureSpec extends FeatureSpec with scalatest.Matchers with OptionValues with BeforeAndAfter
                                       with BeforeAndAfterAll with OneInstancePerTest with MockFactory with Inside {

  override def beforeAll {
    Threading.setUiDispatchQueue(new UiDispatchQueue {
      override def execute(runnable: Runnable): Unit = runnable.run()
    })

  }

}