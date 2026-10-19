/**
 *  NuHeat Connect
 *
 *  Parent app for NuHeat Signature thermostats using the NuHeat OAuth2 API (https://api.mynuheat.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Setup:
 *	- Request NuHeat API access with the form at the bottom of https://go.chemelex.com/connected-home
 *		- Redirect URI: https://cloud.hubitat.com/oauth/stateredirect
 *		- NuHeat replies with a ClientID and ClientSecret
 *		- The app asks for scopes openapi openid offline_access at login; offline_access is needed for refresh tokens
 *	- Install this app and the "NuHeat Signature Thermostat" driver in Hubitat
 *	- Enable OAuth for this app in the Hubitat Apps Code editor (the app tries to do this itself)
 *	- Add the app, enter ClientID / ClientSecret, log in to NuHeat, select thermostats
 *
 **/

//file:noinspection GroovySillyAssignment
//file:noinspection GrDeprecatedAPIUsage
//file:noinspection GroovyDoubleNegation
//file:noinspection GroovyUnusedAssignment
//file:noinspection unused
//file:noinspection SpellCheckingInspection
//file:noinspection GroovyFallthrough
//file:noinspection GrMethodMayBeStatic
//file:noinspection GroovyAssignabilityCheck
//file:noinspection UnnecessaryQualifiedReference

import groovy.transform.Field
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

static String version(){ return "v1.0.3.20261013" }

definition(
	name:			"NuHeat Connect",
	namespace:		"ericvitale",
	author:			"ericvitale@gmail.com",
	description:	"Connect NuHeat Signature thermostats using the NuHeat OAuth2 API",
	category:		"Integrations",
	iconUrl:		"",
	iconX2Url:		"",
	iconX3Url:		"",
	singleInstance:	true,
	oauth:			true
)

preferences {
	page(name: "mainPage")
	page(name: "authPage")
	page(name: "thermostatsPage")
}

mappings {
	path("/callback"){action: [GET: "callback"]}
	path("/oauth/callback"){action: [GET: "callback"]}
}

@Field static final String sTRACE='TRACE'
@Field static final String sDEBUG='DEBUG'
@Field static final String sINFO='INFO'
@Field static final String sWARN='WARN'
@Field static final String sERROR='ERROR'

@Field static final String sDRIVER='NuHeat Signature Thermostat1'
@Field static final String sNAMESPACE='ericvitale'

// connection status, also sent to devices as the apiStatus attribute
@Field static final String sFULL='connected'
@Field static final String sWARNING='warning'
@Field static final String sLOST='disconnected'

@Field static final Long lREFRESH_MARGIN=300000L		// refresh the access token 5 minutes before it expires
@Field static final Integer iREATTEMPT_SECS=30			// backoff step between failed token refreshes
@Field static final Integer iMAX_ATTEMPTS=5				// failed refreshes before the connection is declared lost
@Field static final Integer iMAX_TIMEOUT_ATTEMPTS=20	// same, when the failures are timeouts
@Field static final Long lMIN_CHILD_REFRESH=60000L		// minimum time between polls requested by devices
@Field static final Long lPOLL_TIMEOUT=25000L			// a poll still running after this is assumed dead

// serializes token refreshes: NuHeat refresh tokens are one-time use, so two concurrent refreshes would lose the connection
@Field static Semaphore tokenLock=new Semaphore(1)
@Field static Long tokenLockTime=0L
@Field static final Long lLOCK_STALE=120000L			// a lock held longer than this is assumed abandoned

static String getCallbackUrl()		{ return "https://cloud.hubitat.com/oauth/stateredirect" }
static String getAuthorizeUrl()		{ return "https://identity.mynuheat.com/connect/authorize" }
static String getTokenUrl()			{ return "https://identity.mynuheat.com/connect/token" }
static String getApiUrl()			{ return "https://api.mynuheat.com" }
static String getScopes()			{ return "openapi openid offline_access" }

