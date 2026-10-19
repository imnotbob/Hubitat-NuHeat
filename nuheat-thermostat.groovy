/**
 *  NuHeat Signature Thermostat
 *  Modified for HE by ERS 11/06/2020
 *  Converted to NuHeat OAuth2 API (child of NuHeat Connect app) 10/13/2026
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
 *  Note: This driver is created and updated by the NuHeat Connect app, which handles OAuth2 with NuHeat.
 *
 *  Thanks to @erobertshaw for providing initial api URLs and initial code.
 *  https://community.smartthings.com/users/erobertshaw/summary
 *
 *  Manufacturer Website: http://www.mynuheat.com
 *  API: https://api.mynuheat.com
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

import groovy.time.TimeCategory
import groovy.transform.Field

static String version(){ return "v1.0.3.20261013" }

metadata{
	definition (name: "NuHeat Signature Thermostat1", namespace: "ericvitale", author: "ericvitale@gmail.com"){
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
		command ("setAwayMode",
		[
			["name": "Mode*",
			 "description":"Away mode applies to every thermostat in this thermostat's NuHeat group",
			 "type": "ENUM",
			 "constraints": ["away", "home"]
			]
		] )
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
		attribute "online", "string"
		attribute "holdUntil", "string"
		attribute "apiStatus", "string"
	}

	preferences(){
		input "defaultHoldTime", "number", title: "Default Hold Time on setting Heat setpoint (hours)", defaultValue: 1, range: "1..23"
		input "defaultHoldTemperature", "number", title: "Default Indefinite Hold Temperature", defaultValue: getMinTemp()

		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
		input "logging", "enum", title: "Log Level", defaultValue: "INFO", options: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR" ]
	}
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
	log.debug "debug logging is: ${logEnable == true}"
	log.debug "description logging is: ${txtEnable == true}"
	if(logEnable) runIn(1800,logsOff)

	// remove state from the pre-OAuth driver
	['theSessionID', 'authenticatedUser', 'updatedLastRanAt', 'isAutoRefreshEnabled', 'defaultTemperature', 'serialNumber', 'summary'].each{ state.remove(it) }
	refresh()
}

void logsOff(){
	log.debug "text logging disabled..."
	log.debug "debug logging disabled..."
	device.updateSetting("logEnable",[value:"false",type:"bool"])
	device.updateSetting("txtEnable",[value:"false",type:"bool"])
}

def parse(String description){
	log((String)"Parse() description = ${description}.".toString(), sDEBUG)
}

void refresh(){
	log("Device is being refreshed.", sINFO)
	parent?.componentRefresh(device)
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
	// API wants ISO-8601 UTC, and only allows holds within the next 23 hours
	String sdate = date.format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))

	LinkedHashMap<String,Object> values = [setPointTemp: temperatureToSetpoint(degrees), scheduleMode: 2, holdSetPointDateTime: sdate]
	if(!setThermostat(values)) return
	sendEvent(name: "heatingSetpoint", value: degrees, unit: getTemperatureScale())
	sendEvent(name:"thermostatSetpoint", value: degrees, "unit": getTemperatureScale())
	sendEvent(name:"scheduleStatus", value: "temporary hold")
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
	LinkedHashMap<String,Object> values = [scheduleMode: 1]
	if(!setThermostat(values)) return
	sendEvent(name:"scheduleStatus", value: "running")
}

void indefiniteHold(degrees){
	if(!degrees) degrees=defaultHoldTemperature ?: state.MinTemp
	log((String)"Setting indefinite Hold at temperature of ${degrees}".toString(), sINFO)
	LinkedHashMap<String,Object> values = [setPointTemp: temperatureToSetpoint(degrees), scheduleMode: 3]
	if(!setThermostat(values)) return
	sendEvent(name: "heatingSetpoint", value: degrees, unit: getTemperatureScale())
	sendEvent(name:"thermostatSetpoint", value: degrees, "unit": getTemperatureScale())
	sendEvent(name:"scheduleStatus", value: "indefinite hold")
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

// NuHeat setpoints are hundredths of a degree C, in 0.5 degree steps
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

// returns true if NuHeat accepted the change
Boolean setThermostat(LinkedHashMap<String,Object> value_map){
	log((String)"Attempting to set values ${value_map}.".toString(), sINFO)
	if(!parent){
		log.error "This device must be created by the NuHeat Connect app"
		return false
	}
	Boolean ok=(Boolean)parent.componentSetThermostat(device, value_map)
	if(!ok) log.error "NuHeat did not accept ${value_map}"
	return ok
}

void setAwayMode(String mode){
	if(!(mode in ["away", "home"])){
		log.error "Bad away mode $mode"
		return
	}
	log((String)"Setting away mode to ${mode}".toString(), sINFO)
	if(!parent){
		log.error "This device must be created by the NuHeat Connect app"
		return
	}
	if(!parent.componentSetAwayMode(device, mode == "away")){
		log.error "NuHeat did not accept away mode ${mode}"
		return
	}
	sendEvent(name:"home", value: mode)
}

// called by the NuHeat Connect app when the cloud connection status changes
void setApiStatus(String status){
	sendEvent(name:"apiStatus", value: status)
}

// called by the NuHeat Connect app with a ThermostatModel from GET /api/v1/Thermostat (plus awayMode from its group)
void updateFromApi(Map myD){
	String temperatureScale = getTemperatureScale()
	log((String)"response data: ${myD}".toString(), sTRACE)

	sendEvent(name:"online", value: myD.online ? "online" : "offline")

	if(myD.currentTemperature != null){
		def theTemp = setpointToTemperature(myD.currentTemperature as Integer)
		sendEvent(name:"temperature", value: theTemp, unit: temperatureScale)
	}

	if(myD.setPointTemp != null){
		def setPoint = setpointToTemperature(myD.setPointTemp as Integer)
		sendEvent(name:"heatingSetpoint", value: setPoint, "unit": temperatureScale)
		sendEvent(name:"thermostatSetpoint", value: setPoint, "unit": temperatureScale)
	}

	sendEvent(name:"thermostatOperatingState", value: myD.isHeating ? "heating" : "idle")

	if(myD.awayMode != null) sendEvent(name:"home", value: myD.awayMode ? "away" : "home")

	switch(myD.scheduleMode as Integer){
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
	sendEvent(name:"holdUntil", value: (myD.scheduleMode as Integer) == 2 && myD.holdSetPointDateTime ? (String)myD.holdSetPointDateTime : "none")

	if(myD.error) log((String)"Thermostat error: ${myD.error}".toString(), sWARN)

	updateDeviceLastActivity(new Date())
}

void updateDeviceLastActivity(Date lastActivity){
	String finalString = lastActivity?.format('MM/d/yyyy hh:mm:ss a',location.timeZone)
	state.lastActivity=finalString
}
