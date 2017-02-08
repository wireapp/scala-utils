package com.wire.db

import java.sql.{ResultSet, Statement}

/**
  * Allows keeping connection open while ResultSet is being used outside of Database class, and also
  * provides a nice way to wrap the android methods, making copying easier
  */
case class Cursor(st: Statement, resultSet: ResultSet) extends AutoCloseable {

  def close() = {
    st.close()
    st.getConnection.close()
  }

  def moveToFirst() = resultSet.first()

  def moveToNext() = resultSet.next()

  def isClosed = resultSet.isClosed

  def isAfterLast = resultSet.isAfterLast

  def getColumnIndex(columnName: String) = resultSet.findColumn(columnName)

  def count: Int = -1

  def getString(colIndex: Int): Option[String] = returnWithNullCheck(_.getString(colIndex))
  def getString(colLabel: String): Option[String] = returnWithNullCheck(_.getString(colLabel))

  def getInt(colIndex: Int): Option[Int] = returnWithNullCheck(_.getInt(colIndex))
  def getInt(colLabel: String): Option[Int] = returnWithNullCheck(_.getInt(colLabel))

  private def returnWithNullCheck[A](value: ResultSet => A): Option[A] = {
    val r = value(resultSet)
    if (resultSet.wasNull()) None else Some(r)
  }

}
