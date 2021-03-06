import { StompServer } from '../../stomp-server'
import * as StompJS from 'stompjs'
import SockJS from 'sockjs-client'

const authorize = (headers: any) => {
	console.log( headers )
	return true
}

const stomp = new StompServer( 'Test Server/0.1', '0.0.0.0', 15674, '/stomp', authorize, authorize, true )

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
    }
)

setTimeout( () => {
    console.log( stomp.queues() )
    console.log( 'sending test message' )
    stomp.send( 'data', 'this is a message' )
}, 200 )
