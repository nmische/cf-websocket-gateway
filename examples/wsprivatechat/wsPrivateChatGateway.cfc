<cfcomponent>

	<!--- 
		incoming messages have the following keys:
		
		msgType: one of the predefined message types
		username: the username sending the message
		msg: the message you are sending to the user
		destinationWebSocketID: the websocket to send the message to for private messages (optional)
		
		messages may be one of the following types:
		
		connect: new user enters the room
		disconnect: user leaves the room
		public: a public message
		private: a private message
		userlist: a request for a list of users
	--->

	<cffunction name="onIncomingMessage">
		<cfargument name="event" />
		<!--- parse the incoming message --->
		<cfset var incomingMessage = DeserializeJSON(event.data.message) />
		<cfset var outgoingMessage = "" />
		
		<cfinvoke method="#incomingMessage.msgType#" returnVariable="outgoingMessage">
			<cfinvokeargument name="event" value="#arguments.event#"/>
			<cfinvokeargument name="incomingMessage" value="#incomingMessage#"/>
		</cfinvoke>
		
		<cfif IsDefined("outgoingMessage")>
			<cfreturn outgoingMessage />	
		</cfif>
			
	</cffunction>
		
	<cffunction name="onClientOpen">
		<cfargument name="event" />		
		<cfset var key = event.originatorID />		
		<cfset application.userRegistry[key] = "UNREGISTERED" />	
	</cffunction>
	
	<cffunction name="onClientClose">
		<cfargument name="event" />
		<cfset var key = event.originatorID />
		<cfset var username = "" />
		<cfif StructKeyExists(application.userRegistry, key)>
			<cfset username = application.userRegistry[key] />
			<cfset StructDelete(application.userRegistry,key) />
			<cfreturn newMessage(msgType="disconnect",username=username) />
		</cfif>		
	</cffunction>
	
	<!--- message handlers --->
	
	<cffunction name="connect">
		<cfargument name="event">
		<cfargument name="incomingMessage">			
		<cfset var key = event.originatorID />
		<cfset application.userRegistry[key] = incomingMessage.username />			
		<cfreturn newMessage(argumentCollection=incomingMessage) />				
	</cffunction>
	
	<cffunction name="public">
		<cfargument name="event">
		<cfargument name="incomingMessage">	
		<cfreturn newMessage(argumentCollection=incomingMessage) />				
	</cffunction>
	
	<cffunction name="private">
		<cfargument name="event">
		<cfargument name="incomingMessage">
		<!--- send private messages to the sender and destination only --->
		<cfset var destinationWebSocketIDs = [] />
		<cfset var outgoingMsg = "" />		
		<cfset ArrayAppend(destinationWebSocketIDs,incomingMessage.destinationWebSocketID) />
		<cfset ArrayAppend(destinationWebSocketIDs,event.originatorID) />		
		<cfreturn newMessage(msgType="private",
			username=incomingMessage.username,
			msg=incomingMessage.msg,
			destinationWebSocketIDs=destinationWebSocketIDs) />
	</cffunction>
	
	<cffunction name="userlist">
		<cfargument name="event">
		<cfargument name="incomingMessage">	
		<!--- send usrlist requests back to the sender only --->
		<cfset var outgoingMsg = Duplicate(incomingMessage) />	
		<cfset outgoingMsg.msg = application.userRegistry />
		<cfset outgoingMsg.destinationWebSocketID = event.originatorID />
		<cfreturn newMessage(argumentCollection=outgoingMsg) />						
	</cffunction>
	
	<cffunction name="newMessage">
		<cfargument name="msgType"/>
		<cfargument name="username"/>
		<cfargument name="msg"/>
		<cfargument name="destinationWebSocketID"/>
		<cfargument name="destinationWebSocketIDs"/>
		
		<cfset m = {} />
		<cfset message = {} />
		
		<cfif StructKeyExists(arguments,"msgType")>
			<cfset message["msgType"] = arguments.msgType />
		</cfif>
		<cfif StructKeyExists(arguments,"username")>
			<cfset message["username"] = arguments.username />
		</cfif>
		<cfif StructKeyExists(arguments,"msg")>
			<cfset message["msg"] = arguments.msg />
		</cfif>
		
		<cfset m["MESSAGE"] = SerializeJSON(message) />
		
		<cfif StructKeyExists(arguments,"destinationWebSocketID")>
			<cfset m["DESTINATIONWEBSOCKETID"] = arguments.destinationWebSocketID />
		</cfif>
		
		<cfif StructKeyExists(arguments,"destinationWebSocketIDs")>
			<cfset m["DESTINATIONWEBSOCKETIDS"] = arguments.destinationWebSocketIDs />
		</cfif>
			
		<cfreturn m/>
		
	</cffunction>

</cfcomponent>
