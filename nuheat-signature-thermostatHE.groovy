/**
 *  NuHeat Signature Thermostat
 *
 * Modified for HE by ERS 11/06/2020
 *  Copyright 2016 ericvitale@gmail.com
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
 *  You can find my SmartThings profile @ https://community.smartthings.com/users/whoismoses/summary
 *
 *  Note: You will need an account which can only be setup on a thermostat.
 *
 *  Thanks to @erobertshaw for providing initial api URLs and initial code.
 *  https://community.smartthings.com/users/erobertshaw/summary
 *
 *  Manufacturer Website: http://www.mynuheat.com
 *  Project Page: http://www.nuheat.com/products/thermostats/signature
 *
 **/

import groovy.time.TimeCategory

static String version(){ return "v0.0.001.202011106" }

metadata{
	definition (name: "NuHeat Signature Thermostat", namespace: "ericvitale", author: "ericvitale@gmail.com"){
		capability "Actuator"
		capability "Temperature Measurement"
		//capability "Thermostat"    // don't want to use this, brings in too many useless commands
		capability "ThermostatCoolingSetpoint" // only want this due to google home on HE
		capability "ThermostatHeatingSetpoint"
		capability "ThermostatMode"
		capability "ThermostatOperatingState"
		capability "ThermostatSetpoint"
		capability "Refresh"
		capability "Sensor"

		command "resume"
		command ("indefiniteHold",
		[
			["name": "Temperature",
			 "description":"Heating setpoint in degrees",
			 "type": "NUMBER"
			]
		] )
		command( "setHeatingSetpointWithHoldTime",
		[
			["name": "Temperature*",
			 "description":"Heating setpoint in degrees",
			 "type": "NUMBER"
			],
			["name": "Date*",
			 "type": "DATE",
			 "description":"Date object with time to end temperature override"
			],
		])

		attribute "supportedThermostatModes", "string"
		attribute "scheduleStatus", "string"
		attribute "home", "string"
		//attribute "power", "number"
	}

	preferences(){
		input "tStatSerialNumber", "text", title: "Thermostat Serial Number", description:"Lookup at mynuheat.com", required:true
		input "theUser", "text", title: "Username", description: "Your Nuheat email address", required:true
		input "thePassword", "password", title: "Password", description: "Your Nuheat password", required:true
		input "showPassword", "bool", title: "Show password in logs", defaultValue: false
		input "defaultHoldTime", "number", title: "Default Hold Time on setting Heat setpoint (hours)", defaultValue: 1
		input "autoRefresh1", "bool", title: "Auto Refresh (5 mins)", defaultValue: true
		//input "tStatName", "text", title: "Name", required: true
		input "defaultHoldTemperature", "number", title: "Default Indefinite Hold Temperature", defaultValue: getMinTemp()
		//input "powerUsage", "number", title: "Power Usage (watts)", defaultValue: 1200

		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
		input "logging", "enum", title: "Log Level", defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR" ]

	}

/*	tiles(scale: 2){
	multiAttributeTile(name:"heading", type: "thermostat", width: 6, height: 4, canChangeIcon: true){
		tileAttribute ("device.temperature", key: "PRIMARY_CONTROL"){
		attributeState("on", label:'${currentValue}°', unit:"F", defaultState: true, icon: "st.Weather.weather2",
			backgroundColors:[
			[value: 60, color: "#153591"],
			[value: 70, color: "#44b621"],
			[value: 80, color: "#f1d801"],
			[value: 90, color: "#d04e00"]
			]
		)
		}

			tileAttribute("device.summary", key: "SECONDARY_CONTROL"){
			attributeState "heat", label:'${currentValue}°', unit:"F", backgroundColor:"#ffffff"
			}
	}

		standardTile("mode", "device.thermostatMode", inactiveLabel: false, decoration: "flat"){
			state "off", label:'${name}', action:"thermostat.setThermostatMode"
			state "heat", label:'${name}', action:"thermostat.setThermostatMode"
		}

		controlTile("heatSliderControl", "device.heatingSetpoint", "slider", height: 1, width: 6, inactiveLabel: false, range:"(62..100)"){
			state "setHeatingSetpoint", action:"thermostat.setHeatingSetpoint", backgroundColor:"#e86d13"
		}

	valueTile("heatingSetpoint", "device.heatingSetpoint", height: 1, width: 2, inactiveLabel: false, decoration: "flat"){
			state "heat", label:'Currently: ${currentValue}°', unit:"F", backgroundColor:"#ffffff"
		}

	valueTile("currentMode", "device.currentMode", height: 1, width: 2, inactiveLabel: false, decoration: "flat"){
			state "default", label:'${currentValue}', backgroundColor:"#ffffff"
		}

	valueTile("power", "device.power", height: 1, width: 2, inactiveLabel: false, decoration: "flat"){
			state "default", label:'${currentValue} W', backgroundColor:"#ffffff"
		}

		standardTile("refresh", "device.temperature", height: 2, width: 2, inactiveLabel: false, decoration: "flat"){
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}

	standardTile("setTemp", "device.setTemp", inactiveLabel: false, decoration: "flat"){
			state "default", action:"setTemp", label:"Set"
		}

	standardTile("resume", "device.resume", height: 2, width: 2, inactiveLabel: false, decoration: "flat"){
			state "resume", label:'Resume', action:"resume", icon:"st.Office.office7"
		}

	standardTile("hold", "device.hold", height: 2, width: 2, inactiveLabel: false, decoration: "flat"){
			state "hold", label:'Hold', action:"hold", icon:"st.Office.office13"
		}

	valueTile("lastActivityTitle", "device.lastActivity", height: 1, width: 2, inactiveLabel: false, decoration: "flat"){
			state "default", label:'Last Activity', backgroundColor:"#ffffff"
		}

	valueTile("lastActivity", "device.lastActivity", height: 1, width: 4, inactiveLabel: false, decoration: "flat"){
			state "default", label:'${currentValue}', backgroundColor:"#ffffff"
		}

	main (["heading", "power"])
	details(["heading", "heatSliderControl", "hold", "resume", "refresh", "heatingSetpoint", "currentMode", "power", "lastActivityTitle", "lastActivity"])
	}
*/
}

