/**
 *   Copyright 2020-2024 Tony Fleisher
 * 
 * Unless required by applicable law or agreed to in writing, 
 * this software is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
*/

import groovy.json.JsonOutput

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
private releaseVer() { return "1.1.35" }
private appVerDate() { return "2025-07-09" }

/**********************************************************************************************************************************************/
preferences {
	page name: "mainPage"
	page name: "devicesPage", nextPage: "mainPage"
}

mappings {
	path("/appData") { action: [GET: "appDataController"]}
	path("/deviceDetails") { action: [GET: "deviceDetailsController", POST: "saveDeviceDetailsController"]}
	path("/events") { action: [POST: "publishEventController"]}
	path("/meshinfo") { action: [GET: "meshInfo"]}
	path("/settings.js") { action: [GET: "settingsController"]}
	path("/settings") { action: [GET: "settingsController", POST: "updateAppSettingsController"]}
	path("/remoteLog") { action: [POST: "remoteLog"]}
}
import java.text.SimpleDateFormat

import groovy.transform.Field
@Field static String uiFramework = "bs5" // Use Boostrap5 framework
@Field static String uiMainTableClasses = "table table-striped table-bordered table-hover stripe cell-border hover"
@Field static String uiDeviceDetailTableClasses = "table table-bordered"
@Field static String uiDevicePropertiesTableClasses = "table table-bordered"
@Field static String v = "998md11343"
@Field static String statusMessage = ""
@Field static String fileSuffix = "-1.1.35"

String getMAIN_SCRIPT_LOCATION() { "/local/zwave_mesh_dev-script-controller" + fileSuffix + ".js" + "?v=${v}" }
String getUTILS_SCRIPT_LOCATION() {"/local/zwave_mesh_dev-utils-controller" + fileSuffix + ".js" + "?v=${v}" }
String getDATATABLES_SCRIPT_LOCATION() {"/local/zwave_mesh_dev-dataTables" + fileSuffix + ".js" + "?v=${v}" }

