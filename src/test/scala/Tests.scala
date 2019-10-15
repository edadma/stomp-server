package xyz.hyperreal.stomp_server

import org.scalatest._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import typings.sockjsDashClient.sockjsDashClientMod
import typings.stompjs.stompjsMod
import typings.stompjs.stompjsMod.{Frame, Message}

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.util.{Success, Try}


object Tests {

  private val serverHostname = "0.0.0.0"
  private val serverPort = 15674

}

class Tests extends AsyncFreeSpec with ScalaCheckPropertyChecks with Matchers {

  import Tests._

  implicit override def executionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  val server = new StompServer( "ShuttleControl/1.0", "0.0.0.0", 15674, "/stomp", _ => true, true )

	"connection" in {
    val sockjs = new sockjsDashClientMod.^( s"http://$serverHostname:$serverPort/stomp" )
    val client = stompjsMod.over( sockjs )
    val p = Promise[Assertion]

    client.connect( js.Dynamic.literal(Authorization = "Bearer asdf"), frame => {
      p success (frame.asInstanceOf[Frame].command shouldBe "CONNECTED")
    } )

    p.future
	}

  "subscription" in {
    val sockjs = new sockjsDashClientMod.^( s"http://$serverHostname:$serverPort/stomp" )
    val client = stompjsMod.over( sockjs )
    val p = Promise[Assertion]

    client.connect( js.Dynamic.literal(Authorization = "Bearer asdf"), frame => {
      if (frame.asInstanceOf[Frame].command == "CONNECTED") {
        client.subscribe("data", (message: Message) => {
          p success (message.command shouldBe "MESSAGE")
        }, js.Dynamic.literal(Authorization = "Bearer asdf"))
        client.send( "data" )
      }

    } )

    p.future
  }

}