//Fix this if using Celsius
static Integer getMinTemp(){
	//return 5
	return 41
}

static String logPrefix(){
	return "NuHeatSig"
}

static Integer determineLogLevel(String data){
	switch (data?.toUpperCase()){
	case "TRACE":
		return 0
		break
	case "DEBUG":
		return 1
		break
	case "INFO":
		return 2
		break
	case "WARN":
		return 3
		break
	case "ERROR":
		return 4
		break
	default:
		return 1
	}
}

void log(String data, String type){
	if(!logEnable) return

	String ndata = "${logPrefix()} -- ${device.label} -- ${data ?: ''}".toString()

	if(determineLogLevel(type) >= determineLogLevel(settings?.logging ?: "INFO")){
		switch (type?.toUpperCase()){
		case "TRACE":
			log.trace "${ndata}"
			break
		case "DEBUG":
			log.debug "${ndata}"
			break
		case "INFO":
			log.info "${ndata}"
			break
		case "WARN":
			log.warn "${ndata}"
			break
		case "ERROR":
			log.error "${ndata}"
			break
		case "OFF":
			break
		default:
			log.error "NuHeatSig -- ${device.label} -- Invalid Log Setting"
		}
	}
}

void installed(){
	log.info "Initializing..."
	userAuthenticated(false)

// set some dummy values, for google integration
	Integer myVal=41
	Integer myTemp=80
	if(getTemperatureScale()=='C'){
		myTemp=28
		myVal=5
	}
	sendEvent(name:"coolingSetpoint", value:myTemp, unit: getTemperatureScale())
	if(settings.defaultHoldTemperature==null){
		device.updateSetting("defaultHoldTemperature",[value:myVal,type:"number"])
	}
	List supportedThermostatModes = ["heat"]  // HE capabilities (no "emerency heat")
	sendEvent(name:"supportedThermostatModes", value: supportedThermostatModes, displayed: false )
	sendEvent(name:"thermostatMode", value: 'heat')
}

