/*
 *   Adapted from: ST Mesh Details SmartApp
 *   Copyright 2020 Tony Fleisher. Some Rights Reserved.
*/

/*
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
definition(
	name: "Hubitat Z-Wave Mesh Details",
	namespace: "tfleisher",
	author: "TonyFleisher",
	description: "Get Device Mesh and Router Details",
	category: "Utility",
	singleInstance: true,
	iconUrl: "",
	iconX2Url: "",
	oauth: true,
	importUrl: "https://raw.githubusercontent.com/TonyFleisher/tonyfleisher-hubitat/beta/Apps/mesh-details/mesh-details.groovy"
)


/**********************************************************************************************************************************************/
private releaseVer() { return "0.3.20-beta" }
private appVerDate() { return "2021-02-21" }
/**********************************************************************************************************************************************/
preferences {
	page name: "mainPage"
}

mappings {
	path("/meshinfo") { action: [GET: "meshInfo"] }
	path("/script.js") { action: [GET: "scriptController"] }
}

def mainPage() {
	dynamicPage (name: "mainPage", title: "", install: true, uninstall: true) {

		if (resetSettings) {
			resetAppSettings()
		}
		// Don't need hostoverride anymore
		if (resetHost || hostOverride) {			
			resetHostOverride()
		}

		if (embedStyle && settings?.linkStyle && settings?.linkStyle != 'embedded') {
			log.debug "Removing unused embedStyle"
			app.removeSetting('embedStyle')
		}

		if(settings?.linkStyle && !state?.hasInitializedCols) {
			if (enableDebug) log.debug "Resetting default column options"
			app.updateSetting("addCols", ["status","security","rttStdDev","lwrRssi"])
			state.hasInitializedCols = true
		}

		if (!settings.nodeBase) {
			app.updateSetting("nodeBase", "base16")
		}

		section("") {
			label title: "App name"
		}
		if (!getAccessToken()) {
            section("") {
			    paragraph title: "Enable OAuth", "Please enable OAuth for this App (in Apps Code)"
            }
        } else {
			if (app.getInstallationState() == 'COMPLETE') {
				section("") {
					paragraph "Choose if the Mesh Details webapp will open in a new window or stay in this window"
					input "linkStyle", "enum", title: "Link Style", required: true, submitOnChange: true, options: ["embedded":"Same Window", "external":"New Window"], image: ""
					if (settings?.linkStyle == 'embedded') {
						input "embedStyle", "enum", title: "Embed Style", required: true, submitOnChange: true, options: ["inline": "Display in App screen (alpha feature)", "fullscreen": "Display fullscreen (<b>default</b>)"], defaultValue: "fullscreen"
					}
				}
				section("") {
					String meshInfoLink = getAppLink("meshinfo")

					if(settings?.linkStyle) {
						input "addCols", "enum", title: "Select additional columns to display", multiple: true, options: ["status": "Status","security":"Security Mode","rttStdDev":"RTT Std Dev","lwrRssi":"LWR RSSI"], submitOnChange: true

						if (settings?.linkStyle == 'embedded' && !settings?.embedStyle) {
							paragraph title: "Select Embed Style", "Please Select a Embed style to proceed"
						} else {

							if (settings?.linkStyle == 'external' || settings?.embedStyle == 'fullscreen') {
							href "", title: "Mesh Details", url: meshInfoLink, style: (settings?.linkStyle == "external" ? "external" : "embedded"), required: false, description: "Tap Here to load the Mesh Details Web App", image: ""
							} else { // Inline
								paragraph title: "script", """
									<script id="firstStatsScript">
									var scriptLoaded; 
									if (!scriptLoaded) {\$.getScript('${getAppLink("script.js")}')}; 
									scriptLoaded=true;
									\$(document).ready( function() {
										//console.log("ready");
										\$('#firstStatsScript').parent().hide()
										var btn = \$("span:contains('Show Mesh Details')").parent()
										var docURI = btn.attr('href')
										btn.removeAttr('href')
										btn.click(function() {  
											loadScripts().then( r => loadApp(docURI).then(d => doWork()))
										})
									})
									</script>"""
			                    href "", title: "Show Mesh Details", url: meshInfoLink, style: "embedded", required: false, description: "Tap Here to view the Mesh Details", image: ""
							}
						}
					} else {
						paragraph title: "Select Link Style", "Please Select a link style to proceed"
					}
				}
				section("Advanced", hideable: true, hidden: true) {
					
					input "enableDebug", "bool", title: "Enable debug logs", defaultValue: false, submitOnChange: false
					input "deviceLinks", "bool", title: "Enable device links", defaultValue: false, submitOnChange: true
					input "nodeBase", "enum", title: "Display nodes as hex or base10?", multiple: false, options: ["base16": "base16 (default)", "base10":"base10"], submitOnChange: true
                   
					paragraph "<hr/>"			

					input "resetSettings", "bool", title: "Force app settings reset", submitOnChange: true
				}
			} else {
				section("") {
					paragraph title: "Click Done", "Please click Done to install app before continuing"
				}
			}
		}
	}
}

def resetAppSettings() {
	resetHostOverride()
	app.removeSetting("deviceLinks")
	app.removeSetting("linkStyle")
	app.removeSetting("resetSettings")
	app.removeSetting("resetHost")
	app.removeSetting("enableDebug")
	app.removeSetting('embedStyle')
	app.removeSetting("addCols")
	state.remove('hasInitializedCols')
}

def resetHostOverride() {
	if (enableDebug) log.debug "Resetting hostOverride"
	app.removeSetting("hostOverride")
	app.removeSetting("resetHost")
}

