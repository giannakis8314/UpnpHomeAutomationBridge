/**
 *  UPnP Eq3 Bridge
 *
 *  Copyright 2015 Paul Hampson
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "UPnP EQ3 Bridge",
    namespace: "cyclingengineer",
    author: "Paul Hampson",
    description: "This smart app interfaces with the UPnP Home Automation bridge software to support EQ-3 MAX! Heating Control System",
    category: "SmartThings Labs",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	page(name:"setupPage1", title:"UPnP EQ-3 MAX! Bridge Setup", content:"setupPage1", refreshTimeout:5, nextPage:"setupPage2")
    page(name:"setupPage2", title:"UPnP EQ-3 MAX! Bridge Setup", content:"setupPage2", refreshTimeout:5)
}

// PAGES
def setupPage1()
{
	log.trace "setupPage1"
	if(canInstallLabs())
	{
		int upnpHabRefreshCount = !state.upnpHabRefreshCount ? 0 : state.upnpHabRefreshCount as int
		state.upnpHabRefreshCount = upnpHabRefreshCount + 1
		def refreshInterval = 3

		def options = eq3BridgesDiscovered() ?: []

		def numFound = options.size() ?: 0

		if(!state.subscribe) {
			log.trace "subscribe to location"
			subscribe(location, null, locationHandler, [filterEvents:false])
			state.subscribe = true
		}

		//sonos discovery request every 5 //25 seconds
		if((upnpHabRefreshCount % 8) == 0) {
			discoverUpnpHAB()
		}

		//setup.xml request every 3 seconds except on discoveries
		if(((upnpHabRefreshCount % 1) == 0) && ((upnpHabRefreshCount % 8) != 0)) {
			verifyDiscoveredHvacDevices()
		}

		return dynamicPage(name:"setupPage1", title:"Discovery Started!", nextPage:"setupPage2", refreshInterval:refreshInterval, install:false, uninstall: true) {
			section("Please wait while we discover your EQ3 Systems. Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered.") {
				input "selectedEq3Systems", "enum", required:false, title:"Select EQ3 System (${numFound} found)", multiple:true, options:options
			}
		}
	}
	else
	{
		def upgradeNeeded = """To use SmartThings Labs, your Hub should be completely up to date.

To update your Hub, access Location Settings in the Main Menu (tap the gear next to your location name), select your Hub, and choose "Update Hub"."""

		return dynamicPage(name:"setupPage1", title:"Upgrade needed!", nextPage:"", install:false, uninstall: true) {
			section("Upgrade") {
				paragraph "$upgradeNeeded"
			}
		}
	}
}

def setupPage2() {
	log.trace "setupPage2"
	def options = eq3GetSelectedSystemDiscoveredRoomList( ) ?: []
	def numFound = options.size() ?: 0

	return dynamicPage(name:"setupPage2", title:"Select Rooms", nextPage:"", refreshInterval:refreshInterval, install:true, uninstall: true) {
			section("Please select the rooms you wish to control") {
				input "selectedEq3Rooms", "enum", required:false, title:"Select Rooms (${numFound} found)", multiple:true, options:options
			}
    }
}


private discoverUpnpHAB() {
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:HVAC_System:1", physicalgraph.device.Protocol.LAN))
}

private verifyDiscoveredHvacDevices() {
	log.trace "verifyDiscoveredHvacDevices"
	def devices = getEq3BridgeSystemList().findAll { it?.value?.verified != true }

	if(devices) {
		log.warn "UNVERIFIED DEVICES!: $devices"
	}

	devices.each {
		verifyEq3Bridge((it?.value?.ip + ":" + it?.value?.port), it.value.ssdpPath)
	}
}

private verifyEq3Bridge(String deviceNetworkId, String descPath) {
	log.trace "verifyEq3Bridge"
	log.trace "dni: $deviceNetworkId"
	String ip = getHostAddress(deviceNetworkId)

	log.trace "ip:" + ip
    log.trace "descPath:" + descPath

	sendHubCommand(new physicalgraph.device.HubAction("""GET ${descPath} HTTP/1.1\r\nHOST: ${ip}\r\n\r\n""", physicalgraph.device.Protocol.LAN, "${deviceNetworkId}"))
}

private Map eq3BridgesDiscovered() {
	log.trace "eq3BridgesDiscovered"
	def verifiedBridges = getVerifiedEq3Bridges()
	def map = [:]
	verifiedBridges.each {
		def value = "${it.value.name}"
		def key = it.value.ip + ":" + it.value.port
		map["${key}"] = value
	}
	map
}

private def getEq3BridgeSystemList()
{
	log.trace "getEq3BridgeSystemList"
	state.eq3BridgeHvacSystems = state.eq3BridgeHvacSystems ?: [:]
}

private def getVerifiedEq3Bridges()
{
	log.trace "getVerifiedEq3Bridges"
	getEq3BridgeSystemList().findAll{ it?.value?.verified == true }
}

// Return details all of the eq3 bridges that have been selected by the user
private def getSelectedEq3Bridges()
{
	log.trace "getSelectedEq3Bridges"
    
    // condition selected systems into a list
    def selectedEq3SystemsSettingsList = [] 
    if (settings.selectedEq3Systems instanceof List) {
    	selectedEq3SystemsSettingsList = settings.selectedEq3Systems
    } else {    	
        selectedEq3SystemsSettingsList.add(settings.selectedEq3Systems)
    }
    
    def verifiedSystems = getVerifiedEq3Bridges() ?: [:]
    def selectedSystemsList = []
    selectedEq3SystemsSettingsList.each {
    	def systemIpAndPort = ""+it?.value        
        def parts = systemIpAndPort.split(":")
    	def system = verifiedSystems.find{ it?.value?.ip == parts[0] &&  it?.value?.port == parts[1] }
        selectedSystemsList << system
    }
    selectedSystemsList
}

// Get a list of rooms discovered for each bridge system
private Map eq3GetSelectedSystemDiscoveredRoomList( )
{
	log.trace "eq3GetSelectedSystemRoomList"
	
    def matchingSystems = getSelectedEq3Bridges()    
    
    log.trace matchingSystems
    
    def map = [:]
	matchingSystems.each {    	
        it?.value?.rooms?.each {
        	log.trace it
        	def value = "${it.name}"
			def key = "${it.udn}"
			map["${key}"] = value
        }
    }
    map
}

private def eq3GetRoomList( )
{
	log.trace "eq3GetRoomList"
	state.eq3BridgeRoomList = state.eq3BridgeRoomList ?: [:]
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
}

def locationHandler(evt) {
	log.trace "locationHandler"
	def description = evt.description
	def hub = evt?.hubId

	def parsedEvent = parseEventMessage(description)
	parsedEvent << ["hub":hub]

	if (parsedEvent?.ssdpTerm?.contains("urn:schemas-upnp-org:device:HVAC_System:1"))
	{ //SSDP DISCOVERY EVENTS

		log.trace "HVAC system found"
		def eq3BridgeSystems = getEq3BridgeSystemList()

		if (!(eq3BridgeSystems."${parsedEvent.ssdpUSN.toString()}"))
		{ //hvac system does not exist
			eq3BridgeSystems << ["${parsedEvent.ssdpUSN.toString()}":parsedEvent]
		}
		else
		{ // update the values

			log.trace "Device was already found in state..."

			def d = eq3BridgeSystems."${parsedEvent.ssdpUSN.toString()}"
			boolean deviceChangedValues = false

			if(d.ip != parsedEvent.ip || d.port != parsedEvent.port) {
				d.ip = parsedEvent.ip
				d.port = parsedEvent.port
                d.ssdpPath = parsedEvent.ssdpPath
				deviceChangedValues = true
				log.trace "Device's port or ip changed..."
			}

			if (deviceChangedValues) {
            	log.trace "Updating child device"
				def children = getChildDevices()
				children.each {
					if (it.getDeviceDataByName("mac") == parsedEvent.mac) {
						log.trace "updating dni for device ${it} with mac ${parsedEvent.mac}"
						it.setDeviceNetworkId((parsedEvent.ip + ":" + parsedEvent.port)) //could error if device with same dni already exists
					}
                }
			}
		}
	} else if (parsedEvent.headers && parsedEvent.body)
	{ // BRIDGED EQ3 RESPONSES
		def headerString = new String(parsedEvent.headers.decodeBase64())
		def bodyString = new String(parsedEvent.body.decodeBase64())

		def type = (headerString =~ /Content-Type:.*/) ? (headerString =~ /Content-Type:.*/)[0] : null
		def body
		//log.trace "HVAC SYSTEM REPONSE TYPE: $type"
        //log.trace "Body: ${bodyString}"
        
		if (type?.contains("xml")||bodyString?.contains("<?xml"))
		{ // description.xml response (application/xml)
			body = new XmlSlurper().parseText(bodyString)			
            
			if (body?.device?.modelName?.text().contains("EQ3 Bridge"))
			{
				def eq3Bridges = getEq3BridgeSystemList()
				def eq3System = eq3Bridges.find {it?.key?.contains(body?.device?.UDN?.text())}
				if (eq3System)
				{
                	def foundRoomRaw = body?.device?.deviceList?.device?.find { it?.deviceType?.text().contains( "HVAC_ZoneThermostat" ) }
                    log.trace "Found " + foundRoomRaw.size() +" thermostat zones"
                    def foundRoomList = []
                    foundRoomRaw.each {
                    	def foundRoomDataMap = [:]  	
                        foundRoomDataMap << ["name":it?.friendlyName?.text(), "udn":it?.UDN?.text() ]
                        foundRoomList << foundRoomDataMap
                    }
					eq3System.value << ["name":body?.device?.friendlyName?.text(), "verified": true, "rooms":foundRoomList]                                         
                    eq3System.value.rooms.each {
                    	log.trace "Found thermostat zone: "+it.name
                    }                    
				}
				else
				{
					log.error "XML descriptors returned a device that didn't exist"
				}
			}
		}
		else if(type?.contains("json"))
		{ //(application/json)
			body = new groovy.json.JsonSlurper().parseText(bodyString)
			log.trace "GOT JSON $body"
		}

	}
	else {
		log.trace "cp desc: " + description
		//log.trace description
	}
    log.trace("end locationHandler")
}
private def parseEventMessage(Map event) {
	//handles attribute events
	return event
}