void updated(){
	log.info "Updating..."
	if(tStatSerialNumber && theUser && thePassword){
		initialize()

		log.debug "debug logging is: ${logEnable == true}"
		log.debug "description logging is: ${txtEnable == true}"
		if(logEnable) runIn(1800,logsOff)

		state.remove('isAutoRefreshEnabled')
		state.remove('defaultTemperature')
		state.remove('serialNumber')
		state.remove('summary')
	}else{
		unschedule()
		log.warn "MISSING Thermostat serial number, or username or password"
	}
}

void initialize(){
	log("Initializing device handler for NuHeat Signiture Thermostat", "INFO")
	log("DH Version = ${version()}.".toString(), "INFO")
	//log("Thermostat Name = ${tStatName}.".toString(), "INFO")
	log("Thermostat Serial Number = ${tStatSerialNumber}.".toString(), "INFO")
	log("Username = ${theUser}.".toString(), "INFO")
	log("Show pasword in log? = ${showPassword}.".toString(), "INFO")
	if(showPassword){
		log("Password = ${thePassword}".toString(), "INFO")
	}
	log("Logging Level = ${logging}.".toString(), "INFO")
	//log("Power Usage KWH = ${powerUsage}.".toString(), "INFO")
	log("Default Hold Time = ${defaultHoldTime}.".toString(), "INFO")
	log("Default Hold Temperature = ${defaultHoldTemperature}.".toString(), "INFO")
	log("Auto Refresh = ${autoRefresh1}".toString(), "INFO")

	log("Unscheduling jobs...", "INFO")
	unschedule()

	if(autoRefresh1){
		log("Scheduling auto refresh every 5 minutes.", "INFO")
		runEvery5Minutes(updateStatus)
	}else{
		log("Auto refresh is disabled.", "INFO")
	}
	getStatus()
}

void logsOff(){
	log.debug "text logging disabled..."
	log.debug "debug logging disabled..."
	device.updateSetting("logEnable",[value:"false",type:"bool"])
	device.updateSetting("txtEnable",[value:"false",type:"bool"])
}

void updateStatus(){
	getStatus()
}

def parse(String description){
	log("Parse() description = ${description}.".toString(), "DEBUG")
}

void refresh(){
	log("Device is being refreshed.", "INFO")
	Long t=now()
	if(!state.updatedLastRanAt || t >= (Long)state.updatedLastRanAt + 60000L) getStatus()
}

void poll(){
	log("Device is being polled.", "INFO")
	refresh()
}

/* Sets the heating setpoint to the specificed temperature for the default time stored in setting */
void setHeatingSetpoint(degrees){
	/* Calculate Hold Time */
	Date date = new Date()

	Integer d=defaultHoldTime ? defaultHoldTime.toInteger(): 1
	use( TimeCategory ){
		date = date + d.hours
	}
	setHeatingSetpointWithHoldTime(degrees, date)

}

void setHeatingSetpointWithHoldTime(degrees, Date date){
	log("Setting HeatingSetpoint temporary override to ${degrees} until ${date}".toString(), "INFO")

	if(date == null || date.getTime() < now()){
		log.error "Bad Date $date"
		return
	}
	String sdate = date.format("yyyy-MM-dd'T'HH:mm:ssZ", TimeZone.getTimeZone('GMT'))

	Map values = ["SetPointTemp": temperatureToSetpoint(degrees), "ScheduleMode": "2", "HoldSetPointDateTime": sdate]
	setThermostat(values)
	sendEvent(name: "heatingSetpoint", value: degrees, unit: getTemperatureScale())
	sendEvent(name:"thermostatSetpoint", value: degrees, "unit": getTemperatureScale())
	scheduleGetStatus()
}

void setCoolingSetpoint(degrees){
	log.error "The method setCoolingSetpoint(...) is not supported"
}

void resume(){
	log("Resuming schedule", "INFO")
	Map values = ["ScheduleMode": "1"]
	setThermostat(values)
	scheduleGetStatus()
}

void indefiniteHold(degrees){
	if(!degrees) degrees=defaultHoldTemperature ?: state.MinTemp
	log("Setting indefinite Hold at temperature of ${degrees}", "INFO")
	Map values = ["SetPointTemp": temperatureToSetpoint(degrees), "ScheduleMode": "3"]
	setThermostat(values)
	sendEvent(name: "heatingSetpoint", value: degrees, unit: getTemperatureScale())
	sendEvent(name:"thermostatSetpoint", value: degrees, "unit": getTemperatureScale())
	scheduleGetStatus()
}

