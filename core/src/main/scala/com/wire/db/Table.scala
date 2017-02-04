package com.wire.db

import com.wire.db.Database.ContentValues

import scala.collection.mutable


class Table[A](val name: String, val columns: ColBinder[_, A]*) {
  require(columns.nonEmpty)

  columns.zipWithIndex.foreach { case (col, i) => col.index = i }

  lazy val createSql = columns.map(c => s"${c.name} ${c.col.sqlType} ${c.col.modifiers}").mkString(s"CREATE TABLE $name (", ", ", ");")

  lazy val insertSql = insertOr("REPLACE")
  lazy val insertOrIgnoreSql = insertOr("IGNORE")

  private def insertOr(onConflict: String) = s"INSERT OR $onConflict INTO $name (${columns.map(_.name).mkString(",")}) VALUES (${columns.map(c => "?").mkString(",")});"

  def save(obj: A): ContentValues = {
    val values = mutable.Map.empty[String, String]
    columns.foreach(_.save(obj, values))
    values.toMap
  }

//  def bind(obj: A, stmt: SQLiteProgram) = columns.foreach(_.bind(obj, stmt))
}

class TableWithId[A](name: String, columns: ColBinder[_, A]*)(idCols: => Seq[ColBinder[_, A]]) extends Table[A](name, columns:_*) {
  override lazy val createSql = columns.map(c => s"${c.name} ${c.col.sqlType} ${c.col.modifiers}").mkString(s"CREATE TABLE $name (", ", ", s"$primaryKeyDDL);")

  private def primaryKeyDDL =
    if (idCols.size == 1 && idCols.head.col.modifiers.matches("(?i).*primary key.*")) ""
    else s", PRIMARY KEY (${idCols.map(_.name).mkString(", ")})"
}