private def parseEventMessage(String description) {
	def event = [:]
	def parts = description.split(',')
	parts.each { part ->
		part = part.trim()
		if (part.startsWith('devicetype:')) {
			def valueString = part.split(":")[1].trim()
			event.devicetype = valueString
		}
		else if (part.startsWith('mac:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.mac = valueString
			}
		}
		else if (part.startsWith('networkAddress:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.ip = valueString
			}
		}
		else if (part.startsWith('deviceAddress:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.port = valueString
			}
		}
		else if (part.startsWith('ssdpPath:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.ssdpPath = valueString
			}
		}
		else if (part.startsWith('ssdpUSN:')) {
			part -= "ssdpUSN:"
			def valueString = part.trim()
			if (valueString) {
				event.ssdpUSN = valueString
			}
		}
		else if (part.startsWith('ssdpTerm:')) {
			part -= "ssdpTerm:"
			def valueString = part.trim()
			if (valueString) {
				event.ssdpTerm = valueString
			}
		}
		else if (part.startsWith('headers')) {
			part -= "headers:"
			def valueString = part.trim()
			if (valueString) {
				event.headers = valueString
			}
		}
		else if (part.startsWith('body')) {
			part -= "body:"
			def valueString = part.trim()
			if (valueString) {
				event.body = valueString
			}
		}
        //else log.trace "part = ${part}"
	}

	event
}    

/////////CHILD DEVICE METHODS
def parse(childDevice, description) {
	def parsedEvent = parseEventMessage(description)

	if (parsedEvent.headers && parsedEvent.body) {
		def headerString = new String(parsedEvent.headers.decodeBase64())
		def bodyString = new String(parsedEvent.body.decodeBase64())
		log.trace "parse() - ${bodyString}"

		def body = new groovy.json.JsonSlurper().parseText(bodyString)
	} else {
		log.trace "parse - got something other than headers,body..."
		return []
	}
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getHostAddress(d) {
	def parts = d.split(":")
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	return ip + ":" + port
}

private Boolean canInstallLabs()
{
	return hasAllHubsOver("000.011.00603")
}

private Boolean hasAllHubsOver(String desiredFirmware)
{
	return realHubFirmwareVersions.every { fw -> fw >= desiredFirmware }
}

private List getRealHubFirmwareVersions()
{
	return location.hubs*.firmwareVersionString.findAll { it }
}