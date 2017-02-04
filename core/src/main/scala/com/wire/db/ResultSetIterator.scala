package com.wire.db

import java.sql.ResultSet

import com.wire.macros.returning

case class ResultSetIterator[A](c: ResultSet)(implicit read: Reader[A]) extends Iterator[A] {
  c.first()
  override def next(): A = returning(read(c)){ _ => c.next() }
  override def hasNext: Boolean = !c.isClosed && !c.isAfterLast
}
