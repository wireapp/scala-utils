package com.wire.context

trait ResourceProvider {

  def getString(id: Int): String



}

/**
  * A simple, hand-crafted resource provider to simulate android R class.
  */
class SimpleResourceProvider extends ResourceProvider {


  override def getString(id: Int) = id match {
    case string.account_pref => "ACCOUNT_PREF"
  }


  object string {
    val account_pref = 1
  }
}