String getStateUrl()				{ return "${getHubUID()}/apps/${app.id}/callback?access_token=${state.accessToken}".toString() }

//--------------------------------------------------------
// Pages

def mainPage(){
	Boolean connected=!!(String)atomicState.authToken
	Boolean driverOk=driverInstalled()
	dynamicPage(name: "mainPage", title: "NuHeat Connect ${version()}", install: driverOk, uninstall: true){
		if(!driverOk){
			section(){
				paragraph "<b style='color:red'>The '${sDRIVER}' driver is not installed. Add it in Drivers Code, then re-open this app.</b>"
			}
			return
		}
		if((String)atomicState.connected == sLOST){
			section(){
				paragraph "<b style='color:red'>The connection to NuHeat was lost. Re-authorize below.</b>"
			}
		}
		section("NuHeat Account"){
			href "authPage", title: "NuHeat API Authorization", description: connected ? "Connected" : "Not connected - tap to connect",
				state: connected ? "complete" : null
		}
		if(connected){
			section("Thermostats"){
				href "thermostatsPage", title: "Select Thermostats", description: settings.thermostats ? "${settings.thermostats.size()} selected" : "Tap to choose"
			}
		}
		section("Options"){
			input "pollInterval", "enum", title: "Polling interval (minutes)", options: ["1","5","10","15","30"], defaultValue: "5"
			input "notifyDevices", "capability.notification", title: "Notify these devices if the NuHeat connection is lost", multiple: true, required: false
			input "logging", "enum", title: "Log Level", defaultValue: sINFO, options: [sTRACE, sDEBUG, sINFO, sWARN, sERROR]
		}
	}
}

def authPage(){
	if(!initializeEndpoint()){
		return dynamicPage(name: "authPage", title: "OAuth Initialization Failure", nextPage: "mainPage"){
			section(){
				paragraph "Could not create a Hubitat access token. Enable OAuth for the 'NuHeat Connect' app in the Hubitat Apps Code editor, then try again."
			}
		}
	}
	Boolean connected=!!(String)atomicState.authToken
	dynamicPage(name: "authPage", title: "NuHeat API Authorization", nextPage: "mainPage"){
		section("NuHeat OAuth client credentials"){
			paragraph "Request a ClientID and ClientSecret with the form at the bottom of https://go.chemelex.com/connected-home. Enter ${getCallbackUrl()} as the redirect URI."
			input "clientId", "text", title: "Client ID", required: true, submitOnChange: true
			input "clientSecret", "password", title: "Client Secret", required: true, submitOnChange: true
		}
		if((String)settings.clientId && (String)settings.clientSecret){
			section(){
				Long exp=(Long)atomicState.authTokenExpires
				if(connected) paragraph "Connected. Token expires ${exp ? new Date(exp).format('MM/d/yyyy hh:mm:ss a', (TimeZone)location.timeZone) : 'unknown'}"
				href url: oauthInitUrl(), style: "external", required: false,
					title: connected ? "Re-authorize NuHeat account" : "Log in to NuHeat",
					description: "Click to log in to NuHeat and authorize Hubitat access"
			}
		}
	}
}

def thermostatsPage(){
	Map<String,String> options=[:]
	List tstats=apiGetSync("/api/v1/Thermostat")
	Map<String,String> names=[:]
	tstats?.each{ Map t ->
		options[(String)t.serialNumber]="${t.name ?: 'Thermostat'} (${t.serialNumber})".toString()
		names[(String)t.serialNumber]=(String)t.name
	}
	if(names) state.tstatNames=names
	dynamicPage(name: "thermostatsPage", title: "Select Thermostats", nextPage: "mainPage"){
		section(){
			if(!options) paragraph "No thermostats found on the NuHeat account (or the API call failed - check the logs)."
			input "thermostats", "enum", title: "Thermostats", options: options, multiple: true, required: false
		}
	}
}

