package com.wire.messages

import com.wire.data.MessageId
import com.wire.storage.Storage

trait MessageStorage extends Storage[MessageId, MessageData]