String getMAIN_CSS_LOCATION() { "/local/zwave_mesh_dev" + fileSuffix + ".css" + "?v=${v}" }

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
				String meshInfoLink = getAppLink("meshinfo")
				section("<b>General Configuration</b>") {
					input "linkStyle", "enum", title: "Link Style", required: true, submitOnChange: true, options: ["embedded":"Same Window", "external":"New Window"], image: "", defaultValue: "embedded"
					input "deviceLinks", "bool", title: "Enable device links", defaultValue: false, submitOnChange: true
					input "nodeBase", "enum", title: "Display nodes as hex or base10?", multiple: false, options: ["base16": "base16 (default)", "base10":"base10"], defaultValue: "base16", submitOnChange: true
					if (settings?.nodeBase == "base16") {
						input "includeDecNodeId", "bool", title: "Also include base10 nodeId?", defaultValue: false, submitOnChange: true
					}
					paragraph "<hr/>"
				}
				section("<b>DataTable Configuration</b>") {
					input "spLayout", "enum", title: "Search Panes Layout", required: false, submitOnChange: true, options: ["auto": "Auto", "columns-2": "2 Columns", "columns-3": "3 Columns (default)", "columns-4": "4 Columns", "columns-5": "5 Columns"], defaultValue: "columns-3"
					
					href "devicesPage", title: 'Authorize Extended Device Data', description: "Authorize access to Z-Wave Device data"

					href "", title: "Mesh Details", url: meshInfoLink, style: (settings?.linkStyle == "external" ? "external" : "embedded"), required: false, description: "Tap Here to load the Mesh Details Web App", image: ""

				}
				section("Advanced", hideable: true, hidden: !hasAnyAdvancedSettings()) {
					paragraph "<b>Advanced Table Configuration</b>"
					input "stateSave", "bool", title: "Save Table State (experimental)", defaultValue: false, submitOnChange: true
					input "enableResponsive", "bool", title: "Enable responsive table (experimental; WIP)", defaultValue: false, submitOnChange: true
					input "disableFixedHeader", "bool", title: "Disable fixed header", defaultValue: false, submitOnChange: true

					paragraph "<b>Logging Configuration</b>"
					input "enableDebug", "bool", title: "Enable debug logs", defaultValue: false, submitOnChange: true

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

def devicesPage() {
	dynamicPage (name: "devicesPage", title: "Authorize Access to Devices", install: false, uninstall: false) {
		if (!permitDeviceAccess) {
			app.removeSetting("deviceList")
			state.hasInitializedDeviceList = false
		}
		if (deviceList && !state?.hasInitializedDeviceList) {
			state.hasInitializedDeviceList = true
		}

		section("") {
			input "permitDeviceAccess", "bool", title: "Grant access to z-wave devices (experimental)", defaultValue: false, submitOnChange: true
			if (permitDeviceAccess) {
				if (!deviceList) {
					input "addAllZwave", "bool", title: "Select ALL Z-Wave devices", defaultValue: false, submitOnChange: true
					if (addAllZwave) {
						paragraph title: "initDevicesScript", """
										<div id="#zwDeviceInfo"></div>
										<script id="addDevices">
										var appData = ${JsonOutput.toJson(getAppData())};
										var	appSettings = ${JsonOutput.toJson(settings.findAll{ it.key != "deviceList" })};
										var	enableDebug = appSettings.enableDebug;
										</script>
                                        <script src="${UTILS_SCRIPT_LOCATION}"></script>

										<script>
										async function addAllZwaveDevices() {
											var deviceIds = await getZWaveDeviceIds()
											console.log(`\${deviceIds.length} zw devices found`)
											\$('#settings\\\\[deviceList\\\\]').val(deviceIds.join(","))
											jsonSubmit(null,null,false)
										}
										\$(document).ready( async function() { await addAllZwaveDevices()})
										</script>
						"""
						app.removeSetting("addAllZwave")
					}
				}
				input "deviceList", "capability.*", multiple: true, submitOnChange: true
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

def remoteLog() {
	def data = request.JSON
	if (data.level == "debug") {
		if (enableDebug) log.debug(data.log)
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

def hasAnyAdvancedSettings() {
	def result = enableDebug || stateSave || enableResponsive || disableFixedHeader;
	return result
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
	app.removeSetting("addAllZwave")
	app.removeSetting("permitDeviceAccess")
	app.removeSetting("spLayout")
	app.removeSetting("spOrder")
	app.removeSetting("spDisabled")
	app.removeSetting("disableResponsive")
	app.removeSetting("enableResponsive")
	app.removeSetting("disableFixedHeader")
	app.removeSetting("nodeBase")
	app.removeSetting("includeDecNodeId")
	app.removeSetting("hubitatQueryString")
	state.remove('hasInitializedCols')
	state.remove('hasInitializedDeviceList')
	state.remove('flirs')
	state.remove('listening')
	state.remove('refreshable')
}

def resetHostOverride() {
	if (enableDebug) log.debug "Resetting hostOverride"
	app.removeSetting("hostOverride")
	app.removeSetting("resetHost")
}

import org.codehaus.groovy.runtime.EncodingGroovyMethods


// Map from HE device id to object with relevant data; this is sent as json to app via /deviceDetails endpoint
// zwaveNodeInfo details:
/*
Silicon Labs, SDS13781, Z-Wave Application Command Class Specification.

Capability
Security
Reserved
Basic
Generic
Specific
inCCList 
0xEF
outCCList
*/
def collectDevicesData() {

	def results = [:]
	results = deviceList.inject([:], { r, dev -> 
			def id = dev.id
			def dni = dev.getDeviceNetworkId()
			def dniValue = -1
			if (dni && dni.length() <= 4 && dni.matches("\\p{XDigit}{${dni.length()}}")) {
				dniValue = Long.parseLong(dni, 16);
			}
			def isLR = dniValue > 255 ? true : false
			def lastActiveStrUTC = dev.getLastActivity()
			def lastActiveTS = lastActiveStrUTC ? Date.parse("yyy-MM-dd HH:mm:ssZ","$lastActiveStrUTC".replace("+00:00","+0000")).getTime() : null;
			
			SimpleDateFormat sdf= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
			def lastActiveStrLocal = lastActiveStrUTC ? sdf.format(lastActiveTS) : "Never"

			def zwaveData = dev.getDataValue("zwNodeInfo")
			if (!zwaveData && !isLR) {
				log.info("${dev.getDisplayName()} has no zwNodeInfo; some data will not be available. (New device or Not a z-wave device?)")
				return r;
			}
			def inCC = dev.getDataValue("inClusters")
			def inCCSec = dev.getDataValue("secureInClusters")

			def zwaveBytes = zwaveData ? zwaveData.split(" ") : []
			def zwaveDataLen = zwaveData ? zwaveBytes.length : 0

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
				listening = (Integer.parseInt(zwaveBytes[0], 16) & 0x80) ? "yes" : "no"
				routing = (Integer.parseInt(zwaveBytes[0], 16) & 0x40) ? "yes" : "no"
				flirs1000 = (Integer.parseInt(zwaveBytes[1], 16) & 0x40)
				flirs250 = (Integer.parseInt(zwaveBytes[1], 16) & 0x20)
				flirs = (flirs250 | flirs1000) ? "yes" : "no"
				beaming = (Integer.parseInt(zwaveBytes[1], 16) & 0x10) ? "yes" : "no"
				routingSlave = (Integer.parseInt(zwaveBytes[1], 16) & 0x08) ? "yes" : "no"
				speedBits = nifBytes[0].substring(2,5)
				maxSpeed = Integer.parseInt(speedBits,2)
				extraSpeed = (Integer.parseInt(zwaveBytes[2], 16) & 0x01) ? "yes" : "no"
				def ccData = parseCCFromZwaveinfo(zwaveData);
				inCCList = ccData.ccList;
				inCCSecList = ccData.ccSecList;

			}

			if (inCC) {
				inCC.split(',').each { if (!inCCList.contains(it)) {inCCList.add(it)}};
			}
			if (inCCSec) {
				inCCSec.split(',').each { if (!inCCSecList.contains(it)) {inCCSecList.add(it)}};
			}
			zwavePlus = (inCCList.contains('0x5E')) ? "yes" : "no"

			r.put(id, [
				name: dev.getDisplayName(),
				isDisabled: dev.isDisabled(),
				
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
				lastActive: lastActiveStrUTC,
				zwaveData: zwaveData,
				zwaveDataLen: zwaveDataLen,

				inCC: inCCList,
				inCCSec: inCCSecList,
				zwavePlus: zwavePlus,
				lastActiveTS: lastActiveTS,
				lastActiveStrLocal: lastActiveStrLocal,
				room: dev.getRoomName()
			])
			r
		}
	)
	return results
}

def parseCCFromZwaveinfo(zwaveInfo) {
	def result = [:]
	def ccList = []
	def ccSecList = []
	def filteredClasses = ['23', '34', '4F', '58', '5C', '5F', '68', '9A', 'F1', '00']
	def zwaveBytes = zwaveInfo.split(" ")
	if (zwaveBytes.size() < 7) {
		result.ccList = ccList
		result.ccSecList = ccSecList
		return result;
	}
	zwaveBytes = zwaveBytes[6..-1]

	// Only care about supported command classes, so ignore the controlled class list
	int end = zwaveBytes.findIndexOf {it == 'EF'}
	if (end != -1) {
		zwaveBytes = zwaveBytes[0..end-1]
	}

	// Technically, we should look for consecutive bytes: F1 00
	int secMark = zwaveBytes.findIndexOf {it == '00'}
	if (secMark != -1) {
		ccList = zwaveBytes[0..secMark-1]
		ccSecList = zwaveBytes[secMark+1 .. -1]
	} else {
		ccList = zwaveBytes
	}

	ccList.removeAll(filteredClasses)
	ccSecList.removeAll(filteredClasses)
	result.ccList = ccList.collect { '0x' + it}
	result.ccSecList = ccSecList.collect { '0x' + it}

	return result
}

// JSON Endpoint: GET /deviceDetails
def deviceDetailsController() {
	def results = collectDevicesData()
	results.now=(new Date()).getTime()
	renderJson(results)
}

// JSON Endpoint: POST /deviceDetails
def saveDeviceDetailsController() {
	def data = request.JSON
	if (enableDebug) {
		log.debug("saveDeviceDetailsController called with:\n${data}")
	}
	if (data.repeaterlist) {
		state.repeaterList = data.repeaterList
	}
	if (data.sleepyDevicesList) {
		state.sleepyDevicesList = data.sleepyDevicesList
	}
}

//JSON Endpoint: POST /settings.js
def updateAppSettingsController() {
	def data = request.JSON
	if (enableDebug) {
        log.debug("updateAppSettings: ${data}");
	}
	

	if (data.containsKey("spOrder")) {
        String spOrder = data.spOrder
        if (spOrder && spOrder.length() > 0) {
	        if (enableDebug) {log.debug("Updating panes order")}
			app.updateSetting("spOrder", data.spOrder)
        } else {
	        if (enableDebug) {log.debug("Resetting panes order to default")}
            app.removeSetting("spOrder")
        }
	}

	if (data.containsKey("spDisabled")) {
        String spDisabled = data.spDisabled
        if (spDisabled && spDisabled.length() > 0) {
	        if (enableDebug) {log.debug("Updating disabled panes")}
			app.updateSetting("spDisabled", data.spDisabled)
        } else {
	        if (enableDebug) {log.debug("Resetting disabled panes list")}
            app.removeSetting("spDisabled")
        }
	}

	if (data.containsKey("spLayout")) {
        String spLayout = data.spLayout
        if (spLayout && spLayout.length() > 0) {
	        if (enableDebug) {log.debug("Updating panes layout to ${data.spLayout}")}
			app.updateSetting("spLayout", [value: data.spLayout, type: 'enum'])
        } else {
	        if (enableDebug) {log.debug("Resetting panes layout to default")}
            app.removeSetting("spLayout")
        }
	}
}


def publishEventController() {
	def event = request.JSON
	sendEvent(event)
}

def settingsController() {
    def settingsJson = JsonOutput.toJson(settings.findAll{ it.key != "deviceList" })
	def jsOutput = """
	appSettings = ${settingsJson};
	enableDebug = appSettings.enableDebug;
	appData = Promise.resolve(\$.getJSON('${getAppEndpointUrl('appData')}'));
	"""
	renderJavaScript(jsOutput);
}

def appDataController() {
	renderJson(getAppData())
}

def getAppData() {
	return [
		appId: getAppId(),
		accessToken: getAccessToken(),
		locationName: location.name,
		hub: [
			uptime: location.hub.uptime,
			firmwareVersion: location.hub.firmwareVersionString,
			name: location.hub.name
		],
		links: [
			self: getAppLink(),
			appData: getAppLink("appData"),
			deviceDetails: getAppLink("deviceDetails"),
			remoteLog: getAppLink("remoteLog"),
			settings: getAppLink("settings"),
			events: getAppLink("events")
		],
		ui: [
			framework: uiFramework,
			mainTableClasses: uiMainTableClasses,
			detailTableClasses: uiDeviceDetailTableClasses,
			propertiesTableClasses: uiDevicePropertiesTableClasses
		]
	]
}

def renderJson(obj)
{
	render contentType: 'application/json', data: JsonOutput.toJson(obj)
}

def renderJavaScript(jsString) {
	render contentType: "application/javascript", data: jsString
}

def meshInfo() {
	def heVersion = location.hub.firmwareVersionString
	def html = """
<!DOCTYPE html>
<html lang="en">
<head>
<title>Hubitat Z-Wave Mesh Details</title>
<script>
var appData = ${JsonOutput.toJson(getAppData())};
var	appSettings = ${JsonOutput.toJson(settings.findAll{ it.key != "deviceList" && it.key != "hubitatQueryString" })};
var	enableDebug = appSettings.enableDebug;
</script>

<!-- From Datatables Download builder: https://datatables.net/download/ -->
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdn.datatables.net/v/bs5/jq-3.7.0/jszip-3.10.1/dt-2.1.8/b-3.2.0/b-colvis-3.2.0/b-html5-3.2.0/b-print-3.2.0/cr-2.0.4/fc-5.0.4/fh-4.0.1/r-3.0.3/sp-2.3.3/sl-2.1.0/sr-1.4.1/datatables.min.css" rel="stylesheet">
 
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/pdfmake.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.2.7/vfs_fonts.js"></script>
<script src="https://cdn.datatables.net/v/bs5/jq-3.7.0/jszip-3.10.1/dt-2.1.8/b-3.2.0/b-colvis-3.2.0/b-html5-3.2.0/b-print-3.2.0/cr-2.0.4/fc-5.0.4/fh-4.0.1/r-3.0.3/sp-2.3.3/sl-2.1.0/sr-1.4.1/datatables.min.js"></script>
<!-- END Download Builder -->
<!-- jsDelivr :: Sortable :: Latest (https://www.jsdelivr.com/package/npm/sortablejs) -->
<script src="https://cdn.jsdelivr.net/npm/sortablejs@latest/Sortable.min.js"></script>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
<link rel="stylesheet" href="/ui2/css/fontawesome.min.css?buildVersion=${heVersion}">
<link rel="stylesheet" href="/ui2/css/fontawesome-regular.min.css?buildVersion=${heVersion}">
<link rel="stylesheet" type="text/css" href="${MAIN_CSS_LOCATION}"/>
<!-- <script src="${getAppLink("settings.js")}"></script> -->
<script src="${DATATABLES_SCRIPT_LOCATION}"></script>
<script src="${UTILS_SCRIPT_LOCATION}"></script>
<script src="${MAIN_SCRIPT_LOCATION}"></script>

</head>
<body>
<div id="header-menu" class="fixed-top">
	<button type="button" class="btn" aria-controls="sidebarMenu" data-bs-toggle="offcanvas" data-bs-target="#sidebarMenu"><i class="fa-solid fa-bars"></i></button>
	<span id="hubNameBadge" class="align-self-center badge ms-4 rounded-pill text-bg-primary"></span>
</div>
<div id="pageTitle" class="text-center fixed-top">
	<h1>Hubitat Z-Wave Mesh Details <div role="doc-subtitle" style="font-size: small;">(v${releaseVer() + ' - ' + appVerDate()})</div> </h1>
</div>
<div id="messagesContainer" class="container">
	<div class="row justify-content-center">
		<div class="col-6">
			<div id="messages" style="text-align:center; flex-basis:100%;">
				<div id="errorAlerts" class="alert alert-danger text-center d-none"></div>
				<div id="warningAlerts" class="alert alert-warning text-center d-none"></div>
				<div id="message1" style="text-align:center; flex-basis:100%;"></div>
				<div id="loading1" style="text-align:center;"></div><div id="loading2" style="text-align:center;"></div>
			</div>
		</div>
	</div>
</div>

<div id="sidebarMenu" class="offcanvas offcanvas-start" aria-labelledby="menuTitle">
	<div class="offcanvas-header">
	  <div>
		<p class="offcanvas-title h5" id="menuTitle">Hubitat Z-Wave Mesh Details</span>
		<p ><small>version: v${releaseVer() + ' - ' + appVerDate()}</small></p>
		<p id="platformVersion" class="d-none mb-1"></p>
		<p id="zwaveVersion"><span id="zwaveUpdateBadge"></span></p>
	  </div>
	  <button type="button" class="btn-close align-self-start" data-bs-dismiss="offcanvas" aria-label="Close"></button>
	</div>
	<div class="offcanvas-body">
	  <div class="btn-group mb-3" role="group" aria-label="Light/Dark Theme Switcher">
		<button id="themeLight" type="button" class="btn btn-light border-dark">Light</button>
		<button id="themeDark" type="button" class="btn btn-dark border-light">Dark</button>
	  </div>
	  <div class="list-group">
		<button class="list-group-item list-group-item-success list-group-item-action" onclick="handleRefreshStats(this)">
			Refresh Statistics
		</button>
		<button class="list-group-item list-group-item-success list-group-item-action" id="view-topology" onclick="getTopologyModal(this)">
			View Z-Wave Topology >
		</button>
		<button id="panesConfigurationButton" class="list-group-item list-group-item-success list-group-item-action">
			Filter Panes Configuration >
		</button>
	  </div>
	</div>
	<div class="d-flex offcanvas-footer p-2">
		<hr/>
		<button id="clearStateSaveBtn" class="btn btn-outline-secondary" style="display:none;">Clear Saved Table State</button>
	</div>
</div>
<div id="mainContainer" class="container-fluid">
<table id="mainTable" class="${uiMainTableClasses}">
	<thead>
	<tr>
	</tr>
	</thead>
</table>
<div style="text-align:center;">&copy; 2020-2024 Tony Fleisher. All Rights Reserved.</div>
</div> <!-- mainContainer END -->
<div class="modal" id="topologyModal">
	<div id="topologyDialog" class="modal-dialog">
	  <div class="modal-content">
	  	<button type="button" class="btn-close ms-auto" data-bs-dismiss="modal" aria-label="Close"></button>
		<div class="modal-header">
			<span class="modal-title h2">Z-Wave Topology</span>
			<button type="button" class="btn btn-outline-info ms-auto" id="hideNonRepeatersBtn" onclick="hideNonRepeaters()" style="display:none;" data-toggle="modal">Hide NonRepeaters</button>
			<button type="button" class="btn btn-outline-info ms-auto" id="showNonRepeatersBtn" onclick="showAllTopology()" style="display:none;" data-toggle="modal">Show All</button>
		</div>
		<div class="modal-body">
			<div id="zwave-topology-table"> Loading ...
			</div>
		</div>
	  </div>
	</div>
</div>
<dialog id="zwaveRepairStatus">
	<span class="mdl-dialog__title">Z-Wave Repair</span>
	<div class="mdl-dialog__content">
		<p></p>
		<p><span id="zwave-repair-status"><br><br><br><br></span></p>
	</div>
	<div class="mdl-dialog__actions">
		<button type="button" onclick="closeRepair()" class="mdl-button close" id="close-zwave-repair">Close</button>
		<button type="button" class="mdl-button close" onclick="cancelRepair()" id="abort-zwave-repair">Abort</button>
	</div>
</dialog>
 <template id="reorderPanesListTemplate"><ul class="list-group d-flex"></ul></template>
 <template id="reorderPanesListItemTemplate">
 	<li class="list-group-item d-flex p-1">
	 <i class="bi bi-grip-vertical sort-handle"></i>
	 <span class="flex-md-grow-1 text-start ps-2"></span>
	</li>
 </template>
 <template id="reorderPanesColumnTemplate">
	<div class="col">
	</div>
 </template>
 <template id="reorderPanesContentTemplate">
 	<div class="row" id="selectLayout">
        <span>Search Filters Layout: </span>
        <div>
			<select class="form-select" id="layoutOption">
				<option value="auto">auto</option>
				<option value="columns-2">2-column</option>
				<option value="columns-3">3-column</option>
				<option value="columns-4">4-column</option>
			</select>
		</div>
    </div>
 	<div class="row mt-3" id="reorderPanesSortableRow"></div>
	<div class="footer p-2 row">
		<button id="cancelPanesConfig" class="btn btn-secondary col m-1">Cancel</button>
		<button id="applyPanesConfig" class="btn btn-secondary col m-1">Apply</button>
		<button id="savePanesConfig" class="btn btn-success col m-1">Save</button>
	</div>
 </template>
</body>
</html>
	"""
	// END meshInfo
	render contentType: "text/html", data: html
}

def installed() {
	if (enableDebug) log.debug "Installed with settings: ${settings}"
	atomicState?.isInstalled = true
	initialize()
}

def updated() {
	if (enableDebug) log.trace ("${app?.getLabel()} | Now Running Updated() Method")
	if(!atomicState?.isInstalled) { atomicState?.isInstalled = true }
	initialize()
}

def initialize() {
	log.info "Endpoint: ${getAppLink('meshinfo')}"
	
	// Cleanup removed settings
	app.removeSetting('embedStyle')

	if (permitDeviceAccess) {
		def allDevData = collectDevicesData()
		def listeningDevices = []
		def flirsDevices = []
		def stateDevData = allDevData?.each { entry -> 
			def devId = entry.key
			def devData = entry.value
			if (devData && devData.flirs == 'yes') {
				flirsDevices.push(devId)
			}
			if (devData && devData.listening == 'yes') {
				listeningDevices.push(devId)
			}
		}
		state.listening = listeningDevices
		state.flirs = flirsDevices

		def refreshableDevices = []
		deviceList?.each { d -> 
			if (d.hasCapability("refresh") || d.hasCommand("refresh")) {
				refreshableDevices.push(d.getId());
			}
		}
		state.refreshable = refreshableDevices
	}
	atomicState?.backgroundActionStatus = null
	
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

String getAppId() {
	return app.getId()
}

String removeHostFromURL(String originalURL)
{
	URI uri = new URI(originalURL);
	return uri.getPath() + (uri.getQuery() ? '?' + uri.getQuery() : '');
}
