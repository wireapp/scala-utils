package com.wire.assets

import com.wire.messages.MessageData
import com.wire.network.ErrorResponse
import com.wire.network.Response.InternalError
import com.wire.threading.CancellableFuture

import scala.concurrent.Future

trait AssetSyncHandler {
  def uploadAsset(msg: MessageData): Future[Any]
}

class AssetSyncHandlerImpl(storage: AssetStorage) extends AssetSyncHandler {

  import com.wire.threading.Threading.Implicits.Background

  override def uploadAsset(msg: MessageData): Future[Any] = {



    CancellableFuture.lift(storage.getAsset(msg.assetId)).flatMap {
      case None => CancellableFuture successful Left(InternalError(s"no asset found for msg: $msg"))
      case Some(data) if data.status == AssetStatus.UploadCancelled => CancellableFuture.successful(Left(ErrorResponse.Cancelled))
      case Some(data) =>




        CancellableFuture.successful(())
    }
  }
}
