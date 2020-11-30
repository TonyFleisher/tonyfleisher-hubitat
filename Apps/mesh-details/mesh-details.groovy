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
private releaseVer() { return "0.1.13-beta" }
private appVerDate() { return "2020-11-30" }
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
				// section("Advanced", hideable: true, hidden: true) {
				// 	input "deviceLinks", "bool", title: "Enable device links", defaultValue: false, submitOnChange: true
				// }
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
</style>
</head>
<body>
<h1 style="text-align:center;">Hubitat Z-Wave Mesh Details <div role="doc-subtitle" style="font-size: small;">(v${releaseVer() + ' - ' + appVerDate()})</div> </h1>
<div id="messages"><div id="loading1" style="text-align:center;"></div><div id="loading2" style="text-align:center;"></div></div>
<table id="mainTable" class="stripe cell-border hover">
	<thead>
	<tr>
	</tr>
	</thead>
</table>
<div>&copy; 2020 Tony Fleisher. All Rights Reserved.</div>
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
	if (childrenData[4].innerText != '') {
		label = childrenData[4].innerText.trim()
	} else {
		label = "<NO NAME>"
	}

	var deviceData = {
		id: devId,
		id2: devId2,
		devIdDec: devIdDec,
		node: nodeText.replace(' ', '&nbsp;'),
		metrics: statMap,
		routers: routers,
		label: label,
		deviceLink: deviceLink,
		deviceSecurity: childrenData[5].innerText.trim(),
		routeHtml: routesText,
		deviceStatus: childrenData[2].firstChild.data.trim(),
		connection: connectionSpeed

	}
	return deviceData
}

function updateLoading(msg1, msg2) {
	\$('#loading1').text(msg1);
	\$('#loading2').text(msg2);
}

async function getData() {

	var devList = await getZwaveList()
	var fullNameMap = devList.reduce( (acc,val) => {
						 acc[val.id]= `\${val.id} - \${val.label}`;
						 return acc;
					 }, {});

	// Pseudo entry for direct-connected devices
	fullNameMap.DIRECT = 'DIRECT'
	
	updateLoading('Loading.','Getting device detail');
	var nodeDetails = await getZwaveNodeDetail()

	var tableContent = devList.map( dev => {
		var routersFull = dev.routers.map(router => fullNameMap[router])
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

function findDeviceByDecId(devId) {
	return tableContent.find( row => row.id2 == devId)
}

function findDeviceByHexid(devId) {
	return tableContent.find( row => row.id == devId)
}
function displayNeighbors(devId) {
	var neighborList = []
	var deviceData = tableContent.find( row => row.id == devId)
	var html = '<div>'
	html += 'Neighbors:<hr/>'
	Object.entries(deviceData.detail.neighbors).forEach( (e) => {
		var key = e[0]
		var val = e[1]
		var nHex = parseInt(key).toString(16)
		if (key < 6) { 
			//console.log (`Found neighbor: \${key} name: HUB `)
			html += `0x0\${key} - HUB`
		} else {
			var neighbor = findDeviceByDecId(key)
			//console.log (`Found neighbor: \${key} spead: \${val.speed} repeater?: \${val.repeater} name: \${neighbor.label}` )
			html += `0x\${neighbor.id} - \${neighbor.label}`
		}
		html += '<br/>'
	})

	html += '</div>'
	return html

}

\$.ajaxSetup({
	cache: true
	});
var tableContent;
var tableHandle;
\$(document).ready(function(){
		loadScripts().then(function() {
			updateLoading('Loading..','Getting device data');
			getData().then( r => {	
				// console.log(list)
				tableContent = r;

				updateLoading('Loading..','Creating table');
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
						{ data: 'node', title: 'Node' },
						{ data: 'deviceStatus', title: 'Status', searchPanes: {controls: false},
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
								if ( cellData != "OK" ) {
									\$(td).css('color', 'red')
								}
							}
						},
						{ data: 'label', title: 'Device name', defaultContent: "!NO DEVICE!"},
						{ data: 'routersFull', title: 'Repeater', visible: false,
							render: {'_':'[, ]', sp: '[]'},
							defaultContent: "None",
							searchPanes : { orthogonal: 'sp' },
						},
						{ data: 'connection', title: 'Connection Speed', defaultContent: "Unknown",
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
							render: function(data, type, row) {
								var val = data
								if (type === 'filter' || type === 'sp') {
									return ( (val == (undefined || '')) || val.toString() == 'NaN') ? 'unknown' : val < 50 ? '0-50ms' : val <= 500 ? '50-500ms' : val < 1000 ? '500-1000ms' : '> 1000ms'
								} else if (type === 'sort' || type === 'type') {
									return val.toString() == 'NaN' ? -2 : val < 0 ? -1 : val
								} else {
									return val >= 0 ? 
										`\${val} ms`
										: "Data Error"
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
						{ data: 'metrics.Neighbors', title: 'Neighbor Count', defaultContent: "n/a", 
							searchPanes: {show: false},
							createdCell: function (td, cellData, rowData, row, col) {
								if (cellData == 2) {
									\$(td).css('color', 'darkorange')
								} else if (cellData <= 1) {
									\$(td).css('color', 'red')
								}
								\$(td).addClass('neighbors-' + rowData.id2)
								//\$(td).append(`&nbsp;<button style="float: right;" onclick="displayNeighbors('\${rowData.id}')">View</button>`)
							}
						},
						{ data: 'metrics.Route Changes', title: 'Route Changes', defaultContent: "n/a", 
							searchPanes: {show: false},
							createdCell: function (td, cellData, rowData, row, col) {
								if (cellData > 1 && cellData <= 4) {
									\$(td).css('color', 'darkorange')
								} else if (cellData > 4) {
									\$(td).css('color', 'red')
								}
							}
						},
						{ data: 'metrics.PER', title: 'Error Count', defaultContent: "n/a", searchPands: {show: false}},
						{ data: 'deviceSecurity', title: 'Security', defaultContent: "Unknown", searchPanes: { controls: false}},
						{ data: 'routeHtml', title: 'Route' }
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

				\$('#mainTable tbody').on('click', 'td.details-control', function () {
						var tr = \$(this).closest('tr');
						var row = tableHandle.row( tr );
				
						if ( row.child.isShown() ) {
							row.child.hide();
							tr.removeClass('shown');
						}
						else {
							row.child(displayNeighbors(row.id())).show();
							tr.addClass('shown');
						}
				} );
				// Fix width issue
				\$('input.dtsp-search').width('auto')

			})

			
		});
});
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

def gitBranch()         { return "beta" }
def getAppEndpointUrl(subPath)	{ return "${getFullLocalApiServerUrl()}${subPath ? "/${subPath}?access_token=${getAccessToken()}" : ""}" }
