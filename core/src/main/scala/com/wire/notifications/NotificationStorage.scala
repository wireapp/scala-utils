package com.wire.notifications

import com.wire.data.NotifId
import com.wire.reactive.{AggregatingSignal, EventStream}
import com.wire.storage.ContentChange.{Added, Removed, Updated}
import com.wire.storage.{ContentChange, Storage}

import scala.collection.{Seq, _}

trait NotificationStorage extends Storage[NotifId, NotificationData] {

  val changesStream = EventStream.union[Seq[ContentChange[NotifId, NotificationData]]](
    onAdded.map(_.map(d => Added(d.id, d))),
    onUpdated.map(_.map { case (prv, curr) => Updated(prv.id, prv, curr) }),
    onDeleted.map(_.map(Removed(_)))
  )

  // signal with all data
  val notifications = new AggregatingSignal[Seq[ContentChange[NotifId, NotificationData]], Map[NotifId, NotificationData]](changesStream, list().map(_.map { n => n.id -> n }(breakOut)), { (values, changes) =>
    val added = new mutable.HashMap[NotifId, NotificationData]
    val removed = new mutable.HashSet[NotifId]
    changes foreach {
      case Added(id, data) =>
        removed -= id
        added += id -> data
      case Updated(id, _, data) =>
        removed -= id
        added += id -> data
      case Removed(id) =>
        removed += id
        added -= id
    }
    values -- removed ++ added
  })
}
