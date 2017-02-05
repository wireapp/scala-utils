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

  def getString(colIndex: Int): String = resultSet.getString(colIndex)
  def getString(colLabel: String): String = resultSet.getString(colLabel)

  def getInt(colIndex: Int): Int = resultSet.getInt(colIndex)
  def getInt(colLabel: String): Int = resultSet.getInt(colLabel)
}
