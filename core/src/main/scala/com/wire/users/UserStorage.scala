package com.wire.users

import com.wire.data.UserId
import com.wire.storage.Storage

trait UserStorage extends Storage[UserId, UserData] {

}
