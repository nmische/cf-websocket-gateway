<cfcomponent>

	<cffunction name="init">
		<cfargument name="fw">
		<cfscript>
  		variables.fw = fw;
		</cfscript>
	</cffunction>
	
	<cffunction name="login">
		<cfargument name="rc">
		<cfscript>
	
		rc.validationErrors = [];
		if (not StructKeyExists(rc,"username") or Len(rc.username) eq 0) {
			rc.validationErrors = ["Please enter a username."];
		}
		
		if (StructKeyExists(application.users,rc.username)) {
		 	rc.validationErrors = ["Username is already in use. Please select another username."];			
		};		
		
		if(ArrayLen(rc.validationErrors) gt 0) {
			variables.fw.redirect("main.default","validationErrors");
		}
		
		application.users[rc.username]=1;
		session.username=rc.username;
		variables.fw.redirect("sortable.default");	
		
		</cfscript>	
	</cffunction>	
	
	<cffunction name="logout">
		<cfargument name="rc">
		<cfscript>
		StructDelete(application.users,session.username);
		StructDelete(session,"username");
		variables.fw.redirect("main.default");
		</cfscript>	
	</cffunction>
	
	<cffunction name="default">
		<cfargument name="rc">
		<cfscript>
		rc.xa = {
			login = "main.login"
		};
		</cfscript>	
	</cffunction>	
	
</cfcomponent>
