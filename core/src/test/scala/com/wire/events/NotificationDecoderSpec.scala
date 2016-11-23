package com.wire.events

import com.wire.testutils.{BackendResponses, FullFeatureSpec}

class NotificationDecoderSpec extends FullFeatureSpec {

  scenario("Test json loading") {
    val n = PushNotification.NotificationDecoder(BackendResponses.conversationOtrMessageAdd())

    println(n)
  }
}
