package xyz.hyperreal.stomp_server

import typings.sockjsDashClient.sockjsDashClientMod
import typings.stompjs.stompjsMod

import scala.scalajs.js


object StompClient {

  def connect( serverHostname: String, serverPort: Int, serverPath: String ) = {
    val sockjs = new sockjsDashClientMod.^( s"http://$serverHostname:$serverPort/stomp" )
    val client = stompjsMod.over( sockjs )

    client.connect( js.Dynamic.literal(Authorization = "Bearer asdf"), frame => {
      println( "client connected" )
    } )
  }

}