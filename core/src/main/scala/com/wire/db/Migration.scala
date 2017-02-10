package com.wire.db

import com.wire.logging.ZLog.ImplicitTag._
import com.wire.logging.ZLog.{error, verbose}

import scala.util.control.NonFatal

trait Migration {
  val fromVersion: Int
  val toVersion: Int

  def apply(db: Database): Unit
}

object Migration {
  val AnyVersion = -1

  def apply(from: Int, to: Int)(migrations: (Database => Unit)*): Migration = new Migration {

    override val fromVersion = from
    override val toVersion = to

    override def apply(db: Database) = migrations.foreach(_ (db))

    override def toString = s"Migration from $from to $to"

  }

  def to(to: Int)(migrations: (Database => Unit)*): Migration = apply(AnyVersion, to)(migrations: _*)
}

class Migrations(migrations: Migration*) {

  val toVersionMap = migrations.groupBy(_.toVersion)

  def plan(from: Int, to: Int): List[Migration] = {

    def shortest(from: Int, to: Int): List[Migration] = {
      val possible = toVersionMap.getOrElse(to, Nil)
      val plans = possible.map { m =>
        if (m.fromVersion == from || m.fromVersion == Migration.AnyVersion) List(m)
        else if (m.fromVersion < from) Nil
        else shortest(from, m.fromVersion) match {
          case Nil => List()
          case best => best ::: List(m)
        }
      }.filter(_.nonEmpty)

      if (plans.isEmpty) Nil
      else plans.minBy(_.length)
    }

    if (from == to) Nil
    else shortest(from, to)
  }

  /**
    * Migrates database using provided migrations.
    * Falls back to dropping all data if migration fails.
    *
    * @throws IllegalStateException if no migration plan can be found
    */
  @throws[IllegalStateException]("If no migration plan can be found for given versions")
  def migrate(db: Database, fromVersion: Int, toVersion: Int): Unit = {
    if (fromVersion != toVersion) {
      plan(fromVersion, toVersion) match {
        case Nil => throw new IllegalStateException(s"No migration plan from: $fromVersion to: $toVersion")
        case ms =>
          try {
            ms.foreach { m =>
              verbose(s"applying migration: $m")
              m(db)
              db.execSQL(s"PRAGMA user_version = ${m.toVersion}")
            }
          } catch {
            case NonFatal(e) =>
              error(s"Migration failed for $db, from: $fromVersion to: $toVersion", e)
              db.dropAllTables()
              db.onCreate()
          }
      }
    }
  }
}