//--------------------------------------------------------
// Lifecycle

void installed(){
	log("Installed", sINFO)
	state.installed=true
	initialize()
}

void updated(){
	log("Updated", sINFO)
	initialize()
}

void uninstalled(){
	unschedule()
	getChildDevices().each{ deleteChildDevice(it.deviceNetworkId) }
}

void initialize(){
	unschedule()
	unsubscribe()
	state.installed=true
	state.versionLabel=version()
	atomicState.remove('pollStart')
	subscribe(location, "systemStart", "rebooted")
	syncChildDevices()
	if(!(String)atomicState.authToken){
		log("Not connected to NuHeat", sWARN)
		if((String)atomicState.connected == sLOST) runEvery3Hours("notifyApiLost")
		return
	}
	schedulePolling()
	// keep the tokens fresh and polling alive even if polling is failing or slow
	runEvery10Minutes("watchdog")
	poll()
}

void rebooted(evt){
	log("Hub rebooted, re-initializing", sINFO)
	initialize()
}

Integer pollMinutes(){
	return ((String)settings.pollInterval ?: "5") as Integer
}

void schedulePolling(){
	unschedule("poll")
	switch((String)settings.pollInterval ?: "5"){
		case "1":  runEvery1Minute("poll"); break
		case "10": runEvery10Minutes("poll"); break
		case "15": runEvery15Minutes("poll"); break
		case "30": runEvery30Minutes("poll"); break
		default:   runEvery5Minutes("poll")
	}
}

/*
 * Checks once whether the child driver exists by creating and deleting a test device,
 * so setup fails up front instead of when thermostats are created.
 */
Boolean driverInstalled(){
	if((Boolean)state.driverOk) return true
	String dni="nuheat-driver-test-${now()}".toString()
	Boolean ok=false
	try{
		def d=addChildDevice(sNAMESPACE, sDRIVER, dni, [name: "NuHeat driver test", isComponent: false])
		ok=(d != null)
	}catch(e){
		log("Driver check failed: ${e}", sWARN)
	}
	try{
		if(getChildDevice(dni)) deleteChildDevice(dni)
	}catch(e){
		log("Could not delete driver test device ${dni}: ${e}", sWARN)
	}
	if(ok) state.driverOk=true
	return ok
}

void syncChildDevices(){
	List<String> selected=(List<String>)settings.thermostats ?: []
	selected.each{ String sn ->
		if(!getChildDevice(sn)){
			log("Creating thermostat device ${sn}", sINFO)
			try{
				addChildDevice(sNAMESPACE, sDRIVER, sn, [name: sDRIVER, label: "NuHeat ${state.tstatNames?.get(sn) ?: sn}".toString(), isComponent: false])
			}catch(e){
				log("Could not create device ${sn} - is the '${sDRIVER}' driver installed? ${e}", sERROR)
			}
		}
	}
	getChildDevices().each{
		if(!selected.contains(it.deviceNetworkId)){
			log("Removing thermostat device ${it.deviceNetworkId}", sINFO)
			deleteChildDevice(it.deviceNetworkId)
		}
	}
	String status=(String)atomicState.connected
	if(status) getChildDevices().each{ it.setApiStatus(status) }
}

//--------------------------------------------------------
// OAuth

Boolean initializeEndpoint(Boolean disableRetry=false){
	if(!(String)state.accessToken){
		try{
			createAccessToken()
		}catch(e){
			log("createAccessToken failed ${e}", sDEBUG)
		}
		if(!(String)state.accessToken && !disableRetry){
			enableOauth()
			return initializeEndpoint(true)
		}
	}
	return !!(String)state.accessToken
}

