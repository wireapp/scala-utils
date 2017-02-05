package com.wire.db

import com.wire.macros.returning

case class CursorIterator[A](c: Cursor)(implicit read: Reader[A]) extends Iterator[A] {
  c.moveToFirst()
  override def next(): A = returning(read(c)){ _ => c.moveToNext() }
  override def hasNext: Boolean = !c.isClosed && !c.isAfterLast
}
