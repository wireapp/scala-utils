package com.wire.assets

trait AssetService {
  def assetSignal: Nothing
  def downloadProgress: Nothing
  def cancelDownload: Nothing
  def uploadProgress: Nothing
  def cancelUpload: Nothing
}
