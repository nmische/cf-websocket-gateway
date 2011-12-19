# ColdFusion WebSocket Gateway #

This is a ColdFusion event gateway for [WebSocket](http://tools.ietf.org/html/rfc6455) messaging. This implementation is based on the [Java-WebSocket](http://github.com/TooTallNate/Java-WebSocket) server.

## Installing the Gateway ##

* Downlaod the `cf-websocket-gateway-java-websocket-X.X.zip` package from [Github](http://github.com/nmische/cf-websocket-gateway/downloads).
* Extract the `cf-websocket-gateway-X.X.jar` and the `WebSocket.jar` to the ColdFusion classpath.
** The best place to put the jar is in the `{cfusion.root}/gateway/lib` folder.
** For example `C:\ColdFusion9\gateway\lib` or `/Applications/JRun4/servers/cfusion/cfusion-ear/cfusion-war/WEB-INF/cfusion/gateway/lib/`
* Restart ColdFusion, if necessary.
* Log in to the ColdFusion administrator and navigate to the Event Gateways > Gateway Types page.
* Under Add / Edit ColdFusion Event Gateway Types enter the following:
** Type Name: WebSocket
** Description: Handles WebSocket Messaging
** Java Class: org.riaforge.websocketgateway.WebSocketGateway
** Startup Timeout: 30 seconds
** Stop on Startup Timeout: checked

## Configuring the Gateway ##

* Log in to the ColdFusion administrator and navigate to the Event Gateways > Gateway Instances page.
* Under Add / Edit ColdFusion Event Gateway Instances enter the following:
** Gateway ID: An name for your gateway instance.
** Gateway Type: WebSocket - Handles WebSocket Messaging
** CFC Path: Path to the CFC listner. For more about the listener CFC see Handling Incoming Messages.
** Configuration File: The path to the (optional) configuration file. If not provided the WebSocket server will listen on port 1225. Below is a sample configuration file.

    # port - The port the WebSocket server should listen on.
    port=1225 

## Handling Incoming Messages ##

### Events ###

The CFC listener may implement any of the three following methods to listen
to WebSocket Gateway events:

* onClose(event) - sent when a client connection is closed. The event has the following fields:
** CfcMethod: Listener CFC method name, onClientClose in this case
** Data.CONN: The underlying org.riaforge.websocketgateway.WebSocket object that fired the event
** GatewayType: Always "WebSocket"
** OriginatorID: A key identifying the underlying org.riaforge.websocketgateway.WebSocket object that fired the event
* onOpen(event) - sent when a client connection is opened. The event has the following fields:
** CfcMethod: Listener CFC method name, onClientOpen in this case
** Data.CONN: The underlying org.riaforge.websocketgateway.WebSocket object that fired the event
** GatewayType: Always "WebSocket"
** OriginatorID: A key identifying the underlying org.riaforge.websocketgateway.WebSocket object that fired the event
* onIncomingMessage(event) - sent when a client sends a message. The event has the following fields:
** CfcMethod: Listener CFC method, onIncomingMessage in this case
** Data.CONN: The underlying org.riaforge.websocketgateway.WebSocket object that fired the event
** Data.MESSAGE: Message contents
** GatewayType: Always "WebSocket"
** OriginatorID: A key identifying the underlying org.riaforge.websocketgateway.WebSocket object that fired the event

### Targeted Messaging ###

By default outgoing messages are sent to all connected WebSocket clients. You may target an outgoing message such that it is only sent to a single client or subset of connected clients using either the DESTINATIONWEBSOCKETID or DESTINATIONWEBSOCKETIDS event fields. To send to a single client the DESTINATIONWEBSOCKETID field should be set to the key of the targeted client. To send to a subset of clients, the DESTINATIONWEBSOCKETIDS field should be set to an array of target keys.

### Example Listeners ###

Below is an example listener CFC that echos a message back to all connected clients:

    <cfcomponent>
      <cffunction name="onIncomingMessage">
        <cfargument name="event" />
        <cfset var msg = {} />
        <cfset msg["MESSAGE"] = event.data.message />
        <cfreturn msg />
      </cffunction>
    </cfcomponent>

Below is an example listener CFC that echos a message back to the client that sent the message:

    <cfcomponent>
      <cffunction name="onIncomingMessage">
        <cfargument name="event" />
        <cfset var msg = {} />
        <cfset msg["MESSAGE"] = event.data.message />
        <cfset msg["DESTINATIONWEBSOCKETID"] = event.originatorID />
        <cfreturn msg />
      </cffunction>
    </cfcomponent>
