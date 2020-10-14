/**
 *  NuHeat Signature Thermostat
 *  Modified for HE by ERS 10/14/2020
 *
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
import groovy.transform.Field

public static String version(){ return "v0.0.001.20201014" }

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
		command( "setHeatingSetpointWithHoldHours",
		[
			["name": "Temperature*",
			 "description":"Heating setpoint in degrees",
			 "type": "NUMBER"
			],
			["name": "Hours*",
			 "type": "NUMBER",
			 "range": "1..23",
			 "description":"Hours until end of temperature override"
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
		input "defaultHoldTime", "number", title: "Default Hold Time on setting Heat setpoint (hours)", defaultValue: 1, range: "1..23"
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

@Field static final String sTRACE='TRACE'
@Field static final String sDEBUG='DEBUG'
@Field static final String sINFO='INFO'
@Field static final String sWARN='WARN'
@Field static final String sERROR='ERROR'

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
	case sTRACE:
		return 0
		break
	case sDEBUG:
		return 1
		break
	case sINFO:
		return 2
		break
	case sWARN:
		return 3
		break
	case sERROR:
		return 4
		break
	default:
		return 1
	}
}

void log(String data, String type){
	if(!logEnable) return

	String ndata = "${logPrefix()} -- ${device.label} -- ${data ?: ''}".toString()

	if(determineLogLevel(type) >= determineLogLevel(settings?.logging ?: sINFO)){
		switch (type?.toUpperCase()){
		case sTRACE:
			log.trace ndata
			break
		case sDEBUG:
			log.debug ndata
			break
		case sINFO:
			log.info ndata
			break
		case sWARN:
			log.warn ndata
			break
		case sERROR:
			log.error ndata
			break
		case "OFF":
			break
		default:
			log.error "NuHeatSig -- ${device.label} -- Invalid Log Setting".toString()
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
	List<String> supportedThermostatModes = ["heat"]  // HE capabilities (no "emerency heat")
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
	if(logEnable){
		log("Initializing device handler for NuHeat Signiture Thermostat", sINFO)
		log((String)"DH Version = ${version()}.".toString(), sINFO)
		//log("Thermostat Name = ${tStatName}.".toString(), sINFO)
		log((String)"Thermostat Serial Number = ${tStatSerialNumber}.".toString(), sINFO)
		log((String)"Username = ${theUser}.".toString(), sINFO)
		log((String)"Show pasword in log? = ${showPassword}.".toString(), sINFO)
		if(showPassword){
			log((String)"Password = ${thePassword}".toString(), sINFO)
		}
		log((String)"Logging Level = ${logging}.".toString(), sINFO)
		//log((String)"Power Usage KWH = ${powerUsage}.".toString(), sINFO)
		log((String)"Default Hold Time = ${defaultHoldTime}.".toString(), sINFO)
		log((String)"Default Hold Temperature = ${defaultHoldTemperature}.".toString(), sINFO)
		log((String)"Auto Refresh = ${autoRefresh1}".toString(), sINFO)

		log("Unscheduling jobs...", sINFO)
	}
	unschedule()

	if(autoRefresh1){
		log("Scheduling auto refresh every 5 minutes.", sINFO)
		runEvery5Minutes(updateStatus)
	}else{
		log("Auto refresh is disabled.", sINFO)
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
	log((String)"Parse() description = ${description}.".toString(), sDEBUG)
}

void refresh(){
	log("Device is being refreshed.", sINFO)
	Long t=now()
	if(!state.updatedLastRanAt || t >= (Long)state.updatedLastRanAt + 60000L) getStatus()
}

void poll(){
	log("Device is being polled.", sINFO)
	refresh()
}

/* Sets the heating setpoint to the specificed temperature for the default time stored in setting */
void setHeatingSetpoint(degrees){
	Integer d=defaultHoldTime ? defaultHoldTime.toInteger(): 1
	setHeatingSetpointWithHoldHours(degrees, d)
}

