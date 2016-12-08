package com.wire.convs

import com.wire.conversations.ConversationData
import com.wire.data.ConvId
import com.wire.storage.Storage

trait ConversationStorage extends Storage[ConvId, ConversationData] {

}
