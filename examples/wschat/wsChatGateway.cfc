<cfcomponent>

	<cffunction name="onIncomingMessage">
		<cfargument name="event" />
		<!--- send the message to all clients --->
		<cfset var msg = {} />
		<cfset msg["MESSAGE"] = event.data.message />
		<cfreturn msg />		
	</cffunction>
	
	<cffunction name="onClientOpen">
	
	</cffunction>
	
	<cffunction name="onClientClose">
	
	</cffunction>

</cfcomponent>
