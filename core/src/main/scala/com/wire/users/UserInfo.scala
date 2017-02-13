package com.wire.users

import com.wire.assets.AssetData
import com.wire.assets.AssetMetaData.Image.Tag.Medium
import com.wire.auth.{EmailAddress, Handle, PhoneNumber}
import com.wire.data.{TrackingId, UserId}

case class UserInfo(id:           UserId,
                    name:         Option[String]          = None,
                    accentId:     Option[Int]             = None,
                    email:        Option[EmailAddress]    = None,
                    phone:        Option[PhoneNumber]     = None,
                    handle:       Option[Handle]          = None,
                    picture:      Option[Seq[AssetData]]  = None, //the empty sequence is used to delete pictures
                    trackingId:   Option[TrackingId]      = None,
                    deleted:      Boolean                 = false,
                    privateMode:  Option[Boolean]         = None) {
  //TODO Dean - this will actually prevent deleting profile pictures, since the empty seq will be mapped to a None,
  //And so in UserData, the current picture will be used instead...
  def mediumPicture = picture.flatMap(_.collectFirst { case a@AssetData.IsImageWithTag(Medium) => a })
}