// try to enable oauth on HE for this app
private void enableOauth(){
	Map params=[
		uri: "http://localhost:8080/app/edit/update?_action_update=Update&oauthEnabled=true&id=${app.appTypeId}".toString(),
		headers: ['Content-Type':'text/html;charset=utf-8']
	]
	try{
		httpPost(params){ resp -> }
	}catch(e){
		log("enableOauth failed ${e}", sERROR)
	}
}

String oauthInitUrl(){
	state.oauthInitState=getStateUrl()
	Map oauthParams=[
		response_type:	"code",
		client_id:		(String)settings.clientId,
		scope:			getScopes(),
		redirect_uri:	getCallbackUrl(),
		state:			(String)state.oauthInitState
	]
	return getAuthorizeUrl()+"?"+toQueryString(oauthParams)
}

def callback(){
	log("callback() params: ${params}", sDEBUG)
	String code=params.code
	String oauthState=params.state
	Boolean ok=false
	if(params.error){
		log("NuHeat authorization error: ${params.error} ${params.error_description ?: ''}", sERROR)
	}else if(code && oauthState == (String)state.oauthInitState){
		Boolean locked=acquireTokenLock()
		try{
			ok=(Boolean)requestToken([
				grant_type:		"authorization_code",
				code:			code,
				redirect_uri:	getCallbackUrl()
			]).ok
		}finally{
			if(locked) releaseTokenLock()
		}
		if(ok){
			state.remove('oauthInitState')
			apiRestored()
			// on first setup installed() runs initialize when the user clicks Done
			if((Boolean)state.installed) runIn(2, "initialize")
		}
	}else{
		log("callback() state mismatch or missing code", sWARN)
	}
	render contentType: 'text/html', status: 200, data: connectionStatus(ok ?
		"Your NuHeat account is now connected.<br>Close this window and click 'Next' / 'Done' in Hubitat." :
		"The connection to NuHeat could not be established.<br>Close this window and check the Hubitat logs.")
}

/*
 * The only path that refreshes the access token. Returns true when atomicState.authToken is usable.
 * rejectedToken: a token the API just answered 401 for; it is refreshed even if it is not near expiry,
 * unless another execution already replaced it.
 */
Boolean ensureToken(String rejectedToken=null){
	// never connected
	if(!(String)atomicState.authToken && !(String)atomicState.refreshToken) return false
	if(tokenValid(rejectedToken)) return true

	if(!acquireTokenLock()){
		log("Timed out waiting for another token refresh", sWARN)
		return tokenValid(rejectedToken)
	}
	try{
		// another execution may have refreshed while we waited for the lock
		if(tokenValid(rejectedToken)) return true
		return refreshAuthToken()
	}finally{
		releaseTokenLock()
	}
}

private Boolean acquireTokenLock(){
	if(tokenLock.tryAcquire(30, TimeUnit.SECONDS)){
		tokenLockTime=now()
		return true
	}
	if(now() - tokenLockTime > lLOCK_STALE){
		log("Token lock abandoned, resetting it", sWARN)
		tokenLock=new Semaphore(0)	// acquired by this execution
		tokenLockTime=now()
		return true
	}
	return false
}

private void releaseTokenLock(){
	tokenLock.release()
}

private Boolean tokenValid(String rejectedToken){
	String tok=(String)atomicState.authToken
	Long exp=(Long)atomicState.authTokenExpires ?: 0L
	return tok && tok != rejectedToken && now() < exp - lREFRESH_MARGIN
}

