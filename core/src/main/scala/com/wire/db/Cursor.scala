package com.wire.db

import java.sql.{ResultSet, Statement}

import com.wire.logging.ZLog.ImplicitTag._
import com.wire.logging.ZLog.verbose

/**
  * Allows keeping connection open while ResultSet is being used outside of Database class, and also
  * provides a nice way to wrap the android methods, making copying easier
  */
case class Cursor(st: Statement, resultSet: ResultSet) extends AutoCloseable {

  def close() = {
    verbose("Closing cursor")
    st.close()
    st.getConnection.close()
  }

//  http://stackoverflow.com/questions/1813858/jdbc-driver-throws-resultset-closed-exception-on-empty-resultset
  def moveToFirst() = resultSet.next()

  def moveToNext() = resultSet.next()

  def isClosed = resultSet.isClosed

  def isAfterLast = resultSet.isAfterLast

  def getColumnIndex(columnName: String) = resultSet.findColumn(columnName)

  def count: Int = -1

  def getString(colIndex: Int): Option[String] = returnWithNullCheck(_.getString(colIndex))
  def getString(colLabel: String): Option[String] = returnWithNullCheck(_.getString(colLabel))

  def getInt(colIndex: Int): Option[Int] = returnWithNullCheck(_.getInt(colIndex))
  def getInt(colLabel: String): Option[Int] = returnWithNullCheck(_.getInt(colLabel))

  def getLong(colIndex: Int): Option[Long] = returnWithNullCheck(_.getLong(colIndex))
  def getLong(colLabel: String): Option[Long] = returnWithNullCheck(_.getLong(colLabel))

  private def returnWithNullCheck[A](value: ResultSet => A): Option[A] = {
    val r = value(resultSet)
    if (resultSet.wasNull() || r == "NULL") None else Some(r)
  }

}
