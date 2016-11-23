package com.wire.events

import com.wire.testutils.{Events, FullFeatureSpec}

class NotificationDecoderSpec extends FullFeatureSpec {

  scenario("Test json loading") {
    val n = PushNotification.NotificationDecoder(Events.conversationOtrMessage())

    println(n)
  }
}