// caller must hold tokenLock
private Boolean refreshAuthToken(){
	String rt=(String)atomicState.refreshToken
	if(!rt){
		apiLost("no refresh token")
		return false
	}
	Map res=requestToken([grant_type: "refresh_token", refresh_token: rt])
	if((Boolean)res.ok){
		atomicState.remove('reAttempt')
		unschedule("retryRefresh")
		if((String)atomicState.connected != sFULL) apiRestored()
		return true
	}

	Integer attempts=((Integer)atomicState.reAttempt ?: 0) + 1
	atomicState.reAttempt=attempts
	Integer maxAttempts=(Boolean)res.timeout ? iMAX_TIMEOUT_ATTEMPTS : iMAX_ATTEMPTS
	if(attempts > maxAttempts){
		apiLost("token refresh failed ${attempts - 1} times (last status ${res.status})")
		return false
	}
	Integer delay=iREATTEMPT_SECS * Math.min(attempts, 12)
	log("Token refresh failed (status ${res.status}), attempt ${attempts} of ${maxAttempts}; retrying in ${delay} seconds", sWARN)
	runIn(delay, "retryRefresh")
	if(attempts > 3 && (String)atomicState.connected == sFULL) setConnected(sWARNING)
	// the current access token may still work until it expires
	String tok=(String)atomicState.authToken
	return tok && now() < ((Long)atomicState.authTokenExpires ?: 0L)
}

void retryRefresh(){
	if(!acquireTokenLock()) return
	try{
		if(!tokenValid(null)) refreshAuthToken()
	}finally{
		releaseTokenLock()
	}
}

void watchdog(){
	if((String)atomicState.connected == sLOST) return
	if(!ensureToken()) return
	Long last=(Long)atomicState.lastPollRun ?: 0L
	if(settings.thermostats && now() - last > (pollMinutes() + 2) * 60000L){
		log("Polling has not run for ${(now() - last) / 60000L} minutes, rescheduling", sWARN)
		schedulePolling()
		poll()
	}
}

// caller must hold tokenLock; returns [ok: Boolean, status: Integer, timeout: Boolean]
private Map requestToken(Map body){
	body.client_id=(String)settings.clientId
	body.client_secret=(String)settings.clientSecret
	Map params=[
		uri:				getTokenUrl(),
		requestContentType:	"application/x-www-form-urlencoded",
		contentType:		"application/json",
		body:				body,
		timeout:			30
	]
	Map res=[ok: false, status: null, timeout: false]
	try{
		httpPost(params){ resp ->
			res.status=resp.status
			if(resp.status == 200 && resp.data?.access_token){
				Map d=(Map)resp.data
				atomicState.authToken=(String)d.access_token
				if(d.refresh_token) atomicState.refreshToken=(String)d.refresh_token
				atomicState.authTokenExpires=now() + ((d.expires_in ?: 3600) as Long) * 1000L
				log("Obtained NuHeat access token, expires in ${d.expires_in} seconds", sINFO)
				res.ok=true
			}else{
				log("Token request returned ${resp.status}", sERROR)
			}
		}
	}catch(groovyx.net.http.HttpResponseException e){
		res.status=e.statusCode
		log("Token request (${body.grant_type}) failed ${e.statusCode}: ${e.response?.data}", sERROR)
	}catch(e){
		res.timeout="${e}".contains("Timeout")
		log("Token request (${body.grant_type}) failed ${e}", sERROR)
	}
	return res
}

private Map authHeaders(String token){
	return [Authorization: "Bearer ${token}".toString(), Accept: "application/json"]
}

//--------------------------------------------------------
// Connection status

void setConnected(String status){
	atomicState.connected=status
	String base=((String)app.label ?: (String)app.name).replaceAll(/\s*<span.*$/, '')
	app.updateLabel(status == sFULL ? base : "${base} <span style='color:${status == sLOST ? 'red' : 'orange'}'>(${status})</span>".toString())
	getChildDevices().each{ it.setApiStatus(status) }
}

void apiRestored(){
	if((String)atomicState.connected != sFULL) log("Connection to NuHeat restored", sINFO)
	atomicState.remove('reAttempt')
	unschedule("notifyApiLost")
	setConnected(sFULL)
}

