package com.wire.history

import com.wire.storage.KeyValueStorage
import com.wire.testutils.FullFeatureSpec

class LastNotificationIdServiceTest extends FullFeatureSpec {

  val mockKVStorage = mock[KeyValueStorage]

  scenario("Update the last id when all notifications have finished process") {

    //    (mockKVStorage.keyValuePref _ ).expects(LastNotificationIdService.LastNotficationId).
    val service = new LastNotificationIdService(mockKVStorage)

  }
}
