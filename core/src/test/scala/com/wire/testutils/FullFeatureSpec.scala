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
  package com.wire.testutils

import com.wire.logging.{ScalaLoggingZLog, ZLog}
import com.wire.threading.{DispatchQueueStats, Threading, UiDispatchQueue}
import org.scalamock.scalatest.MockFactory
import org.scalatest
import org.scalatest._

/**
  * Note, the inclusion of OneInstancePerTest will mean that all instances within the test spec's scope will be re-created before each test. For that reason, anything that
  * needs to be done just once should go in the beforeAll method.
  */
abstract class FullFeatureSpec extends FeatureSpec with scalatest.Matchers with OptionValues with BeforeAndAfter
                                       with OneInstancePerTest with MockFactory with Inside with BeforeAndAfterAll  {

  override def beforeAll {
    Threading.setUiDispatchQueue(new UiDispatchQueue {
      override def execute(runnable: Runnable): Unit = runnable.run()
    })

    ZLog.setZLog(new ScalaLoggingZLog)

    DispatchQueueStats.LogLevel = DispatchQueueStats.Debug //change to Verbose to view concurrent behaviour

  }

}

