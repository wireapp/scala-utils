/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH

 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
  package com.wire.storage

import java.util.concurrent.TimeUnit

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.twitter.cache.guava.GuavaCache
import com.twitter.util.{Await, Duration, Promise}
import com.wire.testutils.FullFeatureSpec


class StorageSpec extends FullFeatureSpec {


//  val st = new TestStorage[String, Int] {
//    var something = 0
//
//    override def load(key: String): Future[Int] = {
//      Thread.sleep(1000)
//      something += 1
//      Future.value(something)
//    }
//  }

  scenario("what's even going on") {
//    st.getCachedValue("test").onSuccess(v => println(s"onSuccess: $v")).onFailure(e => println(s"onFailure, ${e.getMessage}"))
  }


  scenario("playing with futures") {

//    import scala.concurrent.{Await, ExecutionContext, Future}
    import com.twitter.util.Future

//    implicit val ec = ExecutionContext.Implicits.global

    val f1 = Future {
      Thread.sleep(2000)
      println("1 done")
      1
    }

    val p2 = Promise[Int]

    val f2 = p2.setValue(2)

//    f2.raise(new Throwable("blah blah"))

//    f1
//    f2

//    Await.result(Future.collect(Seq(f1, f2)))

  }

}

//abstract class TestStorage[K, V] {
//  private val cache = GuavaCache.fromLoadingCache(CacheBuilder.newBuilder()
//    .maximumSize(1000)
//    .build(new CacheLoader[K, Future[V]] {
//      override def load(k: K): Future[V] = load(k)
//    }))
//
//  def getCachedValue(k: K): Future[V] = cache(k)
//
//  def load(key: K): Future[V]
//}
