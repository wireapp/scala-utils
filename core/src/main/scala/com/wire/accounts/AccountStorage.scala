package com.wire.accounts

import com.wire.accounts.AccountData.AccountDataDao
import com.wire.data.AccountId
import com.wire.db.Database
import com.wire.storage.LRUCacheStorage
import com.wire.logging.ZLog.ImplicitTag._

class AccountStorage(db: Database) extends LRUCacheStorage[AccountId, AccountData](100, AccountDataDao, db) {



}
