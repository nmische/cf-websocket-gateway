<cfcomponent>

	<cffunction name="onIncomingMessage">
		<cfargument name="event" />
		<!--- send the message to all clients --->
		<cfset var msg = {} />
		<cfset msg["MESSAGE"] = event.data.message />
		<cfreturn msg />		
	</cffunction>
	
	<cffunction name="onOpen">
	
	</cffunction>
	
	<cffunction name="onClose">
	
	</cffunction>

</cfcomponent>
