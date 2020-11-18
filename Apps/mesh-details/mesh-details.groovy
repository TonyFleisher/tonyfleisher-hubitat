/*
 *   Adapted from: ST Mesh Details SmartApp
 *   Copyright 2020 Tony Fleisher
 *
 // /**********************************************************************************************************************************************/

definition(
	name			: "Hubitat-Mesh-Details",
	namespace		: "tfleisher",
	author			: "TonyFleisher",
	description		: "Get Device Mesh and Router Details",
	category		: "Utility",
	singleInstance	: true,
	iconUrl: "",
	iconX2Url: "",
	oauth: true)


/**********************************************************************************************************************************************/
private releaseVer() { return "0.1.2" }
private appVerDate() { return "2020-11-17" }
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
				}
				section("") {
					if(settings?.linkStyle) {
						href "", title: "Mesh Details", url: getAppEndpointUrl("meshinfo"), style: (settings?.linkStyle == "external" ? "external" : "embedded"), required: false, description: "Tap Here to load the Mesh Details Web App", image: ""
					} else {
						paragraph title: "Select Link Style", "Please Select a link style to proceed", required: true, state: null
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

def meshInfo() {
	def html = """
<html lang="en">
<head>
<title>Hubitat Z-Wave Mesh Details</title>
<link rel="stylesheet" type="text/css" href="https://cdn.datatables.net/1.10.21/css/jquery.dataTables.min.css">
<link rel="stylesheet" type="text/css" href="https://cdn.datatables.net/searchpanes/1.1.1/css/searchPanes.dataTables.min.css">
<link rel="stylesheet" type="text/css" href="https://cdn.datatables.net/select/1.3.1/css/select.dataTables.min.css">
<!--  
<script src="jquery-3.5.1.min.js"></script>
-->
<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js"></script>
<script src="${getAppEndpointUrl("script.js")}"></script>
</head>
<body>
<h1 style="text-align:center;">Hubitat Z-Wave Mesh Details</h1>
<div id="messages"><div id="loading1" style="text-align:center;"></div><div id="loading2" style="text-align:center;"></div></div>
<table id="mainTable" class="stripe cell-border hover">
	<thead>
	<tr>
	</tr>
	</thead>
</table>
</body>
</html>
	"""
	render contentType: "text/html", data: html
}

def scriptController() {
	def javaScript = """

function loadScripts() {
	updateLoading('Loading...','Getting script sources');
	var s1 = \$.getScript('https://unpkg.com/axios/dist/axios.min.js', function() {
		console.log("axios loaded")
	});
	
	var s2 = \$.getScript('https://cdn.datatables.net/1.10.21/js/jquery.dataTables.min.js')
	.then(s => \$.getScript('https://cdn.datatables.net/select/1.3.1/js/dataTables.select.min.js'))
	.then(s => \$.getScript('https://cdn.datatables.net/searchpanes/1.1.1/js/dataTables.searchPanes.min.js'));
	return Promise.all([s1, s2]);
}
// Get transformed list of devices (see transformDevice)
function getZwaveList() {
	const instance = axios.create({
		timeout: 5000,
		responseType: "document"
		});

	return instance
	.get('/hub/zwaveInfo')
	.then(response => {
		var result = {}
		var doc = new jQuery(response.data)
		var deviceRows = doc.find('.device-row')
		return deviceRows.map (
			(index,row) => {
				return transformZwaveRow(row)
			}
		)
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
	var routesText = childrenData[6].innerText.trim()
	var routers = routesText.split(' -> ')
	var lastParts = routers.splice(-1,1)
	routers.splice(0,1)
	var connectionSpeed = lastParts[0].split(' ')[1]

	var nodeText = childrenData[0].innerText.trim()
	console.log ("processing: " + nodeText)
	var devId = (nodeText.match(/0x([^ ]+) /))[1]
	var deviceData = {
		id: devId,
		node: nodeText,
		metrics: statMap,
		routers: routers,
		label: childrenData[4].innerText.trim(),
		deviceLink: childrenData[4].innerHTML.trim(),
		deviceSecurity: childrenData[5].innerText.trim(),
		routeHtml: routesText,
		connection: connectionSpeed

	}
	console.log(`deviceData: \${JSON.stringify(deviceData)}`)
	return deviceData
}

function updateLoading(msg1, msg2) {
	\$('#loading1').text(msg1);
	\$('#loading2').text(msg2);
}

\$.ajaxSetup({
	cache: true
	});
var tableContent;
var tableHandle;
\$(document).ready(function(){
		loadScripts().then(function() {
			updateLoading('Loading..','Getting device data');
			getZwaveList().then(list => {
				Promise.all(list).then( r => {	
					// console.log(list)
					updateLoading('Loading.','Getting device detail');
					
					var fullNameMap = r.reduce( (acc,val) => {
						 acc[val.id]= `\${val.id} - \${val.label}`;
						 return acc;
					 }, {});

					tableContent = r.map( dev => {
						var routersFull = dev.routers.map(router => fullNameMap[router])
						return {...dev, 'routersFull': routersFull}
					})
					updateLoading('Loading..','Creating table');
					tableHandle = \$('#mainTable').DataTable({
						data: tableContent,
						order: [[0,'asc']],
						columns: [
							//{ data: 'networkType', title: 'Type', searchPanes: { preSelect:['ZWAVE','ZIGBEE']} },
							{ data: 'node', title: 'Node' },
							{ data: 'label', title: 'Device name'},
							{ data: 'routersFull', title: 'Routers', visible: false,
								render: {'_':'[, ]', sp: '[]'},
								defaultContent: "None",
								searchPanes : { orthogonal: 'sp' },
							},
							{ data: 'connection', title: 'Connection Speed', defaultContent: "Unknown"},
							{ data: 'metrics.RTT Avg', title: 'RTT Avg', defaultContent: "n/a", searchPanes: {orthogonal: 'sp'},
							 render: function(data, type, row) {
								 var val = data.match(/(\\d*)ms/)[1]
								 if (type === 'filter' || type === 'sp') {
								 	return val == (undefined || '') ? 'unknown' : val < 10 ? '0-10ms' : val <= 50 ? '10-50ms' : '> 50ms'
								 } else if (type === 'sort' || type === 'type') {
									 return val
								 } else {
									 return val ? val + " ms" : 'unknown'
								 }
							  }
							 },

							{ data: 'metrics.LWR RSSI', title: 'LWR RSSI', defaultContent: "unknown", searchPanes: {show: false},
render: function(data, type, row) {
								 var val = (data === '' ? '' : data.match(/([-0-9]*)dB/)[1])
								 console.log('val for ' + data + ' is ' + val)
								 if (type === 'sort' || type === 'type') {
									 return val
								 } else {
									 return val ? val + " dB" : 'unknown'
								 }
							  }
							},
							{ data: 'metrics.Neighbors', title: 'Neighbor Count', defaultContent: "n/a", searchPanes: {show: false} },
							{ data: 'routeHtml', title: 'Route' },
							{data: 'metrics', title: 'Raw Stats', searchPanes: {show: false},
								"render": function ( data, type, row ) {
                    				return JSON.stringify(data);
								}
							}
						],
						"pageLength": -1,
						"rowId": 'id',
						"lengthChange": false,
						"paging": false,
						"dom": "Pftrip",
						"searchPanes": {
							cascadePanes: true
						}
					});
					updateLoading('','');
				})

			});
		});
});
"""
	render contentType: "application/javascript", data: javaScript
	
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
	log.debug "Endpoint: ${getAppEndpointUrl('meshinfo')}"
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
def gitBranch()         { return "master" }
def getAppEndpointUrl(subPath)	{ return "${getFullLocalApiServerUrl()}${subPath ? "/${subPath}?access_token=${getAccessToken()}" : ""}" }
 
 