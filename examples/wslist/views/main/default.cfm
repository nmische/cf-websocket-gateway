<h1>Sortable List Demo</h1>

<cfoutput>
<form action="#buildURL(rc.xa.login)#" method="post">

<fieldset class="ui-corner-all">
	<legend>Login</legend>


	<cfif StructKeyExists(rc,"validationErrors")>
		<div class="ui-widget">
			<div style="padding: 0pt 0.7em;" class="ui-state-error ui-corner-all"> 
				<p>
					<span style="float: left; margin-right: 0.3em;" class="ui-icon ui-icon-alert"></span> 
					<strong>Error(s):</strong>
				</p>
				<ul>
					<cfloop array="#rc.validationErrors#" index="error">
						<li>#error#</li>
					</cfloop>
				</ul>
			</div>
		</div>
		<br/>
	</cfif>

	<label for="username">
		Username: <input type="text" id="username" name="username" class="ui-corner-all"/>
	</label>
	
	<div class="formButtons">
		<button type="submit" id="loginBtn" name="loginBtn" value="Login">Login</button>
	</div>	

</fieldset>
	
</form>
</cfoutput>

<cfsavecontent variable="htmlHead">
	<script type="text/javascript">
	$(function() {		
		$("#loginBtn").button();
	});
	</script>
</cfsavecontent>
<cfhtmlhead text="#htmlHead#" />
