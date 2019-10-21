package xyz.hyperreal.stomp_server

import org.scalatest._
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import typings.sockjsDashClient.sockjsDashClientMod
import typings.stompjs.stompjsMod
import typings.stompjs.stompjsMod.{Frame, Message}

import scala.concurrent.Promise
import scala.scalajs.js


object Tests {

  private val serverHostname = "0.0.0.0"
  private val serverPort = 15674

}

class Tests extends AsyncFreeSpec with ScalaCheckPropertyChecks with Matchers {

  import Tests._

  implicit override def executionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  val server = new StompServer( "ShuttleControl/1.0", "0.0.0.0", 15674, "/stomp", _ => true, _ => true, false )

	"connect" in {
    val client = stompjsMod.over( new sockjsDashClientMod.^(s"http://$serverHostname:$serverPort/stomp") )
    val p = Promise[Assertion]

    client.connect( js.Dynamic.literal(Authorization = "Bearer asdf"), frame => {
      p success (frame.asInstanceOf[Frame].command shouldBe "CONNECTED")
    } )

    p.future
	}

  "subscribe" in {
    val client = stompjsMod.over( new sockjsDashClientMod.^(s"http://$serverHostname:$serverPort/stomp") )
    val p = Promise[Assertion]

    client.connect( js.Dynamic.literal(), frame => {
      if (frame.asInstanceOf[Frame].command == "CONNECTED") {
        client.subscribe("data", (message: Message) => {
          p success (message.command shouldBe "MESSAGE")
        }, js.Dynamic.literal())
        client.send( "data" )
      }
    } )

    p.future
  }

  "disconnect" in {
    val client = stompjsMod.over( new sockjsDashClientMod.^(s"http://$serverHostname:$serverPort/stomp") )
    val p = Promise[Assertion]

    client.connect( js.Dynamic.literal(Authorization = "Bearer asdf"), frame => {
      client.disconnect( () => {p success (assert(true))} )
    } )

    p.future
  }

}