void setHeatingSetpointWithHoldTime(degrees, Date date){
	log((String)"Setting HeatingSetpoint temporary override to ${degrees} until ${date}".toString(), sINFO)

	if(date == null || date.getTime() < now()){
		log.error "Bad Date $date"
		return
	}
	String sdate = date.format("yyyy-MM-dd'T'HH:mm:ssZ", TimeZone.getTimeZone('GMT'))

	LinkedHashMap<String,Object> values = ["SetPointTemp": temperatureToSetpoint(degrees), "ScheduleMode": "2", "HoldSetPointDateTime": sdate]
	setThermostat(values)
	sendEvent(name: "heatingSetpoint", value: degrees, unit: getTemperatureScale())
	sendEvent(name:"thermostatSetpoint", value: degrees, "unit": getTemperatureScale())
	sendEvent(name:"scheduleStatus", value: "temporary hold")
	scheduleGetStatus()
}

void setHeatingSetpointWithHoldHours(degrees, hours){
	log((String)"Setting HeatingSetpoint temporary override to ${degrees} for ${hours} hours".toString(), sINFO)

	if(hours == null || hours < 1 || hours > 23){
		log.error "Bad Hours $hours"
		return
	}
	Date date = new Date()
	Integer d=hours.toInteger()
	use( TimeCategory ){
		date = date + d.hours
	}
	setHeatingSetpointWithHoldTime(degrees, date)
}

void setCoolingSetpoint(degrees){
	log.error "The method setCoolingSetpoint(...) is not supported"
}

void resume(){
	log("Resuming schedule", sINFO)
	LinkedHashMap<String,Object> values = ["ScheduleMode": "1"]
	setThermostat(values)
	sendEvent(name:"scheduleStatus", value: "running")
	scheduleGetStatus()
}

void indefiniteHold(degrees){
	if(!degrees) degrees=defaultHoldTemperature ?: state.MinTemp
	log((String)"Setting indefinite Hold at temperature of ${degrees}".toString(), sINFO)
	LinkedHashMap<String,Object> values = ["SetPointTemp": temperatureToSetpoint(degrees), "ScheduleMode": "3"]
	setThermostat(values)
	sendEvent(name: "heatingSetpoint", value: degrees, unit: getTemperatureScale())
	sendEvent(name:"thermostatSetpoint", value: degrees, "unit": getTemperatureScale())
	sendEvent(name:"scheduleStatus", value: "indefinite hold")
	scheduleGetStatus()
}

void setThermostatMode(String value){
	if(value =='heat'){ heat(); return }
	if(value =='off'){ off(); return }
	log.error "The method setThermostatMode($value) is not supported"
}

void off(){
	log("off...", sINFO)
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
	log("Heating...", sINFO)
}

//--------------------------------------------------------

void setSessionID(String value){
	state.theSessionID = value
}

String getSessionID(){
	String a=(String)state.theSessionID
	if(a == (String)null){
		return "-1"
	}else{
		return a
	}
}

Integer temperatureToSetpoint(value){
	Double t=value.toDouble()
	if(getTemperatureScale()=='F') t=(t - 32.0D) / 1.8D // * 5.0D/9.0D
	t=Math.round(t*200.0D)/2.0D

	log((String)"Setpoint from temperature ${value} is ${t}.".toString(), sDEBUG)
	return t.toInteger()
}

def setpointToTemperature(Integer value){
	Double t=Math.round(value/100.0D * 2.0D) / 2.0D
	if(getTemperatureScale()=='F') t=Math.round(t*1.8D + 32.0D)

	log((String)"Temperature from Setpoint ${value} is ${t}.".toString(), sDEBUG)

	if(getTemperatureScale()=='F') return t.toInteger()
	return t
}

//--------------------------------------------------------

void scheduleGetStatus(){
	log("Scheduling a status update in 30 seconds...", sINFO)
	runIn(30, getStatus)
}

void getStatus(){
	String sNum=tStatSerialNumber
	log((String)"Updating the status of the ${sNum} thermostat.".toString(), sINFO)

	Long t=now()
	state.updatedLastRanAt = t

	if(!isUserAuthenticated()){
		if(!authenticateUser()){
			//log("Failed to authenticate user.", sERROR)
			return
		}
	}

	LinkedHashMap<String,Object> params = [
		uri: "https://www.mynuheat.com/api/thermostat?sessionid=${getSessionID()}&serialnumber=${sNum}",
		body: []
	]

	try{

		asynchttpGet('myHandler', params, [:])

	}catch (groovyx.net.http.HttpResponseException e){

		log("Not authenticated, authenticating.", sERROR)
		userAuthenticated(false)

		if(e.getMessage() == "Unauthorized"){
			Boolean a=authenticateUser()
		}
	}
}