def meshInfo() {
	def html = """
<html lang="en">
<head>
<title>Hubitat Z-Wave Mesh Details</title>
<link rel="stylesheet" type="text/css" href="/ui2/css/styles.min.css">
<link rel="stylesheet" type="text/css" href="https://cdn.datatables.net/1.10.21/css/jquery.dataTables.min.css">
<link rel="stylesheet" type="text/css" href="https://cdn.datatables.net/searchpanes/1.1.1/css/searchPanes.dataTables.min.css">
<link rel="stylesheet" type="text/css" href="https://cdn.datatables.net/select/1.3.1/css/select.dataTables.min.css">
<!--  
<script src="jquery-3.5.1.min.js"></script>
-->
<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js"></script>
<script src="${getAppLink("script.js")}"></script>
<style>
td.details-control div{
    background: url('/ui2/images/sort_desc.png') no-repeat center center;
    cursor: pointer;
	transform: rotate(-90deg);
	position: relative;
	left: -4px;
}
tr.shown td.details-control div{
    background: url('/ui2/images/sort_desc.png') no-repeat center center;
	transform: none;
	left: auto;
}
div.dtsp-meshdetails-6:first-child {
    min-width: 20%;
    max-width: 20%;
}

div.dtsp-meshdetails-6 {
    min-width: 14%;
    max-width: 15.5%;
    padding-left: 0.5%;
    padding-right: 0.5%;
    margin: 0px !important;
}
div.dtsp-panesContainer div.dtsp-searchPanes div.dtsp-searchPane {
     flex-basis: 120px;
}

/*
dialog {
  position: fixed;
  top: 50%;
  transform: translate(0, -50%);
}
*/

dialog:not([open]) {
    display: none;
}

.btn-nodeDetail {
  display: block
}

#mainTable_wrapper {
	overflow: auto
}

.dtsp-searchButtonCont,.dtsp-buttonGroup {
	display: none !important;
}

.dtsp-searchPanes .even:not(.selected) {
    background-color: #ffffff !important;
}
</style>
</head>
<body>
<h1 style="text-align:center; flex-basis:100%;">Hubitat Z-Wave Mesh Details <div role="doc-subtitle" style="font-size: small;">(v${releaseVer() + ' - ' + appVerDate()})</div> </h1>
<div id="messages" style="text-align:center; flex-basis:100%;"><div id="loading1" style="text-align:center;"></div><div id="loading2" style="text-align:center;"></div></div>
<div id="view-topology-div">
	<button type="button" id="view-topology" data-toggle="modal" type="button" onclick="getTopologyModal()">
		View Z-Wave Topology
	</button>
</div>
<table id="mainTable" class="stripe cell-border hover" style="width: 100%">
	<thead>
	<tr>
	</tr>
	</thead>
</table>
<div style="text-align:center;">&copy; 2020 Tony Fleisher. All Rights Reserved.</div>
<dialog id="topologyDialog">
    <span class="mdl-dialog__title">Z-Wave Topology</span>
	<button id="hidRepeatersBtn" onclick="hideNonRepeaters()" data-toggle="modal">Hide NonRepeaters</button>
	<button id="hidRepeatersBtn" onclick="getTopologyModal()" data-toggle="modal">Refresh</button>
    <div class="mdl-dialog__content">
        <p></p>
        <p style="color: darkblue"><span id="zwave-topology-table"></span></p>
    </div>
    <div class="mdl-dialog__actions">
        <button type="button" onclick="closeTopology()" class="mdl-button close" id="close-zwave-topology">Close</button>
    </div>
</dialog>
<dialog id="zwaveRepairStatus">
    <span class="mdl-dialog__title">Z-Wave Repair</span>
    <div class="mdl-dialog__content">
        <p></p>
        <p style="color: darkblue"><span id="zwave-repair-status"><br><br><br><br></span></p>
    </div>
    <div class="mdl-dialog__actions">
        <button type="button" onclick="closeRepair()" class="mdl-button close" id="close-zwave-repair">Close</button>
        <button type="button" class="mdl-button close" onclick="cancelRepair()" id="abort-zwave-repair">Abort</button>
    </div>
</dialog>
</body>
</html>
	"""
	render contentType: "text/html", data: html
}

