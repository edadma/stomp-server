//import StompServer from '../stomp-server'
const StompServer = require( '../stomp-server' ).StompServer
import * as StompJS from 'stompjs'
import SockJS from 'sockjs-client'

const authorize = (headers: any) => {return true}

const stomp = new StompServer( 'Test Server/0.1', '0.0.0.0', 15674, '/stomp', authorize, true )

const socket = new SockJS( 'http://127.0.0.1:15674/stomp' )
const client = StompJS.over( socket )

client.connect(
    { 'accept-version': '1.2' },
    frame => {
        console.log( 'connected' )

        client.subscribe(
            'data',
            message => {
                console.log( 'message', message )
            },
            { Authorization: 'Bearer anything' } )
        stomp.send( 'data', 'this is a message', 'text/plain' )
    }
)
