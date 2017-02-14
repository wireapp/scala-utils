package com.wire.sync

import com.wire.auth.Handle
import com.wire.data._
import com.wire.sync.SyncServiceHandle.Priority
import com.wire.threading.Threading
import com.wire.users.UserInfo
import org.threeten.bp.Instant

import scala.concurrent.Future

trait SyncServiceHandle {
//  def syncUsersIfNotEmpty(ids: Seq[UserId]): Future[Unit] = if (ids.nonEmpty) syncUsers(ids: _*).map(_ => ())(Threading.Background) else Future.successful(())

//  def syncSearchQuery(query: SearchQuery): Future[SyncId]
//  def syncUsers(ids: UserId*): Future[SyncId]
//  def syncSelfUser(): Future[SyncId]
//  def deleteAccount(): Future[SyncId]
//  def syncConversations(dependsOn: Option[SyncId] = None): Future[SyncId]
//  def syncConversation(id: ConvId, dependsOn: Option[SyncId] = None): Future[SyncId]
//  def syncCallState(id: ConvId, fromFreshNotification: Boolean, priority: Int = Priority.Normal): Future[SyncId]
//  def syncConnectedUsers(): Future[SyncId]
//  def syncConnections(dependsOn: Option[SyncId] = None): Future[SyncId]
//  def syncCommonConnections(id: UserId): Future[SyncId]
//  def syncRichMedia(id: MessageId, priority: Int = Priority.MinPriority): Future[SyncId]
//
//  def postSelfUser(info: UserInfo): Future[SyncId]
//  def postSelfPicture(picture: Option[AssetId]): Future[SyncId]
//  def postMessage(id: MessageId, conv: ConvId, editTime: Instant): Future[SyncId]
//  def postDeleted(conv: ConvId, msg: MessageId): Future[SyncId]
//  def postRecalled(conv: ConvId, currentMsgId: MessageId, recalledMsgId: MessageId): Future[SyncId]
//  def postAssetStatus(id: MessageId, conv: ConvId, exp: EphemeralExpiration, status: AssetStatus.Syncable): Future[SyncId]
//  def postLiking(id: ConvId, liking: Liking): Future[SyncId]
//  def postConnection(user: UserId, name: String, message: String): Future[SyncId]
//  def postConnectionStatus(user: UserId, status: ConnectionStatus): Future[SyncId]
//  def postConversationName(id: ConvId, name: String): Future[SyncId]
//  def postConversationMemberJoin(id: ConvId, members: Seq[UserId]): Future[SyncId]
//  def postConversationMemberLeave(id: ConvId, member: UserId): Future[SyncId]
//  def postConversationState(id: ConvId, state: ConversationState): Future[SyncId]
//  def postConversation(id: ConvId, users: Seq[UserId], name: Option[String]): Future[SyncId]
//  def postLastRead(id: ConvId, time: Instant): Future[SyncId]
//  def postCleared(id: ConvId, time: Instant): Future[SyncId]
//  def postAddressBook(ab: AddressBook): Future[SyncId]
//  def postInvitation(i: Invitation): Future[SyncId]
//  def postTypingState(id: ConvId, typing: Boolean): Future[SyncId]
//  def postOpenGraphData(conv: ConvId, msg: MessageId, editTime: Instant): Future[SyncId]
//  def postReceipt(conv: ConvId, message: MessageId, user: UserId, tpe: ReceiptType): Future[SyncId]

//  def resetGcm(): Future[SyncId]
//  def deleteGcmToken(token: GcmId): Future[SyncId]

  def syncSelfClients(): Future[SyncId]
//  def postClientLabel(id: ClientId, label: String): Future[SyncId]
//  def syncClients(user: UserId): Future[SyncId]
//  def syncClientsLocation(): Future[SyncId]
//  def syncPreKeys(user: UserId, clients: Set[ClientId]): Future[SyncId]
//  def postSessionReset(conv: ConvId, user: UserId, client: ClientId): Future[SyncId]

//  def postValidateHandles(handles: Seq[Handle]): Future[SyncId]
}

object SyncServiceHandle {

  object Priority {
    val Critical = 0
    val High = 1
    val Normal = 10
    val Low = 50
    val Optional = 100
    val MinPriority = Int.MaxValue
  }
}