void apiLost(String why){
	if((String)atomicState.connected == sLOST) return
	log("Lost connection to NuHeat: ${why}. Re-authorize in the NuHeat Connect app.", sERROR)
	atomicState.remove('authToken')
	atomicState.remove('refreshToken')
	atomicState.remove('authTokenExpires')
	atomicState.remove('reAttempt')
	setConnected(sLOST)
	unschedule("poll")
	unschedule("watchdog")
	unschedule("retryRefresh")
	runEvery3Hours("notifyApiLost")
	notifyApiLost()
}

void notifyApiLost(){
	if((String)atomicState.connected != sLOST){
		unschedule("notifyApiLost")
		return
	}
	String msg="NuHeat Connect is disconnected from NuHeat. Open the NuHeat Connect app and log in again."
	log(msg, sERROR)
	settings.notifyDevices?.each{ it.deviceNotification(msg) }
}

//--------------------------------------------------------
// API

// synchronous GET used by the setup pages
def apiGetSync(String path, Boolean retry=true){
	if(!ensureToken()) return null
	String token=(String)atomicState.authToken
	def result=null
	try{
		httpGet([uri: getApiUrl()+path, headers: authHeaders(token), contentType: "application/json", timeout: 20]){ resp ->
			result=resp.data
		}
	}catch(groovyx.net.http.HttpResponseException e){
		if(e.statusCode == 401 && retry && ensureToken(token)) return apiGetSync(path, false)
		log("GET ${path} failed ${e.statusCode}", sERROR)
	}catch(e){
		log("GET ${path} failed ${e}", sERROR)
	}
	return result
}

// scheduled, and after commands; only skipped if a poll is already running
void poll(){
	if((String)state.versionLabel != version()){
		log("Code updated to ${version()}, re-initializing", sINFO)
		updated()
		return
	}
	if((String)atomicState.connected == sLOST) return
	atomicState.lastPollRun=now()
	Long start=(Long)atomicState.pollStart
	if(start && now() - start < lPOLL_TIMEOUT){
		log("Poll already running, skipping", sDEBUG)
		return
	}
	if(!ensureToken()) return
	atomicState.pollStart=now()
	startGroupRequest((String)atomicState.authToken, false)
}

private void startGroupRequest(String token, Boolean retried){
	asynchttpGet("groupHandler", [uri: getApiUrl()+"/api/v1/Group", headers: authHeaders(token), contentType: "application/json", timeout: 20],
		[token: token, retried: retried])
}

// get groups (away mode) first, then thermostats
void groupHandler(resp, Map data){
	Map<String,Boolean> away=[:]
	if(resp.status == 200){
		resp.getJson()?.each{ Map g -> away[(String)g.groupId]=(Boolean)g.awayMode }
	}else if(resp.status == 401){
		if(!(Boolean)data.retried && ensureToken((String)data.token)){
			startGroupRequest((String)atomicState.authToken, true)
		}else{
			pollDone()
		}
		return
	}else{
		log("GET Group returned ${resp.status}", sWARN)
	}
	asynchttpGet("thermostatHandler", [uri: getApiUrl()+"/api/v1/Thermostat", headers: authHeaders((String)data.token), contentType: "application/json", timeout: 20], [away: away])
}

void thermostatHandler(resp, Map data){
	pollDone()
	if(resp.status != 200){
		log("GET Thermostat returned ${resp.status} ${resp.hasError() ? resp.getErrorMessage() : ''}", sERROR)
		return
	}
	atomicState.lastPoll=now()
	Map<String,Boolean> away=(Map<String,Boolean>)data.away ?: [:]
	List tstats=resp.getJson()
	saveGroupIds(tstats)
	tstats?.each{ Map t ->
		def dev=getChildDevice((String)t.serialNumber)
		if(dev){
			t.awayMode=away[(String)t.groupId]
			dev.updateFromApi(t)
		}
	}
}

// serial number -> NuHeat group id, needed to set away mode
private void saveGroupIds(List tstats){
	Map<String,String> groups=[:]
	tstats?.each{ Map t -> if(t.groupId) groups[(String)t.serialNumber]=(String)t.groupId }
	if(groups) atomicState.tstatGroups=groups
}

