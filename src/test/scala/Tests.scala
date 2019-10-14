package xyz.hyperreal.stomp_server

import org.scalatest._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import typings.sockjsDashClient.sockjsDashClientMod
import typings.stompjs.stompjsMod

import scala.concurrent.{Future, Promise}
import scala.scalajs.js


object Tests {

  private val serverHostname = "0.0.0.0"
  private val serverPort = 15674

}

class Tests extends AsyncFreeSpec with ScalaCheckPropertyChecks with Matchers {

  import Tests._

	"tests" in {
    val sockjs = new sockjsDashClientMod.^( s"http://$serverHostname:$serverPort/stomp" )
    val client = stompjsMod.over( sockjs )
    var prom: Promise[String] = Promise[String]()

    client.connect( js.Dynamic.literal(Authorization = "Bearer asdf"), frame => {
      prom.success( "client connected" )
    } )

    prom.future
	}
	
}