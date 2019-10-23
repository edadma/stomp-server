package xyz.hyperreal

import scala.scalajs.js.RegExp


package object stomp_server {

  private [stomp_server] val stompMessageRegex = RegExp( """([A-Z]+)\r?\n?(.*?)\r?\n\r?\n([^\00]*)\00(?:\r?\n)*""", "s" )
  private [stomp_server] val headerRegex = """([a-zA-Z0-9-\\]+):(.+)"""r

  private [stomp_server] def parseMessage( message: String ) = {
    stompMessageRegex.exec( message ) match {
      case null => None
      case array =>
        val Array( _, command, headers, body ) = array.toArray
        val headerMap = headerRegex findAllMatchIn headers.toString map (m => unescape( m.group(1) ) -> unescape( m.group(2) )) toMap

        Some( (command.toString, headerMap, body.toString) )
    }
  }

  private def unescape( s: String ) = s.
    replace( "\\r", "\r" ).
    replace( "\\n", "\n" ).
    replace( "\\c", ":" ).
    replace( "\\\\", "\\" )

}