def scriptController() {
	def javaScript = """
const CMD_CLASS_Names = {
		0x20: "Basic",
		0x21: "Controller Replication",
		0x22: "Application Status",
		0x25: "Binary Switch",
		0x26: "Multilevel Switch",
		0x27: "All Switch (obsoleted)",
		0x28: "Binary Toggle Switch (obsoleted)",
		0x29: "Multilevel Toggle Switch (deprecated)",
		0x2B: "Scene Activation",
		0x2C: "Scene Actuator Configuration",
		0x2D: "Scene Controller Configuration",
		0x30: "Binary Sensor (deprecated)",
		0x31: "Multilevel Sensor",
		0x32: "Meter",
		0x33: "Color Switch",
		0x35: "Pulse Meter (deprecated)",
		0x36: "Basic Tariff",
		0x37: "HRV Status",
		0x39: "HRV Control",
		0x3A: "Demand Control Plan Configuration",
		0x3B: "Demand Control Plan Monitor",
		0x3C: "Meter Table Configuration",
		0x3D: "Meter Table Monitor",
		0x3E: "Meter Table Push Configuration",
		0x3F: "Prepayment",
		0x40: "Thermostat Mode",
		0x41: "Prepayment Encapsulation",
		0x42: "Thermostat Operating State",
		0x43: "Thermostat Setpoint",
		0x44: "Thermostat Fan Mode",
		0x45: "Thermostat Fan State",
		0x46: "Climate Control Schedule (deprecated)",
		0x47: "Thermostat Setback",
		0x48: "Rate Table Configuration",
		0x49: "Rate Table Monitor",
		0x4A: "Tariff Table Configuration",
		0x4B: "Tariff Table Monitor",
		0x4C: "Door Lock Logging",
		0x4E: "Schedule Entry Lock (deprecated)",
		0x50: "Basic Window Covering (obsoleted)",
		0x51: "Move to Position Window Covering (obsoleted)",
		0x53: "Schedule",
		0x55: "Transport Service",
		0x56: "CRC-16 Encapsulation (deprecated)",
		0x57: "Application Capability (obsoleted)",
		0x59: "Association Group Info",
		0x5A: "Device Reset Locally",
		0x5B: "Central Scene",
		0x5E: "Z-Wave Plus Info",
		0x60: "Multi Channel",
		0x62: "Door Lock",
		0x63: "User Code",
		0x66: "Barrier Operator",
		0x6C: "Supervision",
		0x70: "Configuration",
		0x71: "Notification (Alarm)",
		0x72: "Manufacturer Specific",
		0x73: "Powerlevel",
		0x75: "Protection",
		0x76: "Lock (deprecated)",
		0x77: "Node Naming and Location",
		0x7A: "Firmware Update Meta Data",
		0x7B: "Grouping Name (deprecated)",
		0x7C: "Remote Association Activation (obsoleted)",
		0x7D: "Remote Association Configuration (obsoleted)",
		0x80: "Battery",
		0x81: "Clock",
		0x82: "Hail (obsoleted)",
		0x84: "WakeUp",
		0x85: "Association",
		0x86: "Version",
		0x87: "Indicator",
		0x88: "Proprietary (obsoleted)",
		0x89: "Language",
		0x8A: "Time",
		0x8B: "Time Parameters",
		0x8C: "Geographic Location",
		0x8E: "Multi Channel Association",
		0x8F: "Multi Command",
		0x90: "Energy Production",
		0x92: "Screen Meta Data",
		0x93: "Screen Attributes",
		0x94: "Simple AV Control",
		0x98: "Security",
		0x9A: "IP Configuration (obsoleted)",
		0x9B: "Association Command Configuration",
		0x9C: "Alarm Sensor (deprecated)",
		0x9D: "Alarm Silence",
		0x9E: "Sensor Configuration (obsoleted)",
		0x9F: "Security 2"
}

function useHex() {
	return "${settings?.nodeBase}" === "base16"
}

function loadScripts() {
	\$.get('/ui2/js/hubitat.min.js')
	updateLoading('Loading...','Getting script sources');
	var s1 = \$.getScript('https://unpkg.com/axios/dist/axios.min.js', function() {
		//console.log("axios loaded")
	});
	
	var s2 = \$.getScript('https://cdn.datatables.net/1.10.21/js/jquery.dataTables.min.js')
	.then(s => \$.getScript('https://cdn.datatables.net/select/1.3.1/js/dataTables.select.min.js'))
	.then(s => \$.getScript('https://cdn.datatables.net/searchpanes/1.1.1/js/dataTables.searchPanes.min.js'))
	.then(s => {

		function numberSort(a,b) {
			var token1a = a.split('-',2)[0].trim()
			var token1b = b.split('-',2)[0].trim()
			var vala = parseInt(token1a)
			var valb = parseInt(token1b)
			if (!vala) return 1;
			if (!valb) return -1;
			return vala < valb ? -1 : 1			
		}
		jQuery.extend( jQuery.fn.dataTableExt.oSort, {
			"initialNumber-asc": function ( a, b ) {
				return numberSort(a,b);
			},
			"initialNumber-desc": function ( a, b ) {
				return numberSort(a,b) * -1;
			},
		})
	})
	;
	return Promise.all([s1, s2]);
}

// Get transformed list of devices (see transformDevice)
function getZwaveList() {
	const instance = axios.create({
		timeout: 5000,
		responseType: "stream"
		});

	return instance
	.get('/hub/zwaveInfo')
	.then(response => {
		//if ($enableDebug) console.log (`Response: \${JSON.stringify(response)}`)
		var doc = new jQuery(response.data)
		var deviceRows = doc.find('.device-row')
		var results = []
		deviceRows.each (
			(index,row) => {
				results.push(transformZwaveRow(row))
			}
		)
		return results
	})
	.catch(error => { console.error(error); updateLoading("Error", error);} );
}

function getZwaveNodeDetail() {
	const instance = axios.create({
		timeout: 5000
	});

	return instance
	.get('/hub/zwaveNodeDetail')
	.then(response => {
		//if ($enableDebug) console.log (`Response: \${JSON.stringify(response)}`)

		return response
	})
	.catch(error => { console.error(error); updateLoading("Error", error);} );
}

function transformZwaveRow(row) {
	var childrenData = row.children
	var statsText = childrenData[1].innerHTML.trim().replace('<br>',' , ')
	var statsList = statsText.split(',').map(e => e.trim())
	var statMap = {}
	statsList.forEach( s => {
		parts = s.split(':')
		statMap[parts[0]] = parts[1].trim()
	})

	// "01 -> 08 -> 0C -> 1B 100kbps"
	var routesText = childrenData[6].innerText ? childrenData[6].innerText.trim() : ''
	var routers = routesText ? routesText.split(' -> ') : []
	
	var connectionSpeed = "Unknown"
	if (routers.length > 0) {
		var lastParts = routers.splice(-1,1)
		routers.splice(0,1)
		connectionSpeed = lastParts[0].split(' ')[1]
			routers = routers.map(r => useHex() ? "0x" + r : parseInt("0x"+r))
	}

	if (routers.length == 0 && connectionSpeed != '') {
		routers = ['DIRECT']
	}

	var nodeText = childrenData[0].innerText.trim()

	var devId = (nodeText.match(/0x([^ ]+) /))[1]
	var devIdDec = (nodeText.match(/\\(([0-9]+)\\)/))[1]
	var devId2 = parseInt("0x"+devId)

	var label = ""
	var deviceLink = ""
	if (childrenData[4].innerText.trim() != '') {
		label = childrenData[4].innerText.trim()
		deviceLink = childrenData[4].firstElementChild.getAttribute('href')
		hubDeviceId = deviceLink.split('/')[3]
	} else {
		label = "<NO NAME>"
	}

	var typeParts = childrenData[3].innerHTML.split("<br>")
	if (typeParts && typeParts.length >= 2) {
		var type = translateDeviceType(typeParts[0])
		var brand = typeParts[1]
	}
	// Command Classes
	// Older firnmware has this, newer doesn't
	var commandClassesText = childrenData[3].innerText
	const CC_REGEX = /in:(.*), out:(.*)/
	var classesParts = commandClassesText.match(CC_REGEX)

	var inClusters = classesParts && classesParts.length > 1 ? classesParts[1].trim() : undefined
	var inCommandClasses = inClusters && inClusters.length > 0 ? inClusters.split(', ') : []

	var outClusters = classesParts && classesParts.length > 2 ? classesParts[2].trim() : undefined
	var outCommandClasses = outClusters && outClusters.length > 0 ? outClusters.split(', ') : []
	var commandClasses = inCommandClasses.concat(outCommandClasses)

	var deviceData = {
		id: devId,
		id2: devId2,
		devIdDec: devIdDec,
		node: nodeText.replace(' ', '&nbsp;'),
		metrics: statMap,
		routers: routers,
		label: label,
		type: type,
		brand: brand,
		deviceLink: deviceLink,
		hubDeviceId: hubDeviceId,
		deviceSecurity: childrenData[5].innerText.trim(),
		routeHtml: routers.reduce( (acc, v, i) => (v == 'DIRECT') ? v : acc + ` -> \${v}`, "") + (routers[0] == 'DIRECT' ? '' : ` -> \${useHex() ? "0x" + devId : devId2}`) ,
		deviceStatus: childrenData[2].firstChild.data.trim(),
		connection: connectionSpeed,
		commandClasses: commandClasses,
		isZwavePlusDevice: inCommandClasses.length > 0 && inCommandClasses[0] == '0x5E',
		isSleepyDevice: inCommandClasses.length > 0 && inCommandClasses.includes('0x84')
	}
	return deviceData
}

function translateDeviceType(deviceType) {
	switch (deviceType) {
		case "SPECIFIC_TYPE_DOORBELL":
			return "Doorbell"
		case "SPECIFIC_TYPE_SATELLITE_RECEIVER":
			return "Satellite Receiver" 
		case "SPECIFIC_TYPE_SATELLITE_RECEIVER_V2":
			return "Satellite Receiver V2" 
		case "SPECIFIC_TYPE_SOUND_SWITCH":
			return "Sound Switch"
		case "SPECIFIC_TYPE_SIMPLE_DISPLAY":
			return "Simple Display" 
		case "SPECIFIC_TYPE_DOOR_LOCK":
			return "Door Lock" 
		case "SPECIFIC_TYPE_ADVANCED_DOOR_LOCK":
			return "Advanced Door Lock" 
		case "SPECIFIC_TYPE_SECURE_KEYPAD_DOOR_LOCK":
			return "Secure Keypad Door Lock" 
		case "SPECIFIC_TYPE_SECURE_KEYPAD_DOOR_LOCK_DEADBOLT":
			return "Door Lock Keypad Deadbolt" 
		case "SPECIFIC_TYPE_SECURE_DOOR":
			return "Secure Door" 
		case "SPECIFIC_TYPE_SECURE_GATE":
			return "Secure Gate" 
		case "SPECIFIC_TYPE_SECURE_BARRIER_ADDON":
			return "Secure Barrier Addon" 
		case "SPECIFIC_TYPE_SECURE_BARRIER_OPEN_ONLY":
			return "Secure Barrier Open Only" 
		case "SPECIFIC_TYPE_SECURE_BARRIER_CLOSE_ONLY":
			return "Secure Barrier Close Only" 
		case "SPECIFIC_TYPE_SECURE_LOCKBOX":
			return "Secure Lockbox" 
		case "SPECIFIC_TYPE_SECURE_KEYPAD":
			return "Secure Keypad"
		case "SPECIFIC_TYPE_PORTABLE_REMOTE_CONTROLLER":
			return "Portable Remote Controller" 
		case "SPECIFIC_TYPE_PORTABLE_SCENE_CONTROLLER":
			return "Portable Scene Controller" 
		case "SPECIFIC_TYPE_PORTABLE_INSTALLER_TOOL":
			return "Portable Installer Tool"
		case "SPECIFIC_TYPE_REMOTE_CONTROL_AV":
			return "Remote Control AV" 
		case "SPECIFIC_TYPE_REMOTE_CONTROL_SIMPLE":
			return "Remote Control Simple" 
		case "SPECIFIC_TYPE_SIMPLE_METER":
			return "Simple Meter" 
		case "SPECIFIC_TYPE_ADV_ENERGY_CONTROL":
			return "Adv Energy Control" 
		case "SPECIFIC_TYPE_WHOLE_HOME_METER_SIMPLE":
			return "Whole Home Meter Simple" 
		case "SPECIFIC_TYPE_REPEATER_SLAVE":
			return "Repeater Slave" 
		case "SPECIFIC_TYPE_VIRTUAL_NODE":
			return "Virtual Node"
		case "SPECIFIC_TYPE_ZONED_SECURITY_PANEL":
			return "Zoned Security Panel"
		case "SPECIFIC_TYPE_ENERGY_PRODUCTION":
			return "Energy Production" 
		case "SPECIFIC_TYPE_ADV_ZENSOR_NET_ALARM_SENSOR":
			return "Adv Zensor Net Alarm Sensor"
		case "SPECIFIC_TYPE_ADV_ZENSOR_NET_SMOKE_SENSOR":
			return "Adv Zensor Net Smoke Sensor"
		case "SPECIFIC_TYPE_BASIC_ROUTING_ALARM_SENSOR":
			return "Basic Routing Alarm Sensor"
		case "SPECIFIC_TYPE_BASIC_ROUTING_SMOKE_SENSOR":
			return "Basic Routing Smoke Sensor"
		case "SPECIFIC_TYPE_BASIC_ZENSOR_NET_ALARM_SENSOR":
			return "Basic Zensor Net Alarm Sensor"
		case "SPECIFIC_TYPE_BASIC_ZENSOR_NET_SMOKE_SENSOR":
			return "Basic Zensor Net Smoke Sensor"
		case "SPECIFIC_TYPE_ROUTING_ALARM_SENSOR":
			return "Routing Alarm Sensor"
		case "SPECIFIC_TYPE_ROUTING_SMOKE_SENSOR":
			return "Routing Smoke Sensor"
		case "SPECIFIC_TYPE_ZENSOR_NET_ALARM_SENSOR":
			return "Zensor Net Alarm Sensor"
		case "SPECIFIC_TYPE_ZENSOR_NET_SMOKE_SENSOR":
			return "Zensor Net Smoke Sensor"
		case "SPECIFIC_TYPE_ALARM_SENSOR":
			return "Alarm Sensor" 
		case "SPECIFIC_TYPE_ROUTING_SENSOR_BINARY":
			return "Routing Sensor Binary" 
		case "SPECIFIC_TYPE_ROUTING_SENSOR_MULTILEVEL":
			return "Routing Sensor Multilevel" 
		case "SPECIFIC_TYPE_CHIMNEY_FAN":
			return "Chimney Fan"
		case "SPECIFIC_TYPE_PC_CONTROLLER":
			return "Pc Controller" 
		case "SPECIFIC_TYPE_SCENE_CONTROLLER":
			return "Scene Controller" 
		case "SPECIFIC_TYPE_STATIC_INSTALLER_TOOL":
			return "Static Installer Tool"
		case "SPECIFIC_TYPE_SET_TOP_BOX":
			return "Set Top Box" 
		case "SPECIFIC_TYPE_SUB_SYSTEM_CONTROLLER":
			return "Sub System Controller" 
		case "SPECIFIC_TYPE_TV":
			return "TV" 
		case "SPECIFIC_TYPE_GATEWAY":
			return "Gateway" 
		case "SPECIFIC_TYPE_POWER_SWITCH_BINARY":
			return "Power Switch Binary" 
		case "SPECIFIC_TYPE_SCENE_SWITCH_BINARY":
			return "Scene Switch Binary" 
		case "SPECIFIC_TYPE_POWER_STRIP":
			return "Power Strip" 
		case "SPECIFIC_TYPE_SIREN":
			return "Siren" 
		case "SPECIFIC_TYPE_VALVE_OPEN_CLOSE":
			return "Valve Open/Close" 
		case "SPECIFIC_TYPE_COLOR_TUNABLE_BINARY":
			return "Binary Tunable Color Light"
		case "SPECIFIC_TYPE_IRRIGATION_CONTROLLER":
			return "Irrigation Controller"
		case "SPECIFIC_TYPE_CLASS_A_MOTOR_CONTROL":
			return "Class A Motor Control" 
		case "SPECIFIC_TYPE_CLASS_B_MOTOR_CONTROL":
			return "Class B Motor Control" 
		case "SPECIFIC_TYPE_CLASS_C_MOTOR_CONTROL":
			return "Class C Motor Control" 
		case "SPECIFIC_TYPE_MOTOR_MULTIPOSITION":
			return "Motor Multiposition" 
		case "SPECIFIC_TYPE_POWER_SWITCH_MULTILEVEL":
			return "Power Switch Multilevel" 
		case "SPECIFIC_TYPE_SCENE_SWITCH_MULTILEVEL":
			return "Scene Switch Multilevel" 
		case "SPECIFIC_TYPE_FAN_SWITCH":
			return "Fan Switch" 
		case "SPECIFIC_TYPE_COLOR_TUNABLE_MULTILEVEL":
			return "Multilevel Tunable Color Light"
		case "SPECIFIC_TYPE_SWITCH_REMOTE_BINARY":
			return "Switch Remote Binary" 
		case "SPECIFIC_TYPE_SWITCH_REMOTE_MULTILEVEL":
			return "Switch Remote Multilevel" 
		case "SPECIFIC_TYPE_SWITCH_REMOTE_TOGGLE_BINARY":
			return "Switch Remote Toggle Binary" 
		case "SPECIFIC_TYPE_SWITCH_REMOTE_TOGGLE_MULTILEVEL":
			return "Switch Remote Toggle Multilevel" 
		case "SPECIFIC_TYPE_SWITCH_TOGGLE_BINARY":
			return "Switch Toggle Binary" 
		case "SPECIFIC_TYPE_SWITCH_TOGGLE_MULTILEVEL":
			return "Switch Toggle Multilevel" 
		case "SPECIFIC_TYPE_SETBACK_SCHEDULE_THERMOSTAT":
			return "Setback Schedule Thermostat" 
		case "SPECIFIC_TYPE_SETBACK_THERMOSTAT":
			return "Setback Thermostat" 
		case "SPECIFIC_TYPE_SETPOINT_THERMOSTAT":
			return "Setpoint Thermostat"
		case "SPECIFIC_TYPE_THERMOSTAT_GENERAL":
			return "Thermostat General" 
		case "SPECIFIC_TYPE_THERMOSTAT_GENERAL_V2":
			return "Thermostat General V2" 
		case "SPECIFIC_TYPE_THERMOSTAT_HEATING":
			return "Thermostat Heating" 
		case "SPECIFIC_TYPE_RESIDENTIAL_HRV":
			return "Residential Hrv"
		case "SPECIFIC_TYPE_SIMPLE_WINDOW_COVERING":
			return "Simple Window Covering" 
		case "SPECIFIC_TYPE_ZIP_ADV_NODE":
			return "Zip Adv Node"
		case "SPECIFIC_TYPE_ZIP_TUN_NODE":
			return "Zip Tun Node"
		case "SPECIFIC_TYPE_BASIC_WALL_CONTROLLER":
			return "Basic Wall Controller" 
		case "SPECIFIC_TYPE_SECURE_EXTENDER":
			return "Secure Extender" 
		case "SPECIFIC_TYPE_GENERAL_APPLIANCE":
			return "General Appliance"
		case "SPECIFIC_TYPE_KITCHEN_APPLIANCE":
			return "Kitchen Appliance"
		case "SPECIFIC_TYPE_LAUNDRY_APPLIANCE":
			return "Laundry Appliance"
		case "SPECIFIC_TYPE_NOTIFICATION_SENSOR":
			return "Notification Sensor"
		default:
			return deviceType
	}
}

function updateLoading(msg1, msg2) {
	\$('#loading1').text(msg1);
	\$('#loading2').text(msg2);
}

async function getData() {

	var devList = await getZwaveList()
	var fullNameMap = devList.reduce( (acc,val) => {
						 acc[useHex() ? `0x\${val.id}` : val.id2]= `\${useHex() ? `0x\${val.id}` : val.id2} - \${val.label}`;
						 return acc;
					 }, {});

	// Pseudo entry for direct-connected devices
	fullNameMap.DIRECT = 'DIRECT'
	
	updateLoading('Loading.','Getting device detail');
	var nodeDetails = await getZwaveNodeDetail()

	updateLoading('Loading..', 'Building Neighbors Lists')
	buildNeighborsLists(fullNameMap, nodeDetails.data)

	var tableContent = devList.map( dev => {
		var routersFull = dev.routers.map(router => fullNameMap[router] || `\${router} - UNKNOWN`)
		var detail = nodeDetails.data[dev.id2.toString()]

		var variance = 0
		var stdDev = "0.00"
		
		var count = detail.transmissionCount
		if (count > 0) {
			var totalSquared = Math.pow(detail.sumOfTransmissionTimes,2)
			var sumOfTransmissionTimesSquared = detail.sumOfTransmissionTimesSquared
			var ss = (sumOfTransmissionTimesSquared - (totalSquared/count)).toFixed(0)
			variance = (ss/count).toFixed(2)
			stdDev = Math.sqrt(variance).toFixed(2)
		}
		dev.metrics.rtt_variance = variance
		dev.metrics.std_dev = stdDev
		return {...dev, 'routersFull': routersFull, 'detail': detail}
	})

	return tableContent
}

function getZwaveDeviceInfo(devId) {
	const instance = axios.create({
		timeout: 5000,
		responseType: "stream"
		});

	return instance
	.get('/device/edit/' + devId)
	.then(response => {
		//if ($enableDebug) console.log (`Response: \${JSON.stringify(response)}`)
		var doc = new jQuery(response.data)
		var deviceData = doc.find('#tableDeviceDetails li')
		var inClusters
		var outClusters
		deviceData.each (
			(index,row) => {
				var kvp = row.innerText.split(":\\n")
				if (kvp[0].trim() == "inClusters")
					inClusters = kvp[1].trim()
				else if (kvp[0].trim() == "outClusters")
					outClusters = kvp[1].trim()
			}
		)
		return {inClusters,outClusters}
	})
	//.catch(error => { console.error(error); } );
}

function findDeviceByDecId(devId) {
	return tableContent.find( row => row.id2 == devId)
}

function findDeviceByHexId(devId) {
	return tableContent.find( row => row.id == devId)
}

function decodeSpeed(val) {
	return val == (undefined || '') ? 'unknown' 
		: val == '01' ? '9.6 kbps' 
		: val == '02' ? '40 kbps' 
		: val == '03' ? '100 kbps' 
		: 'UNKNOWN'
}

// Map dev id -> [neighbors]
var neighborsMap = new Map()
// Map dev id -> [seen by]
var neighborsMapReverse = new Map()
// List of ids that are not repeaters
var nonRepeaters = new Set()

function buildNeighborsLists(fullnameMap, nodeData) {
	neighborsMap = new Map()
	neighborsMapReverse = new Map()
	nonRepeaters = new Set()
	Object.entries(nodeData).forEach(  e1 => {
		var devId = e1[0]
		var detail = e1[1]
		if (detail.neighbors) {
			Object.entries(detail.neighbors).forEach( e2 => {
					var neighborId = e2[0]
					var neighborDetail = e2[1]
					if (!neighborsMap.has(devId)) {
						neighborsMap.set(devId, [])
					}
					n = neighborsMap.get(devId)
					n.push(neighborId)

					if (!neighborsMapReverse.has(neighborId)) {
						neighborsMapReverse.set(neighborId, [])
					}
					r = neighborsMapReverse.get(neighborId)
					r.push(devId)

					if (neighborDetail.repeater == '0') {
						nonRepeaters.add(neighborId)
					}

					var nHex = ('00'+parseInt(neighborId).toString(16)).slice(-2).toUpperCase()
					if (!fullnameMap[nHex]) {
						fullnameMap[nHex] = `\${nHex} - UNKNOWN`
					}

			})
		}
	})
}


function displayRowDetail(row) {
	var devId = row.id()
	var neighborList = []
	var deviceData = tableContent.find( row => row.id == devId)
	var data = row.data()

	var html = '<div><table>'

	// Header Row
	html += '<tr>'
	html += '<th>Repeaters</th>'
	html += '<th>Neighbors</th><th>NeighborOf</th>'

	if (data.commandClasses && data.commandClasses.length > 0) {
		html += '<th>Command Classes</th>'
	}

	html += '<th>Actions</th>'
	html += '</tr>'
	// End Header Row

	html += '<tr>'
	html += '<td style="vertical-align: top;">'
	html += deviceData.routersFull.join('<br/>')
	html += '</td>'

	html += '<td style="vertical-align: top;">'
	var neighborMap = neighborsMap.get(deviceData.id2.toString())
	if (neighborMap && neighborMap.length > 0) {
		neighborMap.forEach( (devId) => {
			if (devId < 6) { 
				html += useHex() ? '0x' : ''
				html += `\${devId} - HUB`
			} else {
				var deviceData = findDeviceByDecId(devId)
				html += useHex() ? `0x\${deviceData.id}` : deviceData.id2
				html += ` - \${deviceData.label}`
				if (nonRepeaters.has(deviceData.id2.toString())) {
					html += '<sup style="vertical-align:text-top;">*</sup>'
				}
			}
			html += '<br/>'
		})
	}
	html += '</td>'

	html += '<td style="vertical-align: top;">'
	var neighborOfMap = neighborsMapReverse.get(deviceData.id2.toString())
	if (neighborOfMap && neighborOfMap.length > 0) {
		neighborOfMap.forEach( (devId) => {
			if (devId < 6) { 
				html += useHex() ? '0x' : ''
				html += `\${devId} - HUB`
			} else {
				var deviceData = findDeviceByDecId(devId)
				html += useHex() ? `0x\${deviceData.id}` : deviceData.id2
				html += ` - \${deviceData.label}`

				if (nonRepeaters.has(deviceData.id2.toString())) {
					html += '<sup style="vertical-align:text-top;">*</sup>'
				}
			}
			html += '<br/>'
		})
	}
	html += '</td>'

	if (data.commandClasses && data.commandClasses.length > 0) {
		html += '<td style="vertical-align: top;">'
		data.commandClasses.forEach( cc => {
			html += cc
			var ccVal = Number(cc)
			if (CMD_CLASS_Names[ccVal]) {
				html += ` - \${CMD_CLASS_Names[ccVal]}`
			}
			html += "<br/>"
		});
		html += '</td>'
	}

	html += '<td style="vertical-align: top;">'

	if ($enableDebug) {
		html += '<button class="debug-control" onclick="showDetailDebug(this)" class="btn btn-danger btn-nodeDetail">Show Debug</button>'
		var pretty = JSON.stringify(data.detail,null,'JSONS')
		html += '<div hidden="true" class="debug-content"><pre>'
		html += pretty.replace(/JSONS/g, '&nbsp;&nbsp;')
		html += '</pre></div>'
	}

	html += `<button onclick="zwaveNodeRepair(\${data.id2})" class="btn btn-danger btn-nodeDetail">Repair</button>`
	html += '</td>'

	html += '</tr></table>'
	html += '<p><sup style="vertical-align:text-top;">*</sup>Device is a slave (non-repeater)</p>'
	html += '</div>'
	return html

}

function showDetailDebug(btn) {
	btn.parentElement.getElementsByClassName('debug-content')[0].hidden=false
}

function zwaveNodeRepair(zwaveNodeId) {

	\$("#close-zwave-repair").attr("disabled", true)
	\$("#abort-zwave-repair").attr("disabled", false)
	if (dialogPolyfill && !zwaveRepairStatus.showModal) {
             dialogPolyfill.registerDialog(zwaveRepairStatus);
    }
	\$.ajax({
		url: "/hub/zwaveNodeRepair2?zwaveNodeId="+zwaveNodeId,
		type: "GET",
		success: function (data) {
			console.log(data)
				repairUpdateInterval = setInterval(checkZwaveRepairStatus, 3000)
				\$("#zwave-repair-status").html('')
				if (zwaveRepairStatus.showModal) {
					zwaveRepairStatus.showModal();
				}
		},
		error: function (data) {

		}
	});
};

function checkZwaveRepairStatus(){
	\$.ajax({
		url: "/hub/zwaveRepair2Status",
		type: "GET",
		dataType: 'JSON',
		success: function (data) {
			console.log(data.stage)
			\$("#zwave-repair-status").html(data.html)
			if(data.stage === "IDLE"){
				\$("#close-zwave-repair").attr("disabled", false)
				\$("#abort-zwave-repair").attr("disabled", true)
				clearInterval(repairUpdateInterval)
			} else {
				\$("#close-zwave-repair").attr("disabled", true)
				\$("#abort-zwave-repair").attr("disabled", false)
			}
		},
		error: function (data) {

		}
	});
}

function getTopologyModal() {
	if (dialogPolyfill && !topologyDialog.showModal) {
		dialogPolyfill.registerDialog(topologyDialog);
	}
	\$.ajax({
		url: "/hub/zwaveTopology",
		type: "GET",
		success: function(result) {
			\$("#zwave-topology-table").html(result);
			topologyDialog.showModal();
		}
	});
}

function cancelRepair() {
	\$.ajax({
		url: "/hub/zwaveCancelRepair",
		type: "GET",
		success: function(result) {

		}
	});
}

function closeRepair() {
	var dialog = document.querySelector('#zwaveRepairStatus')
	dialog.close()
}

function closeTopology() {
	var dialog = document.querySelector('#topologyDialog')
	dialog.close()
}

function hideNonRepeaters() {
	topr = \$('#topologyDialog table tbody tr:nth-child(1)') // Get the top row with nodes (hex starting in position 2)
	rowItems = topr[0].innerText.split(/\\s+/) // Split into a list
	rowItems.slice(2).forEach( (item,index) => {
		if(item.match(/[A-F0-9]/)) {
			if ($enableDebug) console.log(`Testing \${item}`)
			const d = findDeviceByHexId(item)
			if (d.isSleepyDevice) {
				if ($enableDebug) console.log(`\${item} is sleepy; hiding`)
				\$(`#topologyDialog table tbody tr td:nth-child(\${index+3})`).hide()
				\$(`#topologyDialog table tbody tr:nth-child(\${index+3})`).hide()
			} else {
				if ($enableDebug) console.log('not sleepy')
			}
			if (nonRepeaters.has(d.id2.toString())) {
				if ($enableDebug) console.log(`\${item} is not a repeater; hiding`)
				\$(`#topologyDialog table tbody tr td:nth-child(\${index+3})`).hide()
				\$(`#topologyDialog table tbody tr:nth-child(\${index+3})`).hide()
			} else {
				if ($enableDebug) console.log('not in nonrepeaters list')
			}

			var neighborOfMap = neighborsMapReverse.get(d.id2.toString())
			if (!neighborOfMap || neighborOfMap.length == 0) {
				if ($enableDebug) console.log(`\${item} is not seen by any other device; hiding`)
				\$(`#topologyDialog table tbody tr td:nth-child(\${index+3})`).hide()
				\$(`#topologyDialog table tbody tr:nth-child(\${index+3})`).hide()
			} else {
				if ($enableDebug) console.log(`has neighbors: ${neighborOfMap}`)
			}
		}
	})
}

function loadApp(appURI) {
	const instance = axios.create({
		timeout: 5000,
		responseType: "document"
		});

	return instance
	.get(appURI)
	.then(response => {
		var doc = new jQuery(response.data)

		var h = doc.find('head').children()
		\$('head').append(h)

        var c = doc.find('body').children()
		\$('main > :first-child').children().hide()
        \$('main > :first-child').append(c)
        var currentPage = \$('#currentPage').val()
        history.pushState({currentPage: currentPage, previousPage: null, statsLoaded: true, appURI: appURI}, "View Hub Stats", "?page=view&debug=true")
	})
	.catch(error => { console.error(error); updateLoading("Error", error);} );
}

window.onpopstate = function(event) {
    //console.log("popstate")
    //console.log(event)
    if (event.state == null) {
        return
    }
    if (event.state.statsLoaded) {
        loadScripts().then( r => loadApp(event.state.appURI).then(d => doWork()))
    } else {
        location.reload()
    }
}

\$.ajaxSetup({
	cache: true
	});
var tableContent;
var tableHandle;
if ( "${settings?.embedStyle}" != 'inline') {
	\$(document).ready(doWork())
}

function doWork() {

		loadScripts().then(function() {
			updateLoading('Loading..','Getting device data');
			getData().then( r => {	
				// console.log(list)
				tableContent = r;

				updateLoading('Loading..','Creating table');
				var idCol = useHex() ? 'id' : 'id2';
				tableHandle = \$('#mainTable').DataTable({
					data: tableContent,
					order: [[1,'asc']],
					columns: [
						//{ data: 'networkType', title: 'Type', searchPanes: { preSelect:['ZWAVE','ZIGBEE']} },
						{
							"className":      'details-control',
							"orderable":      false,
							"data":           null,
							"defaultContent": '<div>&nbsp;</div>'
						},
						{ data: useHex() ? 'id' : 'id2', title: 'Node',
							render: function(data, type, row) {
								return useHex() ? `0x\${data}` : data
							},
						},
						{ data: 'deviceStatus', title: 'Status', searchPanes: {controls: false}, visible: ${settings?.addCols?.contains("status")},
							render: function(data, type, row) {
								if (type === 'filter' || type === 'sp' || type === 'display') {
									return data
								}
								if (type === 'sort' || type === 'type') {
									if (data == 'OK') 
										return 0
									else if (data == 'NOT_RESPONDING')
										return 1
									else if (data == 'FAILED')
										return 2
									else 
										return `3\${data}`
								}
							},
							"createdCell": function (td, cellData, rowData, row, col) {
								var isRepeater = nonRepeaters.has(rowData.id2.toString())
								if ( cellData != "OK" ) {
									if (!isRepeater) {
										\$(td).css('color', 'red')
									} else {
										\$(td).wrapInner('<strike>')
									}
								}
							}
						},
						{ data: 'label', title: 'Device name', defaultContent: "!NO DEVICE!"
						,
							createdCell: function (td, cellData, rowData, row, col) {
								if ($deviceLinks == true){
									\$(td).wrapInner(`<a href="\${rowData.deviceLink}">`)
								}
							}
						},
						{ data: 'type', title: 'Device type', defaultContent: "!NO DEVICE!" },
						{ data: 'brand', title: 'Manufacturer', defaultContent: "!NO DEVICE!" },
						{ data: 'routersFull', title: 'Repeater', visible: false,
							render: {'_':'[, ]', sp: '[]'},
							defaultContent: "None",
							searchPanes : { orthogonal: 'sp' },
							type: 'initialNumber'
						},
						{ data: 'connection', title: 'Connection <br/>Speed', defaultContent: "Unknown",
							searchPanes: { header: 'Speed', controls: false}},
						{ data: 'metrics.RTT Avg', title: 'RTT Avg', defaultContent: "n/a", searchPanes: {orthogonal: 'sp', controls: false},
							render: function(data, type, row) {
								var val = data.match(/(\\d*)ms/)[1]
								if (type === 'filter' || type === 'sp') {
								return val == (undefined || '') ? 'unknown' : val < 100 ? '0-100ms' : val <= 500 ? '100-500ms' : '> 500ms'
								} else if (type === 'sort' || type === 'type') {
									return val
								} else {
									return val ? 
										`\${val} ms`
										: 'unknown'
								}
							},
							createdCell: function (td, cellData, rowData, row, col) {
								var val = cellData.match(/(\\d*)ms/)[1]
								if ( val > 500 ) {
									\$(td).css('color', 'red')
								} else if (val > 100) {
									\$(td).css('color', 'darkorange')
								}
								if (val > 0) {
									\$(td).append(`<div style="font-size: small;">count: \${rowData.detail.transmissionCount}</div>`)
								}
							}
							
						},
						{ data: 'metrics.std_dev', title: 'RTT StdDev', defaultContent: "n/a", searchPanes: {orthogonal: 'sp', controls: false},
							visible: ${settings?.addCols?.contains("rttStdDev")},
							render: function(data, type, row) {
								var val = data
								if (type === 'filter' || type === 'sp') {
									return ( (val == (undefined || '')) || val.toString() == 'NaN') ? 'unknown' : val < 50 ? '0-50ms' : val <= 500 ? '50-500ms' : val < 1000 ? '500-1000ms' : '> 1000ms'
								} else if (type === 'sort' || type === 'type') {
									return val.toString() == 'NaN' ? -2 : val < 0 ? -1 : val
								} else {
									return val >= 0 ? 
										`\${val} ms`
										: "unknown"
								}
							},
							createdCell: function (td, cellData, rowData, row, col) {
								var val = cellData
								var avg = parseInt(rowData.metrics["RTT Avg"].match(/(\\d*)ms/)[1])
								if ( val > (2 * avg) ) {
									\$(td).css('color', 'red')
								} else if (avg > 0 && val > avg ) {
									\$(td).css('color', 'darkorange')
								}
								
							}
							
						},

						{ data: 'metrics.LWR RSSI', title: 'LWR RSSI', defaultContent: "unknown", searchPanes: {show: false},
							visible: ${settings?.addCols?.contains("lwrRssi")},
							render: function(data, type, row) {
								var val = (data === '' ? '' : data.match(/([-0-9]*)dB/)[1])
								if (type === 'sort' || type === 'type') {
									return val
								} else {
									return val ? val + " dB" : 'unknown'
								}
							},
							createdCell: function (td, cellData, rowData, row, col) {
								var val = (cellData === '' ? '' : cellData.match(/([-0-9]*)dB/)[1])
								if ( val > 0 && val < 17) {
									\$(td).css('color', 'darkorange')
								} else if (val <= 0) {
									\$(td).css('color', 'red')
								}

							}

						},
						{ data: 'metrics.Neighbors', title: 'Neighbor<br/>Count', defaultContent: "n/a", 
							searchPanes: {show: false},
							createdCell: function (td, cellData, rowData, row, col) {
								if (cellData == 2) {
									\$(td).css('color', 'darkorange')
								} else if (cellData <= 1) {
									\$(td).css('color', 'red')
								}
								\$(td).addClass('neighbors-' + rowData.id2)
							}
						},
						{ data: 'metrics.Route Changes', title: 'Route<br/>Changes', defaultContent: "n/a", 
							searchPanes: {show: false},
							createdCell: function (td, cellData, rowData, row, col) {
								if (cellData > 1 && cellData <= 4) {
									\$(td).css('color', 'darkorange')
								} else if (cellData > 4) {
									\$(td).css('color', 'red')
								}
							}
						},
						{ data: 'metrics.PER', title: 'Error<br/>Count', defaultContent: "n/a", searchPands: {show: false}},
						{ data: 'deviceSecurity', title: 'Security', defaultContent: "Unknown", searchPanes: { controls: false},
							visible: ${settings?.addCols?.contains("security")}
						},
						{ data: 'routeHtml', title: 'Route<br/>(from&nbsp;Hub)' }
						// ,{data: 'metrics', title: 'Raw Stats', searchPanes: {show: false},
						// 	"render": function ( data, type, row ) {
						// 		return JSON.stringify(data);
						// 	}
						// }
					],
					"pageLength": -1,
					"rowId": 'id',
					"lengthChange": false,
					"paging": false,
					"dom": "Pftrip",
					"searchPanes": {
						layout: 'meshdetails-6',
						cascadePanes: true,
						order: ['Repeater', 'Status', 'Security', 'Connection Speed', 'RTT Avg', 'RTT StdDev']
					}
				});
				updateLoading('','');
			}).then(e => { 

				\$('#mainTable tbody').on('click', 'td.details-control', async function () {
						var tr = \$(this).closest('tr');
						var row = tableHandle.row( tr );
				
						if ( row.child.isShown() ) {
							row.child.hide();
							tr.removeClass('shown');
						}
						else {
							var rowData = row.data()
							if (rowData.commandClasses && rowData.commandClasses.length > 0) {
								row.child(displayRowDetail(row)).show();
								tr.addClass('shown');
							}
							else {
								var classData = await getZwaveDeviceInfo(rowData.hubDeviceId)
								var inClusters = classData.inClusters && classData.inClusters.length > 1 ? classData.inClusters.split(',') : []
								var outClusters = classData.outClusters && classData.outClusters.length > 1 ? classData.outClusters.split(',') : []
								var commandClasses = inClusters.concat(outClusters)
								rowData.commandClasses = commandClasses
								row.data(rowData)
								row.child(displayRowDetail(row)).show();
								tr.addClass('shown');
							}
						}
						
				} );
				// Fix width issue
				\$('input.dtsp-search').width('auto')

			})

			
		});
};
"""
	render contentType: "application/javascript", data: javaScript.replaceAll('\t','  ')
	
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	atomicState?.isInstalled = true
	initialize()
}

