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
private releaseVer() { return "0.5.22-beta" }
private appVerDate() { return "2022-01-02" }
/**********************************************************************************************************************************************/
preferences {
	page name: "mainPage"
	page name: "devicesPage", nextPage: "mainPage"
}

mappings {
	path("/meshinfo") { action: [GET: "meshInfo"] }
	path("/script.js") { action: [GET: "scriptController"] }
	path("/zwaveUtils.js") { action: [GET: "zwaveUtilsController"]}
	path("/deviceDetails") { action: [GET: "deviceDetails"]}
	path("/remoteLog") { action: [POST: "remoteLog"]}
}

def devicesPage() {
	dynamicPage (name: "devicesPage", title: "", install: false, uninstall: false) {  
		section("") {
			input "deviceList", "capability.*", multiple: true, submitOnChange: true
		}
	}
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
		if (!permitDeviceAccess) {
			app.removeSetting("deviceList")
			state.hasInitializedDeviceList = false
		}
		if (deviceList && !state?.hasInitializedDeviceList) {
			state.hasInitializedDeviceList = true
		}

		if (embedStyle && settings?.linkStyle && settings?.linkStyle != 'embedded') {
			log.debug "Removing unused embedStyle"
			app.removeSetting('embedStyle')
		}

		if(settings?.linkStyle && !state?.hasInitializedCols) {
			if (enableDebug) log.debug "Resetting default column options"
			app.updateSetting("addCols", ["status","security","rttStdDev","lwrRssi","deviceType"])
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
				section("Display Mode") {
					paragraph "Choose if the Mesh Details webapp will open in a new window or stay in this window"
					input "linkStyle", "enum", title: "Link Style", required: true, submitOnChange: true, options: ["embedded":"Same Window", "external":"New Window"], image: ""
					if (settings?.linkStyle == 'embedded') {
						input "embedStyle", "enum", title: "Embed Style", required: true, submitOnChange: true, options: ["inline": "Display in App screen (experimental; alpha feature)", "fullscreen": "Display fullscreen (<b>default</b>)"], defaultValue: "fullscreen"
					}
				}
				section("") {
					String meshInfoLink = getAppLink("meshinfo")

					if(settings?.linkStyle) {
						getAddColsInput()
						if (settings?.linkStyle == 'embedded' && !settings?.embedStyle) {
							paragraph title: "Select Embed Style", "Please Select a Embed style to proceed"
						} else {

							if (settings?.linkStyle == 'external' || settings?.embedStyle == 'fullscreen') {
								href "", title: "Mesh Details", url: meshInfoLink, style: (settings?.linkStyle == "external" ? "external" : "embedded"), required: false, description: "Tap Here to load the Mesh Details Web App", image: ""
							} else { // Inline
								paragraph title: "script", """
									<script id="firstStatsScript">
									var scriptLoaded;
									async function loadMainScript() {
										if (!scriptLoaded) {
											await \$.getScript('${getAppLink("zwaveUtils.js")}')
											await \$.getScript('${getAppLink("script.js")}')
											scriptLoaded=true;
										};
									}
									\$(document).ready( function() {
										//console.log("ready");
										\$('#firstStatsScript').parent().hide()
										var btn = \$("span:contains('Show Mesh Details')").parent()
										var docURI = btn.attr('href')
										btn.removeAttr('href')
										btn.click(async function() { 
											await loadMainScript()
											await loadScripts()
											await loadAxios()
											await loadApp(docURI)
											await doWork()
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
					input "stateSave", "bool", title: "Save Table State (experimental)", defaultValue: false, submitOnChange: true
					paragraph "NOTE: Granting access will immediately add all z-wave devices on the hub to this app for advanced data collection"
					input "permitDeviceAccess", "bool", title: "Grant access to z-wave devices (experimental)", defaultValue: false, submitOnChange: true

					input "enableDebug", "bool", title: "Enable debug logs", defaultValue: false, submitOnChange: false
					input "deviceLinks", "bool", title: "Enable device links", defaultValue: false, submitOnChange: true
					input "nodeBase", "enum", title: "Display nodes as hex or base10?", multiple: false, options: ["base16": "base16 (default)", "base10":"base10"], submitOnChange: true

					paragraph "<hr/>"

					input "resetSettings", "bool", title: "Force app settings reset", submitOnChange: true
				}
				if (permitDeviceAccess && !settings.deviceList && !state.hasInitializedDeviceList) {
					section() {
						log.info("Initializing devicesList with z-wave devices")
						paragraph title: "initDevicesScript", """
										<script id="addDevices">
										async function loadUtilScript() {
												await \$.getScript('${getAppLink("zwaveUtils.js")}')
										}
										\$(document).ready( async function() {
											//console.log("ready");
											await loadUtilScript()
											await refreshDevicesList()
											jsonSubmit(null,null,false)
										})
										</script>
						"""
					}
				}
				if (permitDeviceAccess) {
					section("Show authorized devices", hideable: true, hidden:true) {
						paragraph getDeviceListHtml()
					}
				}
			} else {
				section("") {
					paragraph title: "Click Done", "Please click Done to install app before continuing"
				}
			}
		}
	}
}

private def getDeviceListHtml() {
	def initHtml = """<span style="float:right;"><a href="" onclick="jsonSubmit('_action_href_name|devicesPage|2',null); return false">(edit)</a></span>"""
	def results = deviceList.inject(initHtml, { r, dev -> 
			def id = dev.id
			def name = dev.getDisplayName()
			r += (name + "<br>")
	})
	if (!deviceList) { results = initHtml + "NO Devices Authorized"}
	return results;
}

private def getAddColsInput() {
	def colOptions = ["status": "Status","security":"Security Mode","rttStdDev":"RTT Std Dev","lwrRssi":"LWR RSSI", "deviceType": "Device Type", "deviceManufacturer": "Device Manufacturer", "routingCount": "RoutingFor Count"]
	if (settings?.permitDeviceAccess) {
		colOptions.put("lastActive", "Last Active Time")
		colOptions.put("beaming", "Is Beaming?")
		colOptions.put("listening", "Is Listening?")
	}
	input "addCols", "enum", title: "Select additional columns to display", multiple: true, options: colOptions, submitOnChange: true
}

def remoteLog() {
	def data = request.JSON
	if (data.level == "debug") {
		log.debug(data.log)
	}
	else if (data.level == "info") {
		log.info(data.log)
	}
	else if (data.level == "error") {
		log.error(data.log)
	} else {
		log.error("Received bad message for logging")
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
	app.removeSetting("stateSave")
	app.removeSetting("deviceList")
	app.removeSetting("permitDeviceAccess")
	state.remove('hasInitializedCols')
	state.remove('hasInitializedDeviceList')
}

def resetHostOverride() {
	if (enableDebug) log.debug "Resetting hostOverride"
	app.removeSetting("hostOverride")
	app.removeSetting("resetHost")
}

import org.codehaus.groovy.runtime.EncodingGroovyMethods

def deviceDetails() {
	def results = [:]
	results = deviceList.inject([:], { r, dev -> 
			def id = dev.id
			def lastActiveStr = dev.getLastActivity()
			def zwaveData = dev.getDataValue("zwNodeInfo")
			if (!zwaveData) {
				log.warn("${dev.getDisplayName()} has no zwNodeInfo. (Not a z-wave device?)")
				return r;
			}
			def inCC = dev.getDataValue("inClusters")
			def inCCSec = dev.getDataValue("secureInClusters")
			def zwaveBytes = zwaveData.split(" ")
			def zwaveDataLen = zwaveBytes.length

			def nifBytes = []
			def listening = false
			def routing = false
			def maxSpeed = -1
			def speedBits = "?"
			def rountingSlave = false
			def flirs250 = false
			def flirs10000 = false
			def flirs = false
			def extraSpeed
			def inCCList = []
			def inCCSecList = []
			def zwavePlus = false
			if (zwaveDataLen > 0) {
				def rawBytes = EncodingGroovyMethods.decodeHex(zwaveBytes.join())
				nifBytes = rawBytes.collect {it -> String.format("%8s", Integer.toBinaryString(it & 0xFF)).replace(" ", "0") }
				def capHex = zwaveBytes[0]
				listening = (Integer.parseInt(zwaveBytes[0], 16) & 0x80) ? true : false
				routing = (Integer.parseInt(zwaveBytes[0], 16) & 0x40) ? true : false
				flirs1000 = (Integer.parseInt(zwaveBytes[1], 16) & 0x40)
				flirs250 = (Integer.parseInt(zwaveBytes[1], 16) & 0x20)
				flirs = (flirs250 | flirs1000) ? true : false
				beaming = (Integer.parseInt(zwaveBytes[1], 16) & 0x10) ? true : false
				routingSlave = (Integer.parseInt(zwaveBytes[1], 16) & 0x08) ? true : false
				speedBits = nifBytes[0].substring(2,5)
				maxSpeed = Integer.parseInt(speedBits,2)
				extraSpeed = (Integer.parseInt(zwaveBytes[2], 16) & 0x01) ? true : false

				inCCList = inCC ? inCC.split(',') : []
				inCCSecList = inCCSec ? inCCSec.split(',') : []
				zwavePlus = (inCCList.contains('0x5E')) ? true : false
			}
			r.put(id, [
				name: dev.getDisplayName(),
				//data: dev.getData(),
				listening: listening,
				beaming: beaming,
				routing: routing,
				routingSlave: routingSlave,
				maxSpeed: maxSpeed,
				extraSpeed: extraSpeed,
				flirs: flirs,
				flirs250: flirs250,
				flirs1000: flirs1000,
				//nifBytes: nifBytes,
				speedBits: speedBits,
				status: dev.getStatus(),
				lastActive: lastActiveStr,
				zwaveData: zwaveData,
				//zwaveBytes: zwaveBytes,
				zwaveDataLen: zwaveDataLen,
				inCC: inCCList,
				inCCSec: inCCSecList,
				zwavePlus: zwavePlus,
				lastActiveTS: Date.parse("yyy-MM-dd HH:mm:ssZ","$lastActiveStr".replace("+00:00","+0000")).getTime()
			])
			r
		}
	)
	results.now=(new Date()).getTime()
	renderJson(results)

}

import groovy.json.JsonOutput
def renderJson(obj)
{
	render contentType: 'application/json', data: JsonOutput.toJson(obj)
}


def meshInfo() {
	def html = """
<!DOCTYPE html>
<html lang="en">
<head>
<title>Hubitat Z-Wave Mesh Details</title>
<link rel="stylesheet" type="text/css" href="/ui2/css/styles.min.css">
<link rel="stylesheet" type="text/css" href="https://cdn.datatables.net/v/dt/dt-1.11.3/cr-1.5.5/fh-3.2.1/r-2.2.9/sp-1.4.0/sl-1.3.4/datatables.min.css"/>


<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js"></script>
<script src="${getAppLink("zwaveUtils.js")}"></script>
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


.dtsp-searchPanes .even:not(.selected) {
	background-color: #ffffff !important;
}

.tooltip {
  position: relative
}
.tooltip .tooltiptext {
  visibility: hidden;
  display: inline-block;
  width: 100px;
  background-color: black;
  color: #fff;

  text-align: center;
  padding: 5px 0;
  border-radius: 6px;
}

.tooltip .tooltiptexttop {
  /* Position the tooltip text - see examples below! */
  position: absolute;
  z-index: 1;
  bottom: 100%;
  left: 50%;
  margin-left: -50px;
}

.tooltip .tooltiptextright {
  /* Position the tooltip text - see examples below! */
  position: absolute;
  z-index: 1;
  top: -5px;
  left: 105%;
}

/* Show the tooltip text when you mouse over the tooltip container */
.tooltip:hover .tooltiptext {
  visibility: visible;
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
	<button id="hideNonRepeatersBtn" onclick="hideNonRepeaters()" data-toggle="modal">Hide NonRepeaters</button>
	<button id="showNonRepeatersBtn" onclick="showAllTopology()" style="display:none;" data-toggle="modal">Show All</button>
	<button id="refreshRepeatersBtn" onclick="getTopologyModal()" data-toggle="modal">Refresh</button>
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

def zwaveUtilsController() {
	def javaScript = """

function loadAxios() {
	return \$.getScript('https://unpkg.com/axios/dist/axios.min.js', function() {
		console.log("axios loaded")
	});
}

async function refreshDevicesList() {
		var devList = await getZwaveList()
		if (!window.axios) {
			await loadAxios()
		}
		console.log("Collecting zwave device ids")
		var deviceIds = devList.reduce( (acc, val) => {
			acc.push(val.hubDeviceId); return acc;
		}, [])
		if ($enableDebug) {
			console.log(deviceIds)
		}
		if ($enableDebug) {
			var m = `Setting deviceList from zwave list: \${deviceIds.length}`
			console.log(m)
			hubLog("debug", m)
		}
		return updateDevicesInApp(deviceIds)
}

// Get transformed list of devices (see transformDevice) from hubitat zwave details webpage
async function getZwaveList() {
	if (!window.axios) {
		await loadAxios()
	}

	const instance = axios.create({
		timeout: 5000,
		responseType: "text" // iOS seems to fail (reason unknown) with document here
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
	.catch(error => { 
		console.error(error);
		updateLoading("Error", error);
		hubLog("error", `zwaveInfo: Error getting zwave Info: \${error}`)
	} );
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
	var routersForDisplay = []
	var routersList = []
	
	var connectionSpeed = "Unknown"
	if (routers.length > 0) {
		var lastParts = routers.splice(-1,1) // Remove Last element (this device w/ speed)
		routers.splice(0,1) // Remove first element (always 01; hub)
		connectionSpeed = lastParts[0].split(' ')[1]
		routersList = routers
		routersForDisplay = routers.map(r => useHex() ? "0x" + r : parseInt("0x"+r))
	}

	if (routers.length == 0 && connectionSpeed != '') {
		routersForDisplay = ['DIRECT']
	}

	var nodeText = childrenData[0].innerText.trim()

	var devId = (nodeText.match(/0x([^ ]+) /))[1]
	var devIdDec = (nodeText.match(/\\(([0-9]+)\\)/))[1]
	var devId2 = parseInt("0x"+devId)

	var label = ""
	var deviceLink = ""
	var hubDeviceId = null
	if (childrenData[4].innerText.trim() != '') {
		label = childrenData[4].innerText.trim()
		deviceLink = childrenData[4].firstElementChild.getAttribute('href')
		hubDeviceId = deviceLink.split('/')[3]
	}

	var typeParts = childrenData[3].innerHTML.split("<br>")
	if (typeParts && typeParts.length >= 2) {
		var type = translateDeviceType(typeParts[0])
		var manufacturer = typeParts[1]
	}

	var deviceData = {
		id: devId,
		id2: devId2,
		devIdDec: devIdDec,
		node: nodeText.replace(' ', '&nbsp;'),
		metrics: statMap,
		routers: routersForDisplay,
		routersList: routersList,
		label: label,
		type: type,
		manufacturer: manufacturer,
		deviceLink: deviceLink,
		hubDeviceId: hubDeviceId,
		deviceSecurity: childrenData[5].innerText.trim(),
		routeHtml: routersForDisplay.reduce( (acc, v, i) => (v == 'DIRECT') ? v : acc + ` -> \${v}`, "") + (routersForDisplay[0] == 'DIRECT' ? '' : ` -> \${useHex() ? "0x" + devId : devId2}`) ,
		deviceStatus: childrenData[2].firstChild.data.trim(),
		connection: connectionSpeed
	}
	return deviceData
}

function updateDevicesInApp(devices) {
	var updateLink = "/installedapp/update/json"
	var appLink = "${getAppLink()}"
	var appId = "${getAppId()}"

	const instance = axios.create({
		timeout: 5000,
		config: {headers: {"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"}}
	});

	var postData = {

		"settings[deviceList]": devices.join(','),
		formAction: "update",
		id: appId,
		version: 2,
		appTypeId: '',
		appTypeName: '',
		currentPage: 'devicesPage',
		// pageBreadcrumbs: '%5B%5D',
		"deviceList.type": 'capability.*',
		"deviceList.multiple": 'true',
		deviceList: 'deviceList'
		// referrer: '',
		// url: `/installedapp/configure/\${appId}/devicesPage`

	}

	if ($enableDebug) {
		console.log("Sending deviceList update")
		console.log(postData)
	}

	return instance
		.post(updateLink, serializeToURL(postData))
}

function serializeToURL( obj ) {
	let str = Object.keys(obj).reduce(function(a, k){
		a.push(k + '=' + encodeURIComponent(obj[k]));
		return a;
	}, []).join('&');
	return str;
}


function translateDeviceType(deviceType) {
	switch (deviceType) {
		case "BASIC_TYPE_CONTROLLER": // 0x00
			return "Basic Controler"
		case "BASIC_TYPE_STATIC_CONTROLLER": // 0x03
			return "Basic Static Controller"
		case "BASIC_TYPE_SLAVE": // 0x03
			return "Basic Slave"
		case "BASIC_TYPE_ROUTING_SLAVE": // 0x04
			return "Basic Routing Slave"

		case "GENERIC_TYPE_AV_CONTROL_POINT": // 0x03
			return "AV Control"
		case "SPECIFIC_TYPE_DOORBELL":
			return "Doorbell"
		case "SPECIFIC_TYPE_SATELLITE_RECEIVER":
			return "Satellite Receiver"
		case "SPECIFIC_TYPE_SATELLITE_RECEIVER_V2":
			return "Satellite Receiver V2"
		case "SPECIFIC_TYPE_SOUND_SWITCH":
			return "Sound Switch"

		case "GENERIC_TYPE_DISPLAY": // 0x04
			return "Display"
		case "SPECIFIC_TYPE_SIMPLE_DISPLAY":
			return "Simple Display"

		case "GENERIC_TYPE_ENTRY_CONTROL": // 0x40
			return "Entry Control"
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

		case "GENERIC_TYPE_GENERIC_CONTROLLER": // 0x01
			return "Generic Controller"
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

		case "GENERIC_TYPE_METER": // 0x31
			return "Generic Meter"
		case "SPECIFIC_TYPE_SIMPLE_METER":
			return "Simple Meter"
		case "SPECIFIC_TYPE_ADV_ENERGY_CONTROL":
			return "Adv Energy Control"
		case "SPECIFIC_TYPE_WHOLE_HOME_METER_SIMPLE":
			return "Whole Home Meter Simple"

		case "GENERIC_TYPE_METER_PULSE": // 0x30
			return "Generic Meter Pulse"

		case "GENERIC_TYPE_REPEATER_SLAVE": //0x0F
			return "Repeater Slave"
		case "SPECIFIC_TYPE_REPEATER_SLAVE":
			return "Repeater Slave"
		case "SPECIFIC_TYPE_VIRTUAL_NODE":
			return "Virtual Node"

		case "GENERIC_TYPE_SECURITY_PANEL": // 0x17
			return "Security Panel"
		case "SPECIFIC_TYPE_ZONED_SECURITY_PANEL":
			return "Zoned Security Panel"

		case "GENERIC_TYPE_SEMI_INTEROPERABLE": // 0x50
			return "Semi Interoperable"
		case "SPECIFIC_TYPE_ENERGY_PRODUCTION":
			return "Energy Production"

		case "GENERIC_TYPE_SENSOR_ALARM": // 0xA1
			return "Alarm Sensor"
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

		case "GENERIC_TYPE_SENSOR_BINARY": // 0x20
			return "Binary Sensor"
		case "SPECIFIC_TYPE_ROUTING_SENSOR_BINARY":
			return "Routing Sensor Binary"

		case "GENERIC_TYPE_SENSOR_MULTILEVEL": // 0x21
			return "Sensor Multilevel"
		case "SPECIFIC_TYPE_ROUTING_SENSOR_MULTILEVEL":
			return "Routing Sensor Multilevel"
		case "SPECIFIC_TYPE_CHIMNEY_FAN":
			return "Chimney Fan"
		
		case "GENERIC_TYPE_STATIC_CONTROLLER": // 0x02
			return "Static Controller"
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

		case "GENERIC_TYPE_SWITCH_BINARY": // 0x10
			return "Switch Binary"
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

		case "GENERIC_TYPE_SWITCH_MULTILEVEL": // 0x11
			return "Switch Multilevel"
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

		case "GENERIC_TYPE_SWITCH_REMOTE": // 0x12
			return "Switch Remote"
		case "SPECIFIC_TYPE_SWITCH_REMOTE_BINARY":
			return "Switch Remote Binary"
		case "SPECIFIC_TYPE_SWITCH_REMOTE_MULTILEVEL":
			return "Switch Remote Multilevel"
		case "SPECIFIC_TYPE_SWITCH_REMOTE_TOGGLE_BINARY":
			return "Switch Remote Toggle Binary"
		case "SPECIFIC_TYPE_SWITCH_REMOTE_TOGGLE_MULTILEVEL":
			return "Switch Remote Toggle Multilevel"

		case "GENERIC_TYPE_SWITCH_TOGGLE": // 0x13
			return "Switch Toggle"
		case "SPECIFIC_TYPE_SWITCH_TOGGLE_BINARY":
			return "Switch Toggle Binary"
		case "SPECIFIC_TYPE_SWITCH_TOGGLE_MULTILEVEL":
			return "Switch Toggle Multilevel"

		case "GENERIC_TYPE_THERMOSTAT": // 0x08
			return "Thermostat"
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

		case "GENERIC_TYPE_VENTILATION": // 0x16
			return "Ventilation"
		case "SPECIFIC_TYPE_RESIDENTIAL_HRV":
			return "Residential Hrv"

		case "GENERIC_TYPE_WINDOW_COVERING": // 0x09
			return "Window Covering"
		case "SPECIFIC_TYPE_SIMPLE_WINDOW_COVERING":
			return "Simple Window Covering"

		case "GENERIC_TYPE_ZIP_NODE": // 0x15
			return "Zip Node"
		case "SPECIFIC_TYPE_ZIP_ADV_NODE":
			return "Zip Adv Node"
		case "SPECIFIC_TYPE_ZIP_TUN_NODE":
			return "Zip Tun Node"

		case "GENERIC_TYPE_WALL_CONTROLLER": // 0x18
			return "Wall Controller"
		case "SPECIFIC_TYPE_BASIC_WALL_CONTROLLER":
			return "Basic Wall Controller"

		case "GENERIC_TYPE_NETWORK_EXTENDER": // 0x05
			return "Network Extender"
		case "SPECIFIC_TYPE_SECURE_EXTENDER":
			return "Secure Extender"

		case "GENERIC_TYPE_APPLIANCE": // 0x06
			return "Applicance"
		case "SPECIFIC_TYPE_GENERAL_APPLIANCE":
			return "General Appliance"
		case "SPECIFIC_TYPE_KITCHEN_APPLIANCE":
			return "Kitchen Appliance"
		case "SPECIFIC_TYPE_LAUNDRY_APPLIANCE":
			return "Laundry Appliance"

		case "GENERIC_TYPE_SENSOR_NOTIFICATION": // 0x07
			return "Notification Sensor"
		case "SPECIFIC_TYPE_NOTIFICATION_SENSOR":
			return "Notification Sensor"

		default:
			return deviceType
	}
}


function hubLog(level,log) {
  if (window.axios) {
	const instance = axios.create({
		timeout: 5000
	});

	return instance
	.post("${getAppLink("remoteLog")}", { level: level, log: log})
  }
}

function updateLoading(msg1, msg2) {
	\$('#loading1').text(msg1);
	\$('#loading2').text(msg2);
	if ($enableDebug) {
		if (msg1 || msg2) {
			hubLog("debug", `\${msg1} - \${msg2}`)
		}
	}
}


function useHex() {
	return "${settings?.nodeBase}" === "base16"
}

function hasDeviceAccess() {
	return ${settings.permitDeviceAccess}
}


	"""

	render contentType: "application/javascript", data: javaScript.replaceAll('\t','  ')
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
		0x79: "Sound Switch",
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

function loadScripts() {
	\$.get('/ui2/js/hubitat.min.js')
	updateLoading('Loading...','Getting script sources');

	return \$.getScript('https://cdn.datatables.net/v/dt/dt-1.11.3/cr-1.5.5/fh-3.2.1/r-2.2.9/sp-1.4.0/sl-1.3.4/datatables.min.js')
	.then(s => {

		function numberSort(a,b) {
			var token1a = a.split('-',2)[0].trim()
			var token1b = b.split('-',2)[0].trim()
			var vala = parseInt(token1a)
			var valb = parseInt(token1b)
			if (!vala && vala !== 0) return 1;
			if (!valb && vala !== 0) return -1;
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
}


// Get data from zwaveNodeDetail endpoint (built-in)
function getZwaveNodeDetail() {
	const instance = axios.create({
		timeout: 5000
	});

	return instance
	.get('/hub/zwaveNodeDetail')
	.then(response => {
		//if ($enableDebug) console.log (`Response: \${JSON.stringify(response)}`)

		return response.data
	})
	.catch(error => { 
		console.error(error);
		updateLoading("Error", error);
		hubLog("error", `zwaveNodeDetail: Error getting zwave details: \${error}`)
	} );
}

// Get details from devices app endpoint and merge into devList
function getDeviceDetails() {
	const instance = axios.create({
		timeout: 5000
	});

	return instance
	.get('${getAppLink("deviceDetails")}')
	.then(response => {
		//if ($enableDebug) console.log (`Response: \${JSON.stringify(response)}`)

		return response.data
	})
	.catch(error => { 
		console.error(error);
		updateLoading("Error", error);
		hubLog("error", `zwaveNodeDetail: Error getting zwave details: \${error}`)
	} );
}

async function getData() {

	var devList = await getZwaveList()

	var fullNameMap = devList.reduce( (acc,val) => {
						 acc[useHex() ? `0x\${val.id}` : val.id2]= `\${useHex() ? `0x\${val.id}` : val.id2} - \${val.label}`;
						 return acc;
					 }, {});


	// Build routersFor map
	var routersFor = devList.reduce( (acc, val) => {
		var myRouters = val.routersList
		var fullName = fullNameMap[useHex() ? `0x\${val.id}` : val.id2]
		myRouters.map(r => {
			//console.log(`\${r} is a router for \${fullName}`)
			if (!acc.has(r)) {
				acc.set(r, [])
			}
			l = acc.get(r)
			l.push(fullName)
		})
		return acc
	}, new Map())
	// Pseudo entry for direct-connected devices
	fullNameMap.DIRECT = 'DIRECT'

	updateLoading('Loading.','Getting device detail');
	var nodeDetails = await getZwaveNodeDetail()

	updateLoading('Loading..', 'Building Neighbors Lists')
	buildNeighborsLists(fullNameMap, nodeDetails)

	var deviceDetails = {}
	if (hasDeviceAccess()) {
		deviceDetails = await getDeviceDetails()
		var missingNonRepeaters = devList.reduce( (acc, val) => {
			var hubId = val.hubDeviceId.toString()
			var zwId = val.id2.toString()
			var detail = deviceDetails[val.hubDeviceId.toString()]
			if (detail && detail.listening === false && !nonRepeaters.has(zwId)) {
				acc.push(zwId)
			}
			return acc
		}, [])

		if (missingNonRepeaters.length > 0) {
			hubLog("info", "Non-listening devices missing: Adding to nonRepeaters: " + missingNonRepeaters.toString())
			missingNonRepeaters.forEach(item => nonRepeaters.add(item))
		}
	}

	var tableContent = devList.map( dev => {
		var routersFull = dev.routers.map(router => fullNameMap[router] || `\${router} - UNKNOWN`)
		var detail = nodeDetails[dev.id2.toString()]
		var devDetail
		
		if (dev.hubDeviceId && hasDeviceAccess()) {
			devDetail = deviceDetails[dev.hubDeviceId.toString()]
			if (devDetail) {
				dev.commandClasses = devDetail.inCC.concat(devDetail.inCCSec)
			}
		}

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
		return {...dev, 'routerOf': routersFor.get(dev.id), 'routersFull': routersFull, 'detail': detail, 'devDetail': devDetail}
	})

	return tableContent
}

var deviceDetailsMap = new Map() // cache/memoize data for each device (deviceId => map)

// Get data from device settings screen if we can't get it somewhere else
function getDeviceInfo(devId) {
	console.log("Getting Device Detail for " + devId)
	if (!devId) {
		hubLog("info", "No hub device for " + devId);
		return Promise.resolve({});
	}
	if (deviceDetailsMap.has(devId)) {
		// console.log("Returning details for " + devId + " from cache")
		return Promise.resolve(deviceDetailsMap.get(devId))
	}
	const instance = axios.create({
		timeout: 5000,
		responseType: "text" // iOS seems to fail (reason unknown) with document here
		});
	return instance
	.get('/device/edit/' + devId)
	.then(response => {
		var doc = new jQuery(response.data)
		var deviceData = doc.find('#data-label ~ td li')
		var details = {}
		deviceData.map (
			(index,row) => {
				var kvp = row.innerText.split(":")
				details[kvp[0].trim()] = kvp[1].trim()
			}
		)
		deviceDetailsMap.set(devId, details)
		return details
	})
	.catch(error => { console.error(error); 
		hubLog("error", `Error getting device detail: \${error}`)
	} );
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
			var hasNonHubNeighbor = false;
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

					if (!hasNonHubNeighbor && parseInt(neighborId) > 5) {
						hasNonHubNeighbor = true
					}

			})

			if (!hasNonHubNeighbor) {
				hubLog("debug", "No neighbors: Adding to nonRepeaters: " + devId)
				nonRepeaters.add(devId)
			}
		}
	})
}


function displayRowDetail(row) {
	var devId = row.id()
	var neighborList = []
	var deviceData = tableContent.find( row => row.id == devId)
	var data = row.data()

  return getDeviceInfo(data.hubDeviceId).then(detailData => {
	// On demand data
	if (!data.commandClasses) {
		var inClusters = detailData.inClusters && detailData.inClusters.length > 1 ? detailData.inClusters.split(',') : []
		var secureInClusters = detailData.secureInClusters && detailData.secureInClusters.length > 1 ? detailData.secureInClusters.split(',') : []
		var commandClasses = inClusters.concat(secureInClusters)
		// Update data
		console.log("Command classes is: " + commandClasses)
		data.commandClasses = commandClasses
	}
	var html = '<div><table>'

	// Header Row
	html += '<tr>'
	html += '<th>Repeaters</th>'
	if (deviceData.routerOf && deviceData.routerOf.length > 0) {
		html+= '<th>Routing For</th>'
	}
	html += '<th>Neighbors</th><th>NeighborOf</th>'

	if (data.commandClasses && data.commandClasses.length > 0) {
		html += '<th>Command Classes</th>'
	}

	html += '<th>Actions</th>'
	html += '</tr>'
	// End Header Row

	html += '<tr>'
	// Repeaters
	html += '<td style="vertical-align: top;">'
	html += deviceData.routersFull.join('<br/>')
	html += '</td>'

	// 	RoutingFor
	if (deviceData.routerOf && deviceData.routerOf.length > 0) {
		html += '<td style="vertical-align: top;">'
		html += deviceData.routerOf.join('<br/>')
		html += '</td>'		
	}

	// Neighbors
	html += '<td style="vertical-align: top;">'
	var neighborListStyle = "list-style-type:none;margin:0;padding:0"
	var neighborList = neighborsMap.get(deviceData.id2.toString())
	var neighborOfList = neighborsMapReverse.get(deviceData.id2.toString())
	if (neighborList && neighborList.length > 0) {
		html += `<ul style="\${neighborListStyle}">`
		neighborList.forEach( (neighborId) => {
			var symetry = false
			if (neighborOfList && neighborOfList.includes(neighborId)) {
				symetry = true
			}
			var color
			if (!symetry) { color = "orange"}
			html += `<li \${color ? `style="color:\${color}"` : ""}>`
			if (neighborId < 6) {
				html += useHex() ? '0x0' : '' // 0-pad for hex value
				html += `\${neighborId} - HUB`
			} else {
				var deviceData = findDeviceByDecId(neighborId)
				html += useHex() ? `0x\${deviceData.id}` : deviceData.id2
				html += ` - \${deviceData.label}`
				if (nonRepeaters.has(deviceData.id2.toString())) {
					html += '<sup style="vertical-align:text-top;">*</sup>'
				}
				// TODO: If neighborId is a router
			}
			html += '</li>'
		})
		html += '</ul>'
	}
	html += '</td>'
	// NeighborOf
	html += '<td style="vertical-align: top;">'
	if (neighborOfList && neighborOfList.length > 0) {
		html += `<ul style="\${neighborListStyle}">`
		neighborOfList.forEach( (neighborId) => {
			var symetry = false
			if (neighborList && neighborList.includes(neighborId)) {
				symetry = true
			}
			var color
			if (!symetry) { color = "orange"}
			html += `<li \${color ? `style="color:\${color}"` : ""}>`
			if (neighborId < 6) {
				html += useHex() ? '0x' : ''
				html += `\${neighborId} - HUB`
			} else {
				var deviceData = findDeviceByDecId(neighborId)
				html += useHex() ? `0x\${deviceData.id}` : deviceData.id2
				html += ` - \${deviceData.label}`

				if (nonRepeaters.has(deviceData.id2.toString())) {
					html += '<sup style="vertical-align:text-top;">*</sup>'
				}
				// TODO: If deviceData.id is a router for neighborId
			}
			html += '</li>'
		})
		html += '</ul>'
	}
	html += '</td>'

	// Command Classes
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

	if (data.commandClasses && !data.commandClasses.includes('0x84')) {
		html += `<button onclick="zwaveNodeRepair(\${data.id2})" class="btn btn-danger btn-nodeDetail">Repair</button>`
	}
	html += '</td>'

	html += '</tr></table>'
	html += '<p><sup style="vertical-align:text-top;">*</sup>Device is a non-repeater</p>'
	html += '</div>'
	return html
  })

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


function labelTopologyHeads(sel, ttClass) {
	sel.each( (i, data) => {
		var td = \$(data)
		var str = data.innerHTML
		//console.log(str)
		if (str.match(/[A-F0-9]/)) {
			if (str == "01") {
				td.prop("aria-label","HUB")
				td.addClass("tooltip")
				td.append(`<span class="tooltiptext \${ttClass}">HUB</span>`)
			} else {
				var d = findDeviceByHexId(str)
				if (d != null) {
					td.prop("aria-label", d.label)
					td.addClass("tooltip")
					td.append(`<span class="tooltiptext \${ttClass}">\${d.label}</span>`)
				}
			}
		} 

	}) 
}

function labelTopologyCells(index, row, labels, ttClass) {
 row.find('td:nth-child(n+2)').each( (i, o) => {
	 var seen = "not seen";
	 if (o.bgColor == 'white') return;
	 if (o.bgColor == 'blue') seen = "seen";
	 var myLabel = labels[index]
	 var dstLabel = labels[i]
	 var td = \$(o)
	 td.prop("aria-label", myLabel + " -> " + dstLabel + ":" + seen)
	 td.addClass("tooltip")
	 td.append(`<span class="tooltiptext \${ttClass}">\${myLabel + " -> " + dstLabel + ":" + seen}</span>`)

 })
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
			// Insert tooltips
			var topr = \$('#topologyDialog table tbody tr:nth-child(1) td:nth-child(n+2)')
			var c1 = \$('#topologyDialog table tbody tr:nth-child(n+1) td:nth-child(1)')
			
			var deviceHexIds = topr.map( function() { return this.innerHTML})
			var deviceLabels = deviceHexIds.map( (i,o) => { if (o === '01') {return "HUB" } else return findDeviceByHexId(o).label })

			labelTopologyHeads(topr, "tooltiptexttop")
			labelTopologyHeads(c1, "tooltiptextright")

			var tRows = \$('#topologyDialog table tbody tr:nth-child(n+2)')
			tRows.each ( (i,row) => {
				labelTopologyCells(i,\$(row),deviceLabels, "tooltiptexttop")
			})
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

function showAllTopology() {
	\$('#topologyDialog table tbody tr td').show()
	\$('#topologyDialog table tbody tr').show()
	\$('#hideNonRepeatersBtn').show()
	\$('#showNonRepeatersBtn').hide()
}

function hideNonRepeaters() {
	topr = \$('#topologyDialog table tbody tr:nth-child(1)') // Get the top row with nodes (hex starting in position 2)
	rowItems = topr[0].innerText.split(/\\s+/) // Split into a list
	rowItems.slice(2).forEach( (item,index) => {
		if(item.match(/[A-F0-9]/)) {
			\$('#hideNonRepeatersBtn').hide()
			\$('#showNonRepeatersBtn').show()
			if ($enableDebug) console.log(`Testing \${item}`)
			const d = findDeviceByHexId(item)

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

// For embeded mode, load the app into the app screen
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

		// Hide current page content and add/show the fetched doc
		var c = doc.find('body').children()
		\$('main > :first-child').children().hide()
		\$('main > :first-child').append(c)

		var currentPage = \$('#currentPage').val()
		history.pushState({currentPage: currentPage, previousPage: null, statsLoaded: true, appURI: appURI}, "View Hub Stats", "?page=view&debug=true")
	})
	.catch(error => { console.error(error); updateLoading("Error", error);} );
}

window.onpopstate = function(event) {
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

function searchPanesList() {
	var panes = ['Repeater', 'Status', 'Security', 'Connection Speed', 'RTT Avg', 'RTT StdDev', 'Device Type', 'Manufacturer']
	if (hasDeviceAccess) {
		panes.push('listening')
		panes.push('Beaming')
		panes.push('FLiRS')
		panes.push('Z-Wave Plus')
	}
	return panes
}

function doWork() {
		return loadScripts().then(function() {
			hubLog("info", "UserAgent: " + navigator.userAgent)
			updateLoading('Loading..','Getting device data');
			return getData().then( r => {
				// console.log(list)
				tableContent = r;
				sendDebugData()

				// Setup State handler
				\$('#mainTable').on('requestChild.dt', async function(e, row) {
					if (row.data().hubDeviceId != '') {
						var content = await displayRowDetail(row)
						row.child(content).show();
					}
				} );

				updateLoading('Loading..','Creating table');
				var idCol = useHex() ? 'id' : 'id2';
				tableHandle = \$('#mainTable').DataTable({
					data: tableContent,
					rowId: 'id2',
					stateSave: ${settings?.stateSave},
					order: [[1,'asc']],
					columns: [
						//{ data: 'networkType', title: 'Type', searchPanes: { preSelect:['ZWAVE','ZIGBEE']} },
						{
							"className": 'details-control',
							"orderable": false,
							"data": null,
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
						{ data: 'label', title: 'Device name', 
							render: function(data, type, row) {
								if (type === 'display') {
									if (!data) {
										return "NO DEVICE FOUND"
									}
								}
								return data
							},
							createdCell: function (td, cellData, rowData, row, col) {
								if ($deviceLinks == true && rowData.deviceLink){
									\$(td).wrapInner(`<a href="\${rowData.deviceLink}">`)
								}
								if (cellData == "") {
									\$(td).css('color', 'red')
								}
							}
						},
 						{ data: 'type', title: 'Device Type', defaultContent: "!NO DEVICE!",
							visible: ${settings?.addCols?.contains("deviceType")},
							searchPanes: {controls: false}
						 },
						{ data: 'manufacturer', title: 'Manufacturer', defaultContent: "!NO DEVICE!",
							visible: ${settings?.addCols?.contains("deviceManufacturer")},
							searchPanes: {controls: false}
						},
						{ data: 'routersFull', title: 'Repeater', visible: false,
							render: {'_':'[, ]', sp: '[]'},
							defaultContent: "None",
							searchPanes : { orthogonal: 'sp', controls: false },
							type: 'initialNumber'
						},
						{ data: 'connection', title: 'Connection <br/>Speed', defaultContent: "Unknown",
							searchPanes: { header: 'Speed', controls: false}
						},
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
						{data: 'routerOf', title: "RoutingFor<br/>Count", defaultContent: 0,
							visible: ${settings?.addCols?.contains("routingCount")},
							render:function(data, type, row) { return data ? data.length : 0 }
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
						{ data: 'routeHtml', title: 'Route<br/>(from&nbsp;Hub)', searchPanes: { show: false }},
						{ data: 'devDetail.lastActiveTS', title: "Last Activity", defaultContent: "NO DATA", 
							visible: ${settings?.addCols?.contains("lastActive")},
							searchPanes: { show: false },
							render: function(data, type, row) {
								if (type === 'sort' || type === 'type') {
									return data
								} else if (type === 'display') {
									if (row.devDetail && row.devDetail.lastActive) {
										return row.devDetail.lastActive
									} else {
										return null
									}
								} else {
									return data
								}
							}
						},
						{ data: 'devDetail.listening', title: "Listening", defaultContent: "NO DATA",
							visible: ${settings?.addCols?.contains("listening")},
							searchPanes: {
								name: "listening",
								show: hasDeviceAccess() ? undefined : false,
								controls: false
							}
						},
						{ data: 'devDetail.beaming', title: "Beaming", defaultContent: "NO DATA",
							visible: ${settings?.addCols?.contains("beaming")},
							searchPanes: { show: hasDeviceAccess() ? undefined : false, controls: false}
						},
						{ data: 'devDetail.flirs', title: "FLiRS", defaultContent: "NO DATA",
							visible: ${settings?.addCols?.contains("flirs")},
							searchPanes: { show: hasDeviceAccess() ? undefined : false, controls: false}
						},
						{ data: 'devDetail.zwavePlus', title: "Z-Wave Plus", defaultContent: "NO DATA",
							visible: ${settings?.addCols?.contains("zwaveplus")},
							searchPanes: { show: hasDeviceAccess() ? undefined : false, controls: false}
						},
					],
					"pageLength": -1,
					"rowId": 'id',
					"lengthChange": false,
					"paging": false,
					"dom": "Pftrip",
					"searchPanes": {
						layout: 'meshdetails-6',
						cascadePanes: true,
						order: searchPanesList()
					}
				});
				updateLoading('','');
				hubLog('info', 'Datatables Loaded')
			}).then(e => {

				\$('#mainTable tbody').on('click', 'td.details-control', async function () {
						var tr = \$(this).closest('tr');
						var row = tableHandle.row( tr );
				
						if ( row.child.isShown() ) {
							row.child.hide();
							tr.removeClass('shown');
						}
						else {
							if (row.data().hubDeviceId != '') {
								var content = await displayRowDetail(row)
								row.child(content).show();
								tr.addClass('shown');
							}
						}
				} );
				// Fix width issue
				\$('input.dtsp-search').width('auto')
			})

			
		});
};

function sendDebugData() {
	/* Globals:
		deviceDetailsMap
		neighborsMap
		tableContent
		tableHandle

	*/
	var message = `
		# of Devices: \${tableContent.length}
		# of Devices with details: \${tableContent.filter(x => x.detail != null).length}
		Size of Neighbors Map (includes hub): \${neighborsMap.size}`
	
	if ($enableDebug)
		hubLog("debug", message)
}
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

def gitBranch() { return "beta" }

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
