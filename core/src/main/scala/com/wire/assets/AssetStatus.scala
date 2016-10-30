package com.wire.assets

sealed trait AssetStatus

object AssetStatus {

  sealed trait Sync

  case object UploadNotStarted                    extends AssetStatus
  case object MetaDataSent                        extends AssetStatus
  case object PreviewSent                         extends AssetStatus
  case object UploadInProgress                    extends AssetStatus
  case class  UploadDone(uploaded: AssetKey)      extends AssetStatus
  case object UploadCancelled                     extends AssetStatus with Sync
  case object UploadFailed                        extends AssetStatus with Sync
  case class  DownloadFailed(uploaded: AssetKey)  extends AssetStatus
}
