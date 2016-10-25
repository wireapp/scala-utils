package com.wire.history

import com.wire.core.Uid
import com.wire.storage.KeyValueStorage
import com.wire.testutils.{FullFeatureSpec, FullFlatSpec}

class LastNotificationIdServiceTest extends FullFlatSpec {

  val mockKVStorage = mock[KeyValueStorage]

  behavior of "The LastNotificationIdService"

  it should "update the last id when all notifications have finished process" in {

//    (mockKVStorage.keyValuePref _ ).expects(LastNotificationIdService.LastNotficationId).
    val service = new LastNotificationIdService(mockKVStorage)

  }
}