void setThermostatMode(String value){
	if(value =='heat'){ heat(); return }
	if(value =='off'){ off(); return }
	log.error "The method setThermostatMode($value) is not supported"
}

void off(){
	log("off...", "INFO")
	indefiniteHold(null)
}

void cool(){
	log.error "The method cool() is not supported"
}

void auto(){
	log.error "The method auto() is not supported"
}

void emergencyHeat(){
	log.error "The method emergencyHeat() is not supported"
}

void heat(){
	log("Heating...", "INFO")
}

//--------------------------------------------------------

void setSessionID(String value){
	state.theSessionID = value
}

String getSessionID(){
	if(state.theSessionID == null){
		return "-1"
	}else{
		return (String)state.theSessionID
	}
}

Integer temperatureToSetpoint(value){
	log("temperatureToSetpoint(${value})", "DEBUG")

	Double t=value.toDouble()
	if(getTemperatureScale()=='F') t=(t - 32.0D) / 1.8D // * 5.0D/9.0D
	t=Math.round(t*200.0D)/2.0D

	log("Setpoint from temperature ${value} is ${t}.", "DEBUG")
	return t.toInteger()
}

def setpointToTemperature(Integer value){
	Double t=Math.round(value/100.0D * 2.0D) / 2.0D
	if(getTemperatureScale()=='F') t=Math.round(t*1.8D + 32.0D)

	log("Temperature from Setpoint ${value} is ${t}.", "DEBUG")

	if(getTemperatureScale()=='F') return t.toInteger()
	return t
}

//--------------------------------------------------------

void scheduleGetStatus(){
	log("Scheduling a status update in 30 seconds...", "INFO")
	runIn(30, getStatus)
}

void getStatus(){
	String sNum=tStatSerialNumber
	log("Updating the status of the ${sNum} thermostat.", "INFO")

	Long t=now()
	state.updatedLastRanAt = t

	if(!isUserAuthenticated()){
		if(!authenticateUser()){
			//log("Failed to authenticate user.", "ERROR")
			return
		}
	}

	def params = [
		uri: "https://www.mynuheat.com/api/thermostat?sessionid=${getSessionID()}&serialnumber=${sNum}",
		body: [],
		timeout: 20
	]

	try{

		asynchttpGet('myHandler', params, [:])

	}catch (groovyx.net.http.HttpResponseException e){

		log("Not authenticated, authenticating.", "ERROR")
		userAuthenticated(false)

		if(e.getMessage() == "Unauthorized"){
			authenticateUser()
		}
	}
}

public void myHandler(resp, Map data){
	def t0=resp.getHeaders()
	String t1=t0!=null && (String)t0."Content-Type" ? (String)t0."Content-Type" : (String)null

	t0.each{
		log("header ${it.key} : ${it.value}", "TRACE")
	}

	log("response contentType: ${t1}", "TRACE")
	if(resp.status>=200 && resp.status<300 && resp.data){
		def myD=resp.getJson()
		log("response data: ${myD}", "TRACE")
		log("WPerSquareUnit = ${myD.WPerSquareUnit}", "DEBUG")
		log("FloorArea = ${myD.FloorArea}", "DEBUG")
		log("Temperature = ${myD.Temperature}", "DEBUG")
		log("Heating = ${myD.Heating}", "DEBUG")
		log("Setpoint = ${myD.SetPointTemp}", "DEBUG")

		String temperatureScale = getTemperatureScale()

		def theTemp = setpointToTemperature(myD.Temperature)
		log("Converted Temperature: ${theTemp}.".toString(), "DEBUG")
		sendEvent(name:"temperature", value: theTemp, unit: temperatureScale)

		//def power = 0
		def setPoint = setpointToTemperature(myD.SetPointTemp)
		sendEvent(name:"heatingSetpoint", value: setPoint, "unit": temperatureScale)
		sendEvent(name:"thermostatSetpoint", value: setPoint, "unit": temperatureScale)

		if(myD.Heating){
			sendEvent(name:"thermostatOperatingState", value: "heating")
			//power = getPowerUsage()
			//sendEvent("name":"power", "value": power)
		}else{
			sendEvent(name:"thermostatOperatingState", value: "idle")
			//power = 0
			//sendEvent("name":"power", "value": power)
		}
		//log("Calculated power usage: ${power} watts.".toString(), "DEBUG")

		if(myD.GroupAwayMode) sendEvent(name:"home", value: "away")
		else sendEvent(name:"home", value: "home")

		switch(myD.ScheduleMode){
		case 1:
			sendEvent(name:"scheduleStatus", value: "running")
			break
		case 2:
			sendEvent(name:"scheduleStatus", value: "temporary hold")
			break
		case 3:
			sendEvent(name:"scheduleStatus", value: "indefinite hold")
			break
		}

		if(myD.MaxTemp) state.MaxTemp=setpointToTemperature(myD.MaxTemp)
		if(myD.MinTemp) state.MinTemp=setpointToTemperature(myD.MinTemp)

		updateDeviceLastActivity(new Date())
	}else{
		if(resp.status == 401){
			userAuthenticated(false)
			log("Not authenticated, attempting authentication.", "ERROR")
			if(authenticateUser()){
				getStatus()
				return
			}else log.error "Authentication failed"
		}else log.error "Bad resp code ${resp.status}"
	}
}

