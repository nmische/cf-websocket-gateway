<cfcomponent extends="org.corfield.framework">
<cfscript>

	this.name="wslist";
	this.sessionManagement="true";	

	function setupApplication() {	
		var fis = CreateObject("Java","java.io.FileInputStream").init(ExpandPath("./gateway/websocket.cfg"));
		var properties = CreateObject("Java","java.util.Properties");
		properties.load(fis);
		fis.close();
		
		application.users = {};
		application.listItems = [
				{id=1,label="Item 1"},
				{id=2,label="Item 2"},
				{id=3,label="Item 3"},
				{id=4,label="Item 4"},
				{id=5,label="Item 5"} 
			];
		application.websocketPort = (properties.containsKey("port")) ? properties.getProperty("port") : "1225";
	}
	
	function onSessionEnd(SessionScope,ApplicationScope) {
		StructDelete(arguments.ApplicationScope.users,arguments.SessionScope.username);		
	}

</cfscript>
</cfcomponent>