private void pollDone(){
	atomicState.remove('pollStart')
}

// called by child devices; throttled since every device refresh would otherwise poll the whole account
void componentRefresh(dev){
	Long last=(Long)atomicState.lastPoll
	if(last && now() - last < lMIN_CHILD_REFRESH){
		log("Refresh from ${dev.displayName} skipped, last poll was ${(now() - last) / 1000L} seconds ago", sDEBUG)
		return
	}
	poll()
}

// called by child devices: values are ThermostatUpdateModel fields (setPointTemp, scheduleMode, holdSetPointDateTime)
Boolean componentSetThermostat(dev, Map values){
	return apiPut("/api/v1/Thermostat", [serialNumber: dev.deviceNetworkId] + values)
}

// called by child devices; away mode belongs to the NuHeat group, so it changes every thermostat in that group
Boolean componentSetAwayMode(dev, Boolean awayMode){
	String sn=dev.deviceNetworkId
	String groupId=((Map)atomicState.tstatGroups)?.get(sn)
	if(!groupId){
		saveGroupIds((List)apiGetSync("/api/v1/Thermostat"))
		groupId=((Map)atomicState.tstatGroups)?.get(sn)
	}
	if(!groupId){
		log("No NuHeat group found for thermostat ${sn}; cannot set away mode", sERROR)
		return false
	}
	return apiPut("/api/v1/Group", [groupId: groupId, awayMode: awayMode])
}

// returns true on a 2xx response; polls shortly after so all devices pick up the change
private Boolean apiPut(String path, Map body, Boolean retry=true){
	if(!ensureToken()){
		log("Not connected to NuHeat", sERROR)
		return false
	}
	String token=(String)atomicState.authToken
	log("PUT ${path} ${body}", sDEBUG)
	Boolean ok=false
	try{
		httpPut([uri: getApiUrl()+path, headers: authHeaders(token), requestContentType: "application/json", body: body, timeout: 20]){ resp ->
			log("PUT ${path} response ${resp.status}", sTRACE)
			ok=resp.status >= 200 && resp.status < 300
		}
	}catch(groovyx.net.http.HttpResponseException e){
		if(e.statusCode == 401 && retry && ensureToken(token)){
			return apiPut(path, body, false)
		}
		log("PUT ${path} failed ${e.statusCode}: ${e.response?.data}", sERROR)
	}catch(e){
		log("PUT ${path} failed ${e}", sERROR)
	}
	runIn(15, "poll")
	return ok
}

//--------------------------------------------------------
// Helpers

private static String encodeURIComponent(value){
	return URLEncoder.encode("${value}".toString(), 'UTF-8').replaceAll('\\+', '%20')
}

private static String toQueryString(Map m){
	return m.collect{ k, v -> "${encodeURIComponent(k)}=${encodeURIComponent(v)}" }.join('&')
}

static String connectionStatus(String message){
	return """<!DOCTYPE html>
<html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>NuHeat connection</title>
<style>body{font-family:sans-serif;text-align:center;padding:10%;color:#444;font-size:1.4em}</style></head>
<body><p>${message}</p></body></html>"""
}

static Integer determineLogLevel(String data){
	switch (data?.toUpperCase()){
	case sTRACE: return 0
	case sDEBUG: return 1
	case sINFO:  return 2
	case sWARN:  return 3
	case sERROR: return 4
	default:     return 2
	}
}

void log(String data, String type){
	if(determineLogLevel(type) < determineLogLevel((String)settings?.logging ?: sINFO)) return
	String ndata="NuHeatConnect -- ${data}".toString()
	switch(type){
	case sTRACE: log.trace ndata; break
	case sDEBUG: log.debug ndata; break
	case sINFO:  log.info ndata; break
	case sWARN:  log.warn ndata; break
	default:     log.error ndata
	}
}
