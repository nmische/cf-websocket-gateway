<cfsavecontent variable="htmlHead">
	
	<script language="JavaScript">
	
	var WebSocketDemo = function () {
	
		var ws;
		// Set URL of your WebSocketMain.swf here:
    	WEB_SOCKET_SWF_LOCATION = "assets/swf/WebSocketMain.swf";
    	// Set this to dump debug message from Flash to console.log:
    	WEB_SOCKET_DEBUG = false;		

		return {		
			init: function() {
				ws = new WebSocket("<cfoutput>#rc.websocketurl#</cfoutput>");
				ws.onopen = function() {
					// make the list sortable
					$("#sortable").sortable({
						stop: function (event, ui) {
							var arrayOrder = $("#sortable").sortable("toArray");
							WebSocketDemo.sendMessage(arrayOrder);
						}
					});										
				}
				ws.onmessage = function(e) {				
					// reorder the list					
					data = JSON.parse(e.data);
		            $("#sortable").sortable("fromArray",data.body.msg);
				}
				ws.onclose = function() {
					// do nothing
				}			
			},
			sendMessage: function(message) {
				var msg = {
                    body : { msg: message },
					heders : { username: "<cfoutput>#rc.username#</cfoutput>" }
				};					
				if(ws) {
				    ws.send(JSON.stringify(msg));
				}
			}
		}

	}();
	

	$(function() {
		
		// if we don't have native WebSocket support, use Flash
		if (!window.WebSocket) {				
                var head = $("head");				
				head.append('<script type="text/javascript" src="assets/js/swfobject.js"><\/script>');
				head.append('<script type="text/javascript" src="assets/js/FABridge.js"><\/script>');
				head.append('<script type="text/javascript" src="assets/js/web_socket.js"><\/script>');			
        }
		
		// set up our WebSocket connection
		WebSocketDemo.init();		
		
		// add a function to deserialize the server response
		$.ui.sortable.prototype.fromArray = function( newOrder ) {
			$.each(newOrder,function( intIndex, objValue ){
				var selector = "#" + objValue;
				$(selector).fadeOut(600,function(){
					$(this).parent().append(this);
					$(this).fadeIn(600);
				});
			})
		}

		// disable selection on the list
		$("#sortable").disableSelection();

		// style the logout button
		$("#logoutBtn").button();
	});
	</script>
	<style type="text/css">
		/*demo page css*/
		#sortable { list-style-type: none; margin: 0; padding: 0; width: 60%; }
		#sortable li { margin: 0 3px 3px 3px; padding: 0.4em; padding-left: 1.5em; font-size: 1.4em; height: 18px; }
		#sortable li span { position: absolute; margin-left: -1.3em; }
	</style>
</cfsavecontent>
<cfhtmlhead text="#htmlHead#" />

<cfoutput>
<h1>Sortable List Demo</h1>

<div class="demo">

<ul id="sortable">
	<cfloop array="#rc.listItems#" index="item">
		<li class="ui-state-default" id="item_#item.id#"><span class="ui-icon ui-icon-arrowthick-2-n-s"></span>#item.label#</li>
	</cfloop>
</ul>

<div class="formButtons">
	<a href="#buildURL("main.logout")#" id="logoutBtn">Logout</a>
</div>

</div>
</cfoutput>