public void myHandler(resp, Map data){
	def t0=resp.getHeaders()
	String t1=t0!=null && (String)t0."Content-Type" ? (String)t0."Content-Type" : (String)null

	t0.each{
		log((String)"header ${it.key} : ${it.value}".toString(), sTRACE)
	}

	log((String)"response contentType: ${t1}".toString(), sTRACE)
	if(resp.status>=200 && resp.status<300 && resp.data){
		def myD=resp.getJson()
		String temperatureScale = getTemperatureScale()
		def theTemp = setpointToTemperature(myD.Temperature)

		if(logEnable){
			log((String)"response data: ${myD}".toString(), sTRACE)
			log((String)"WPerSquareUnit = ${myD.WPerSquareUnit}".toString(), sDEBUG)
			log((String)"FloorArea = ${myD.FloorArea}".toString(), sDEBUG)
			log((String)"Temperature = ${myD.Temperature}".toString(), sDEBUG)
			log((String)"Heating = ${myD.Heating}".toString(), sDEBUG)
			log((String)"Setpoint = ${myD.SetPointTemp}".toString(), sDEBUG)
			log((String)"Converted Temperature: ${theTemp}.".toString(), sDEBUG)
		}
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
		//log("Calculated power usage: ${power} watts.".toString(), sDEBUG)

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
			log("Not authenticated, attempting authentication.", sERROR)
			if(authenticateUser()){
				getStatus()
				return
			}else log.error "Authentication failed"
		}else log.error "Bad resp code ${resp.status}"
	}
}

void setThermostat(LinkedHashMap<String,Object> value_map){
	log((String)"Attempting to set values ${value_map}.".toString(), sINFO)
	String sNum=tStatSerialNumber

	if(!isUserAuthenticated()){
		if(!authenticateUser()){
			//log("Failed to authenticate user.", sERROR)
			return
		}
	}
	LinkedHashMap<String,Object> params = [
		uri: "https://www.mynuheat.com/api/thermostat?sessionid=${getSessionID()}&serialnumber=${sNum}",
		body: value_map
	]

	try{
	httpPost(params) {resp ->
		log((String)"Response: ${resp.status}".toString(), sTRACE)

		if(!(resp.status>=200 && resp.status<300)) log.error "Response error ${resp.status}"

		//log("Response: ${resp}.", sDEBUG)
		if(logEnable){
			resp.headers.each{
				log((String)"header ${it.name} : ${it.value}".toString(), sDEBUG)
			}

			log((String)"response contentType: ${resp.contentType}".toString(), sTRACE)
			log((String)"response data: ${resp.data}".toString(), sTRACE)
		}
	}
	}catch (groovyx.net.http.HttpResponseException e){

		log("User is not authenticated, authenticating.", sERROR)
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
	log('Attempting to authenticate user '+(String)theUser, sINFO)
	if(!(theUser && thePassword)){
		userAuthenticated(false)
		log.warn "No username or no password"
		return false
	}
	setSessionID("")
	LinkedHashMap<String,Object> params = [
		uri: 'https://www.mynuheat.com/api/authenticate/user',
		body: ["Email": theUser, "password": thePassword, "application": "0"]
	]

	httpPost(params) {resp ->
		if(logEnable){
			log((String)"Response: ${resp.status}".toString(), sTRACE)
			resp.headers.each{
				log((String)"header ${it.name} : ${it.value}".toString(), sTRACE)
			}
			log((String)"response contentType: ${resp.contentType}".toString(), sTRACE)
			log((String)"response data: ${resp.data}".toString(), sTRACE)
			log((String)"SessionID: ${resp.data["SessionId"]}".toString(), sTRACE)
		}

		setSessionID((String)resp.data.SessionId)
	}

	if(getSessionID() != ""){
		userAuthenticated(true)
		log("authentication successful", sINFO)
		return true
	}else{
		userAuthenticated(false)
		log("Failed to authenticate.", sERROR)
		return false
	}
}

Boolean isUserAuthenticated(){
	Boolean a=state.authenticatedUser
	if(a == null){
		return false
	}else{
		return a
	}
}

void userAuthenticated(Boolean value){
	state.authenticatedUser = value
}

void updateDeviceLastActivity(Date lastActivity){
	String finalString = lastActivity?.format('MM/d/yyyy hh:mm:ss a',location.timeZone)
	state.lastActivity=finalString
}