void setThermostat(Map value_map){
	log("Attempting to set values ${value_map}.".toString(), "INFO")
	String sNum=tStatSerialNumber

	if(!isUserAuthenticated()){
		if(!authenticateUser()){
			//log("Failed to authenticate user.", "ERROR")
			return
		}
	}
	def params = [
		uri: "https://www.mynuheat.com/api/thermostat?sessionid=${getSessionID()}&serialnumber=${sNum}",
		body: value_map,
		timeout: 20
	]

	try{
	httpPost(params) {resp ->
		log("Response: ${resp.status}", "TRACE")

		if(!(resp.status>=200 && resp.status<300)) log.error "Response error ${resp.status}"

		//log("Response: ${resp}.", "DEBUG")
		resp.headers.each{
			log("header ${it.name} : ${it.value}", "DEBUG")
		}

		log("response contentType: ${resp.contentType}", "TRACE")
		log("response data: ${resp.data}", "TRACE")
	}
	}catch (groovyx.net.http.HttpResponseException e){

		log("User is not authenticated, authenticating.", "ERROR")
		userAuthenticated(false)

		if(e.getMessage() == "Unauthorized"){
			if(authenticateUser()){
				setThermostat(value_map)
			}
		}
	}
}

//--------------------------------------------------------

Boolean authenticateUser(){
	log("Attempting to authenticate user "+theUser, "INFO")
	if(!(theUser && thePassword)){
		userAuthenticated(false)
		log.warn "No username or no password"
		return false
	}
	setSessionID("")
	Map params = [
		uri: 'https://www.mynuheat.com/api/authenticate/user',
		body: ["Email": theUser, "password": thePassword, "application": "0"],
		timeout: 20
	]

	httpPost(params) {resp ->
		log("Response: ${resp.status}", "TRACE")
		resp.headers.each{
			log("header ${it.name} : ${it.value}", "TRACE")
		}
		log("response contentType: ${resp.contentType}", "TRACE")
		log("response data: ${resp.data}", "TRACE")
		log("SessionID: ${resp.data["SessionId"]}", "TRACE")

		setSessionID((String)resp.data.SessionId)
	}

	if(getSessionID() != ""){
		userAuthenticated(true)
		log("authentication successful", "INFO")
		return true
	}else{
		userAuthenticated(false)
		log("Failed to authenticate.", "ERROR")
		return false
	}
}

Boolean isUserAuthenticated(){
	if(state.authenticatedUser == null){
		return false
	}else{
		return (Boolean)state.authenticatedUser
	}
}

void userAuthenticated(Boolean value){
	state.authenticatedUser = value
}

void updateDeviceLastActivity(lastActivity){
	def finalString = lastActivity?.format('MM/d/yyyy hh:mm:ss a',location.timeZone)
	state.lastActivity=finalString
}
