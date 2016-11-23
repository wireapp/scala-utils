package com.wire.assets

import com.wire.data.{JsonDecoder, JsonEncoder}
import org.json.{JSONException, JSONObject}

sealed trait AssetStatus

object AssetStatus {

  sealed trait Sync
  type Syncable = AssetStatus with Sync

  case object UploadNotStarted    extends AssetStatus
  case object UploadInProgress    extends AssetStatus
  case object UploadDone          extends AssetStatus
  case object UploadCancelled     extends AssetStatus with Sync
  case object UploadFailed        extends AssetStatus with Sync
  case object DownloadInProgress  extends AssetStatus
  case object DownloadDone        extends AssetStatus
  case object DownloadFailed      extends AssetStatus

  import JsonDecoder._

  implicit lazy val AssetStatusDecoder: JsonDecoder[AssetStatus] = new JsonDecoder[AssetStatus] {
    override def apply(implicit js: JSONObject): AssetStatus = decodeString('status) match {
      case "UploadNotStarted"   => UploadNotStarted
      case "UploadInProgress"   => UploadInProgress
      case "UploadDone"         => UploadDone
      case "UploadCancelled"    => UploadCancelled
      case "UploadFailed"       => UploadFailed
      case "DownloadInProgress" => DownloadInProgress
      case "DownloadDone"       => DownloadDone
      case "DownloadFailed"     => DownloadFailed // this will never be used in AssetData
    }
  }

  def unapply(arg: AssetStatus): Option[String] = arg match {
    case UploadNotStarted   => Some("UploadNotStarted")
    case UploadInProgress   => Some("UploadInProgress")
    case UploadDone         => Some("UploadDone")
    case UploadCancelled    => Some("UploadCancelled")
    case UploadFailed       => Some("UploadFailed")
    case DownloadInProgress => Some("DownloadInProgress")
    case DownloadDone       => Some("DownloadDone")
    case DownloadFailed     => Some("DownloadFailed")
  }

  implicit lazy val AssetStatusEncoder: JsonEncoder[AssetStatus] = new JsonEncoder[AssetStatus] {
    override def apply(data: AssetStatus): JSONObject = JsonEncoder { o =>
      o.put("status", unapply(data))
    }
  }

  implicit lazy val SyncableAssetStatusDecoder: JsonDecoder[Syncable] = AssetStatusDecoder.map {
    case a: AssetStatus.Syncable => a
    case other => throw new JSONException(s"not a syncable asset status: $other")
  }

  implicit lazy val SyncableAssetStatusEncoder: JsonEncoder[Syncable] = AssetStatusEncoder.comap(identity)

}
