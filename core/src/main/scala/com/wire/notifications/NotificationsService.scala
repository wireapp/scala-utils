package com.wire.notifications

import com.wire.convs.ConversationStorage
import com.wire.data.UserId
import com.wire.env.LifeCycle
import com.wire.messages.MessageStorage
import com.wire.storage.KeyValueStorage
import com.wire.users.UserStorage

class NotificationsService(selfUserId:          UserId,
                           messages:            MessageStorage,
                           lifeCycle:           LifeCycle,
                           notificationStorage: NotificationStorage,
                           userStorage:         UserStorage,
                           conversationStorage: ConversationStorage,
                           keyValueStorage:     KeyValueStorage
                           ) {


}
