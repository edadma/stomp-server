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
  private val serverPath = "/stomp"

}

class Tests extends AsyncFreeSpec with ScalaCheckPropertyChecks with Matchers {

  import Tests._

  implicit override def executionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  val server = new StompServer( "ShuttleControl/1.0", "0.0.0.0", 15674, "/stomp", _ => true, _ => true, false )

	"connect" in {
    val sock = new sockjsDashClientMod.^(s"http://$serverHostname:$serverPort$serverPath")
    val client = stompjsMod.over( sock )
    val p = Promise[Assertion]

    client.connect( js.Dynamic.literal(Authorization = "Bearer asdf"), frame => {
      p success (frame.asInstanceOf[Frame].command shouldBe "CONNECTED")
      sock.close
    } )

    p.future
	}

  "subscribe" in {
    val sock = new sockjsDashClientMod.^( s"http://$serverHostname:$serverPort$serverPath" )
    val client = stompjsMod.over( sock )
    val p = Promise[Assertion]

    client.connect( js.Dynamic.literal(), frame => {
      if (frame.asInstanceOf[Frame].command == "CONNECTED") {
        client.subscribe("data", (message: Message) => {
          p success (message.command shouldBe "MESSAGE")
          sock.close
        }, js.Dynamic.literal())
        client.send( "data" )
      }
    } )

    p.future
  }

  "disconnect" in {
    val client = stompjsMod.over( new sockjsDashClientMod.^(s"http://$serverHostname:$serverPort$serverPath") )
    val p = Promise[Assertion]

    client.connect( js.Dynamic.literal(Authorization = "Bearer asdf"), frame => {
      client.disconnect( () => {p success assert(true)} )
    } )

    p.future
  }

  "receipt" in {
    val p = Promise[Assertion]

    def onMessage( client: StompClient, command: String, headers: Map[String, String], body: String ): Unit =
      command match {
        case "CONNECTED" =>
          client.subscribe( "data", "subscribe-receipt" )
        case "MESSAGE" =>
          p success (body shouldBe "this is the message")
          client.disconnect( "disconnect-receipt" )
        case "RECEIPT" =>
          headers("receipt-id") match {
            case "subscribe-receipt" => client.send( "data", "this is the message" )
            case "disconnect-receipt" => client.close
          }
      }

    val client = new StompClient( serverHostname, serverPort, serverPath, _.connect(Map()), onMessage )

    p.future
  }

  "send null from server" in {
    val p = Promise[Assertion]

    def onMessage( client: StompClient, command: String, headers: Map[String, String], body: String ): Unit =
      command match {
        case "CONNECTED" =>
          client.subscribe( "data", "subscribe-receipt" )
        case "MESSAGE" =>
          p success (body shouldBe "null")
          client.disconnect( "disconnect-receipt" )
        case "RECEIPT" =>
          headers("receipt-id") match {
            case "subscribe-receipt" => server.send( "data", null )
            case "disconnect-receipt" => client.close
          }
      }

    val client = new StompClient( serverHostname, serverPort, serverPath, _.connect(Map()), onMessage )

    p.future
  }

  "send null from client" in {
    val p = Promise[Assertion]

    def onMessage( client: StompClient, command: String, headers: Map[String, String], body: String ): Unit =
      command match {
        case "CONNECTED" =>
          client.subscribe( "data", "subscribe-receipt" )
        case "MESSAGE" =>
          p success (body shouldBe "null")
          client.disconnect( "disconnect-receipt" )
        case "RECEIPT" =>
          headers("receipt-id") match {
            case "subscribe-receipt" => client.send( "data", null )
            case "disconnect-receipt" => client.close
          }
      }

    val client = new StompClient( serverHostname, serverPort, serverPath, _.connect(Map()), onMessage )

    p.future
  }

}