def updated() {
	log.trace ("${app?.getLabel()} | Now Running Updated() Method")
	if(!atomicState?.isInstalled) { atomicState?.isInstalled = true }
	initialize()
}

def initialize() {
	log.info "Endpoint: ${getAppLink('meshinfo')}"
}
 
def uninstalled() {
	 log.warn("${app?.getLabel()} has been Uninstalled...")
}

def getAccessToken() {
	try {
		if(!state?.accessToken) {
			log.warn "Access Token Not Found... Creating a New One!!!"
			def accessToken = createAccessToken()
			return accessToken
		} else {
			return state.accessToken 
		}
	}
	catch (e) {
		log.error "OAuth is not Enabled for ${app?.label}! Please Enable OAuth for the App (in Apps Code)"
		return false
	}
}

def gitBranch()         { return "beta" }

def getAppEndpointUrl(subPath)	{ return "${getFullLocalApiServerUrl()}${subPath ? "/${subPath}?access_token=${getAccessToken()}" : ""}" }

String getAppLink(String path) {
	String link = getAppEndpointUrl(path)
	link = removeHostFromURL(link)
	return link
}

String removeHostFromURL(String originalURL)
{
	URI uri = new URI(originalURL);
    return uri.getPath() + (uri.getQuery() ? '?' + uri.getQuery() : '');
}


String getLinkHost(String url) {
	URI uri = new URI(url);
	return uri.getHost()
}
