/*
Copyright 2020-2021 by Tony Fleisher. All Rights Reserved.
*/
definition(
	name: "Hubitat Stats Details",
	namespace: "tfleisher",
	author: "TonyFleisher",
	description: "Get stats",
	category: "Utility",
	singleInstance: true,
	iconUrl: "",
	iconX2Url: "",
	oauth: true
)


/**********************************************************************************************************************************************/
private releaseVer() { return "0.1.2-alpha" }
private appVerDate() { return "2021-01-04" }
/**********************************************************************************************************************************************/
preferences {
	page name: "mainPage"
}

mappings {
	path("/statsinfo") { action: [GET: "statsInfo"] }
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
					paragraph "Choose if the Stats Details webapp will open in a new window or stay in this window"
					input "linkStyle", "enum", title: "Link Style", required: true, submitOnChange: true, options: ["embedded":"Same Window", "external":"New Window"], image: ""
				}
				section("") {
					String statsInfoLink = getAppLink("statsinfo")
					
					if(settings?.linkStyle) {
							href "", title: "Stats Details", url: statsInfoLink, style: (settings?.linkStyle == "external" ? "external" : "embedded"), required: false, description: "Tap Here to load the Stats Details Web App", image: ""
					} else {
						paragraph title: "Select Link Style", "Please Select a link style to proceed", required: true, state: null
					}
				}
				section("Advanced", hideable: true, hidden: true) {

					input "enableDebug", "bool", title: "Enable debug logs", defaultValue: false, submitOnChange: false
					input "deviceLinks", "bool", title: "Enable device links", defaultValue: false, submitOnChange: true

                    paragraph "<hr/>"
					link = getAppEndpointUrl("statsinfo")
					paragraph "If you accesss the hub by hostname rather than IP address, enter the hostname here"
					input "hostOverride", "string", title: "Override link host", defaultValue: getLinkHost(link), submitOnChange: false
					paragraph "Stats link: ${getAppLink("statsinfo")}"
					paragraph "Default link: ${link}"
					input "resetHost", "bool", title: "Reset link host to default", submitOnChange: true
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

def statsInfo() {
	def html = """
<html lang="en">
<head>
<title>Hubitat Stats Details</title>
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

dialog {
  position: fixed;
  top: 50%;
  transform: translate(0, -50%);
}

dialog:not([open]) {
    display: none;
}

.btn-nodeDetail {
  display: block
}
</style>
</head>
<body>
<h1 style="text-align:center;">Hubitat Stats Details <div role="doc-subtitle" style="font-size: small;">(v${releaseVer() + ' - ' + appVerDate()})</div> </h1>
<div id="messages"><div id="loading1" style="text-align:center;"></div><div id="loading2" style="text-align:center;"></div></div>
<div id="globalStatsDetail" hidden="true">
    App Stats: <span id="appStatsEnabled"></span><br/>
    App Stats Runtime: <span id="appStatsRuntime"></span><br/>
    Device Stats: <span id="devStatsEnabled"></span><br/>
    Device Stats Runtime: <span id="devStatsRuntime"></span>
</div>
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

function splitLines(t) { return t.split(/\\r\\n|\\r|\\n/); }

function updateLoading(msg1, msg2) {
	\$('#loading1').text(msg1);
	\$('#loading2').text(msg2);
}

function loadScripts() {
	updateLoading('Loading...','Getting script sources');
	var s1 = \$.getScript('https://unpkg.com/axios/dist/axios.min.js', function() {
	});
	
	var s2 = \$.getScript('https://cdn.datatables.net/1.10.21/js/jquery.dataTables.min.js')
	.then(s => \$.getScript('https://cdn.datatables.net/select/1.3.1/js/dataTables.select.min.js'))
	.then(s => \$.getScript('https://cdn.datatables.net/searchpanes/1.1.1/js/dataTables.searchPanes.min.js'));
	return Promise.all([s1, s2]);
}

// Get transformed list of apps (see transformAppRow)
function getAppList() {
	const instance = axios.create({
		timeout: 5000,
		responseType: "document"
		});

	return instance
	.get('/installedapp/list')
	.then(response => {
		//if ($enableDebug) console.log (`Response: \${JSON.stringify(response)}`)
		var doc = new jQuery(response.data)
		var deviceRows = doc.find('tr.app-row')
		var results = new Map()
		deviceRows.each (
			(index,row) => {
				const data = transformAppRow(row)
                results.set(data.id, data)
                if (data.children.size > 0) {
                    data.children.forEach((v,k)=>
                        {
                            results.set(k,v)
                        }
                    )
                }
			}
		)
		return results
	})
	.catch(error => { console.error(error); updateLoading("Error", error);} );
}

function transformAppRow(row) {
    var appid = row.getAttribute('data-app-id')
    var appName = row.firstElementChild.getAttribute('data-order').trim()
    var allAppIds = []
    allAppIds.push(appid)
    var childrenMap = new Map()
    var childrenList = row.getElementsByClassName('app-row-link-child')
    var appType = row.children[1].firstElementChild.firstChild.data.trim()
    var appConfigLink = row.getElementsByClassName('app-row-link')[0].firstElementChild.getAttribute('href')
    if (childrenList.length > 0) {
        Object.entries(childrenList).forEach( (entry) => {
            var i = entry[0]
            var e = entry[1]
            var childName = e.firstElementChild.firstChild.data.trim()
            var childLink = e.firstElementChild.getAttribute('href')
            var childId = childLink.match(/\\/([0-9]+)\$/)[1]
            var i2 = Number(i)+1
            var childType = row.children[1].children[i2].firstChild.data.trim()
            childDetail = {
                name: childName,
                id: childId,
                type: childType,
                configLink: childLink
            }
            childrenMap.set(childId, childDetail)
            allAppIds.push(childId)
        })
    }

    var appData = {
        name: appName,
        type: appType,
        id: appid,
        children: childrenMap,
        allIdList: allAppIds,
        configLink: appConfigLink
    }
    // console.log(appData)
    return appData
}

// Get transformed list of devices (see transformDeviceRow)
function getDevList() {
	const instance = axios.create({
		timeout: 5000,
		responseType: "document"
		});

	return instance
	.get('/device/list')
	.then(response => {
		//if ($enableDebug) console.log (`Response: \${JSON.stringify(response)}`)
		var doc = new jQuery(response.data)
		var deviceRows = doc.find('#list-view tbody tr')
		var results = new Map()
		deviceRows.each (
			(index,row) => {
                const data = transformDeviceRow(row)
				results.set(data.id, data)
                if (data.children.size > 0) {
                    data.children.forEach((v,k)=>
                        {
                            results.set(k,v)
                        }
                    )
                }
			}
		)
		return results
	})
	.catch(error => { console.error(error); updateLoading("Error", error);} );
}

function transformDeviceRow(row) {
    const devId = row.getAttribute('data-device-id')

    const devName = row.getElementsByClassName('name')[0].getAttribute('data-order')
    const devConfigLink = row.getElementsByClassName('name')[0].getElementsByTagName('a')[0].getAttribute('href')
    var allIds = []
    allIds.push(devId)

    var devType
    var childrenMap = new Map()
    const typeElement = row.getElementsByClassName('type')[0]
    const elementClass = row.getAttribute('class')
    if (elementClass == 'childRow') {
        devType = typeElement.firstElementChild.innerText.trim()
        var childrenList = row.getElementsByClassName('name')[0].getElementsByClassName('childArea')

        if (childrenList.length > 0) {
            Object.entries(childrenList).forEach( (entry) => {
                var i = Number(entry[0])
                var e = entry[1]
                var childName
                if (e.firstElementChild.children.length > 1) {
                    childName = e.firstElementChild.children[1].firstChild.data.trim()
                }
                else { 
                    childName = e.firstElementChild.firstChild.data.trim()
                }
                var childLink = e.firstElementChild.getAttribute('href')
                var childId = childLink.match(/\\/([0-9]+)\$/)[1]
                var childType = typeElement.children[i+1].innerText.trim()
                childDetail = {
                    name: childName,
                    id: childId,
                    type: childType,
                    configLink: childLink
                }
                childrenMap.set(childId, childDetail)
                allIds.push(childId)
            })
        }
    } else {
        devType = typeElement.innerText.trim()
    }

    const devData = {
        id: devId,
        type: devType,
        name: devName,
        children: childrenMap,
        configLink: devConfigLink,
        allIdList: allIds
    }

    // console.log(devData)
    return devData
}

function displayRowDetail(row) {
	var devId = row.id()
	var neighborList = []
	var deviceData = tableContent.find( row => row.id == devId)
	var data = row.data()
}

const STATS_TEST = false

const EMPTY_STATS = `Device Stats enabled: false
Device stats start time: 0
Device stats total run time: 0
App Stats enabled: false
App stats start time: 0
App stats total run time: 0
`
const STATS_TEST_DATA = EMPTY_STATS

function getStatsSource() {
    if (STATS_TEST) {
        return Promise.resolve({data: STATS_TEST_DATA})
    }

    const text_instance = axios.create({
        timeout: 5000,
        responseType: "text"
    });

    return text_instance.get('/hub/stats')
}

function getStatsData() {
    return getStatsSource()
        .then(response => {
            // if ($enableDebug) console.log (`Response: \${JSON.stringify(response)}`)
            const responseText = response.data
            const responseLines = splitLines(responseText)
            var results = []
            Object.entries(responseLines).forEach (
                (e) => {
                    var line = e[1]
                    result = parseStatsLine(line)
                    if (result) results.push(result)
                }
            )
            return results;
        })
        .catch(error => { console.error(error); updateLoading("Error", error);} );
}

var devStatsEnable = false
var devStatsStartTime = 0
var devStatsRunTime = 0

var appStatsEnable = false
var appStatsStartTime = 0
var appStatsRunTime = 0

function parseStatsLine(line) {
    if (line.startsWith('Device Stats enabled')) {
        vals = line.split(/: /,2)
        devStatsEnable = (vals[1] === 'true')
        if ($enableDebug) console.log(`Device Stats enabled: \${devStatsEnable}`)
        return;
    }

    if (line.startsWith('Device stats start time')) {
        vals = line.split(/: /,2)
        devStatsStartTime = Number(vals[1])
        return;
    }

    if (line.startsWith('Device stats total run time')) {
        vals = line.split(/: /,2)
        devStatsRunTime = Number(vals[1])
        if ($enableDebug) console.log(`Device Stats runtime: \${devStatsRunTime}`)
        return;
    }

    if (line.startsWith('App Stats enabled')) {
        vals = line.split(/: /,2)
        appStatsEnable = (vals[1] === 'true')
        if ($enableDebug) console.log(`App Stats enabled: \${appStatsEnable}`)
        return;
    }

    if (line.startsWith('App stats start time')) {
        vals = line.split(/: /,2)
        appStatsStartTime = Number(vals[1])
        return;
    }

    if (line.startsWith('App stats total run time')) {
        vals = line.split(/: /,2)
        appStatsRunTime = Number(vals[1])
        if ($enableDebug) console.log(`App Stats runtime: \${appStatsRunTime}`)
        return;
    }

    if (line.startsWith('app id')) {
        return parseAppStat(line)
    }

    if (line.startsWith('device id ')) {
        return parseDevStat(line)
    }

    console.log(`WARN: parse error with: \${line}`)
}

let APP_REGEX = /app id ([0-9]+) runcount ([0-9]+) total runtime ([0-9]+) average run time ([0-9.]+)/

function parseAppStat(line) {
//app id 504 runcount 25 total runtime 9650 average run time 386
//app id 503 runcount 23 total runtime 9040 average run time 393.0434782609

    var parts = line.match(APP_REGEX)
    var id = parts[1]
    const data = {
        id: id,
        runcount: Number(parts[2]),
        runtime: Number(parts[3]),
        average: Math.round( (Number(parts[4]) + Number.EPSILON) * 100)/100,
        detail: appData.get(id),
        type: 'App',
        line: line
    }
    return data
}

let DEV_REGEX = /device id ([0-9]+) runcount ([0-9]+) total runtime ([0-9]+) average run time ([0-9.]+)/

function parseDevStat(line) {

    var parts = line.match(DEV_REGEX)
    var id = parts[1]
    const data = {
        id: id,
        runcount: Number(parts[2]),
        runtime: Number(parts[3]),
        average: Math.round( (Number(parts[4]) + Number.EPSILON) * 100)/100,
        detail: deviceData.get(id),
        type: "Device",
        line: line
    }
    return data
}

var deviceData = null
var appData = null

async function getData() {

    updateLoading('Loading..','Getting device details');
    deviceData = await getDevList()

    updateLoading('Loading..','Getting app details');
    appData = await getAppList()

    const tableContent = getStatsData()
    return tableContent
}

function enableStats() {
	const instance = axios.create({
		timeout: 5000,
		responseType: "text"
		});

	return instance
	.get('/hub/enableStats')
	.then(response => {
        location.reload()
    });
}

function disableStats() {
	const instance = axios.create({
		timeout: 5000,
		responseType: "text"
		});

	return instance
	.get('/hub/disableStats')
	.then(response => {
        location.reload()
    });
}

// Data Tables setup
\$.ajaxSetup({
	cache: true
	});

var tableContent;
var tableHandle;
\$(document).ready(function(){
		loadScripts().then(function() {
			getData().then( r => {	
				// console.log(list)
				tableContent = r;
                \$('#appStatsEnabled').text(appStatsEnable ? "enabled" : "disabled")
                \$('#appStatsRuntime').text(appStatsRunTime)
                \$('#devStatsEnabled').text(devStatsEnable ? "enabled" : "disabled")
                \$('#devStatsRuntime').text(devStatsRunTime)
                \$('#globalStatsDetail').show()

                if (appStatsEnable) {
                    \$('#globalStatsDetail').prepend('<button style="background-color: orangered;" class="stats" onclick="disableStats()">Disable Stats</button><br/>')
                } else {
                    \$('#globalStatsDetail').prepend('<button style="background-color: lightgreen;" class="stats" onclick="enableStats()">Enable Stats</button><br/>')
                }
                if ( (devStatsRunTime > 1) || (appStatsRunTime > 1)) {
		    		updateLoading('Loading..','Creating table');
                    tableHandle = \$('#mainTable').DataTable({
                        data: tableContent,
                        order: [[5,'desc']],
                        columns: [
                            // {
                            //     "className":      'details-control',
                            //     "orderable":      false,
                            //     "data":           null,
                            //     "defaultContent": '<div>&nbsp;</div>'
                            // },
                            { data: 'type', title: 'Stat Type', defaultContent: 'unknown', searchPanes: { controls: false} },
                            { data: 'id', title: 'id', defaultContent: 'unknown' },
                            { data: 'detail.name', title: 'Label', defaultContent: 'unknown',
                                createdCell: function (td, cellData, rowData, row, col) {
                                        \$(td).wrapInner(`<a href="\${rowData.detail.configLink}">`)
                                }
                            },
                            { data: 'detail.type', title: 'Code Type', defaultContent: 'unknown', searchPanes: { controls: false} },
                            { data: 'runcount', title: 'Run count', defaultContent: 0, searchPanes: { controls: false} },
                            { data: 'runtime', title: 'Runtime', defaultContent: 0 },
                            { data: 'average', title: 'Avg Runtime', defaultContent: 0, searchPanes: { orthogonal: 'sp', controls: false},
							    render: function(data, type, row) {
                                    var val = data
                                    if (type === 'filter' || type === 'sp') {
                                        return val == (undefined || '') ? 'unknown' : val < 250 ? '0-250ms' : val <= 500 ? '250-500ms' : '> 500ms'
                                    } else if (type === 'sort' || type === 'type') {
                                        return val
                                    } else {
                                        return val ? 
                                            `\${val} ms`
                                            : 'unknown'
                                    }
                                },
                                createdCell: function (td, cellData, rowData, row, col) {
                                    var val = cellData
                                    if ( val > 500 ) {
                                        \$(td).css('color', 'red')
                                    } else if (val > 250) {
                                        \$(td).css('color', 'darkorange')
                                    }
                                    
                                }
                            }
                        ],
                        "pageLength": -1,
                        "rowId": 'id',
                        "lengthChange": false,
                        "paging": false,
                        "dom": "Pftrip",
                        "searchPanes": {
                            layout: 'columns-3',
                            cascadePanes: true,
                            order: ['Stat Type', 'Code Type', 'Avg Runtime']
                        }
                    });
				    updateLoading('','');
                } else {
                    updateLoading('No Stats', 'Enable Stats and then come back for analysis')
                }
			}).then(e => { 

				\$('#mainTable tbody').on('click', 'td.details-control', function () {
						var tr = \$(this).closest('tr');
						var row = tableHandle.row( tr );
				
						if ( row.child.isShown() ) {
							row.child.hide();
							tr.removeClass('shown');
						}
						else {
							row.child(displayRowDetail(row)).show();
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
	log.debug "Endpoint: ${getAppLink('statsinfo')}"
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

String getAppLink(String path) {
	String link = getAppEndpointUrl(path)
	if (hostOverride) {
		link = replaceHostInUrl(link, hostOverride)
		if (enableDebug) log.debug "Host overrride: ${hostOverride} new link: ${link}"
	}
	if (enableDebug) log.debug "appLink: ${link}"
	return link
}

String replaceHostInUrl(String originalURL, String newHost)
{
	URI uri = new URI(originalURL);
    uri = new URI(uri.getScheme(), newHost,
        uri.getPath(), uri.getQuery(), uri.getFragment());
    return uri.toString();
}

String getLinkHost(String url) {
	URI uri = new URI(url);
	return uri.getHost()
}