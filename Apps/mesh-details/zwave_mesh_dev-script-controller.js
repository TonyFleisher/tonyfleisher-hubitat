/**
 * Copyright 2023 Tony Fleisher
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
*/
const CMD_CLASS_Names = {
    0x20: "Basic",
    0x21: "Controller Replication",
    0x22: "Application Status",
    0x25: "Binary Switch",
    0x26: "Multilevel Switch",
    // 0x27: "All Switch (obsoleted)",
    // 0x28: "Binary Toggle Switch (obsoleted)",
    // 0x29: "Multilevel Toggle Switch (deprecated)",
    0x27: "All Switch",
    0x28: "Binary Toggle Switch",
    0x29: "Multilevel Toggle Switch",
    0x2B: "Scene Activation",
    0x2C: "Scene Actuator Configuration",
    0x2D: "Scene Controller Configuration",
    // 0x30: "Binary Sensor (deprecated)",
    0x30: "Binary Sensor",
    0x31: "Multilevel Sensor",
    0x32: "Meter",
    0x33: "Color Switch",
    // 0x35: "Pulse Meter (deprecated)",
    0x35: "Pulse Meter",
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
    // 0x46: "Climate Control Schedule (deprecated)",
    0x46: "Climate Control Schedule",
    0x47: "Thermostat Setback",
    0x48: "Rate Table Configuration",
    0x49: "Rate Table Monitor",
    0x4A: "Tariff Table Configuration",
    0x4B: "Tariff Table Monitor",
    0x4C: "Door Lock Logging",
    // 0x4E: "Schedule Entry Lock (deprecated)",
    // 0x50: "Basic Window Covering (obsoleted)",
    // 0x51: "Move to Position Window Covering (obsoleted)",
    0x4E: "Schedule Entry Lock",
    0x50: "Basic Window Covering",
    0x51: "Move to Position Window Covering",
    0x53: "Schedule",
    0x55: "Transport Service",
    // 0x56: "CRC-16 Encapsulation (deprecated)",
    // 0x57: "Application Capability (obsoleted)",
    0x56: "CRC-16 Encapsulation",
    0x57: "Application Capability",
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
    // 0x76: "Lock (deprecated)",
    0x76: "Lock",
    0x77: "Node Naming and Location",
    0x79: "Sound Switch",
    0x7A: "Firmware Update Meta Data",
    // 0x7B: "Grouping Name (deprecated)",
    // 0x7C: "Remote Association Activation (obsoleted)",
    // 0x7D: "Remote Association Configuration (obsoleted)",
    0x7B: "Grouping Name",
    0x7C: "Remote Association Activation",
    0x7D: "Remote Association Configuration",
    0x80: "Battery",
    0x81: "Clock",
    // 0x82: "Hail (obsoleted)",
    0x82: "Hail",
    0x84: "WakeUp",
    0x85: "Association",
    0x86: "Version",
    0x87: "Indicator",
    // 0x88: "Proprietary (obsoleted)",
    0x88: "Proprietary",
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
    // 0x9A: "IP Configuration (obsoleted)",
    0x9A: "IP Configuration",
    0x9B: "Association Command Configuration",
    // 0x9C: "Alarm Sensor (deprecated)",
    0x9C: "Alarm Sensor",
    0x9D: "Alarm Silence",
    // 0x9E: "Sensor Configuration (obsoleted)",
    0x9E: "Sensor Configuration",
    0x9F: "Security 2"
}

function setupScripts() {
    function numberSort(a, b) {
        var token1a = a.split('-', 2)[0].trim()
        var token1b = b.split('-', 2)[0].trim()
        var vala = parseInt(token1a)
        var valb = parseInt(token1b)
        if (!vala && vala !== 0) return 1;
        if (!valb && vala !== 0) return -1;
        return vala < valb ? -1 : 1
    }
    jQuery.extend(jQuery.fn.dataTableExt.oSort, {
        "initialNumber-asc": function (a, b) {
            return numberSort(a, b);
        },
        "initialNumber-desc": function (a, b) {
            return numberSort(a, b) * -1;
        },
    })

}


// Get data from zwaveNodeDetail endpoint (built-in)
function getZwaveNodeDetail() {
    const instance = axios.create({
        timeout: 5000
    });

    return instance
        .get('/hub/zwaveNodeDetail')
        .then(response => {
            //if (enableDebug) console.log (`Response: ${JSON.stringify(response)}`)

            return response.data
        })
        .catch(error => {
            console.error(error);
            updateLoading("Error", error);
            hubLog("error", `zwaveNodeDetail: Error getting zwave details: ${error}`)
        });
}

// Get details from devices app endpoint and merge into devList
function getDeviceDetails() {
    const instance = axios.create({
        timeout: 5000
    });

    return instance
        .get(appData.links.deviceDetails)
        .then(response => {
            //if (enableDebug) console.log (`Response: ${JSON.stringify(response)}`)

            return response.data
        })
        .catch(error => {
            console.error(error);
            updateLoading("Error", error);
            hubLog("error", `zwaveNodeDetail: Error getting zwave details: ${error}`)
        });
}

async function getData() {

    var devList = await getZwaveList()
	if (enableDebug) {
		console.log(`getData: Found ${devList.length} devices from getZwaveList`)
	}
    if (devList.length == 0) {
        return [];
    }

    var fullNameMap = devList.reduce((acc, val) => {
        acc[useHex() ? `0x${val.id}` : val.id2] = `${useHex() ? `0x${val.id}` : val.id2} - ${val.label}`;
        return acc;
    }, {});


    // Build routersFor map
    var routersFor = devList.reduce((acc, val) => {
        var myRouters = val.routersList
        var fullName = fullNameMap[useHex() ? `0x${val.id}` : val.id2]
        myRouters.map(r => {
            //console.log(`${r} is a router for ${fullName}`)
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
    fullNameMap.Unknown = 'Unknown'

    updateLoading('Loading.', 'Getting device detail');
    var nodeDetails = await getZwaveNodeDetail()

    updateLoading('Loading..', 'Building Neighbors Lists')
    buildNeighborsLists(fullNameMap, nodeDetails)

    var deviceDetails = {}
    if (hasDeviceAccess()) {
        deviceDetails = await getDeviceDetails()
        // Find any remaining devices that are not listening and not already in the nonRepeaters list
        var missingNonRepeaters = devList.reduce((acc, val) => {
            if (val.hubDeviceId && val.id2) {
                var hubId = val.hubDeviceId.toString()
                var zwId = val.id2.toString()
                var detail = deviceDetails[val.hubDeviceId.toString()]
                if (detail && detail.listening === false && !nonRepeaters.has(zwId)) {
                    acc.push(zwId)
                }
            }
            return acc
        }, [])

        if (missingNonRepeaters.length > 0) {
            hubLog("debug", "Adding any missed Non-listening and LR devices to nonRepeaters: " + missingNonRepeaters.toString())
            missingNonRepeaters.forEach(item => nonRepeaters.add(item))
        }
    }

    return devList.map(dev => {
        var routersFull = dev.routers.map(router => fullNameMap[router] || `${router} - UNKNOWN`)
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
            var totalSquared = Math.pow(detail.sumOfTransmissionTimes, 2)
            var sumOfTransmissionTimesSquared = detail.sumOfTransmissionTimesSquared
            var ss = (sumOfTransmissionTimesSquared - (totalSquared / count)).toFixed(0)
            variance = (ss / count).toFixed(2)
            stdDev = Math.sqrt(variance).toFixed(2)
        }
        dev.metrics.rtt_variance = variance
        dev.metrics.std_dev = stdDev
        return { ...dev, 'routerOf': routersFor.get(dev.id), 'routersFull': routersFull, 'detail': detail, 'devDetail': devDetail }
    })

}

var deviceDetailsMap = new Map() // cache/memoize data for each device (deviceId => map)

// Get data from device settings screen if we can't get it somewhere else
async function getDeviceInfo(devId) {
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

    var details = {}
    try {
        var response = await instance.get('/device/edit/' + devId)


        var doc = new jQuery(response.data)
        var deviceData = doc.find('#data-label ~ td li')
        deviceData.map(
            (index, row) => {
                var kvp = row.innerText.split(":")
                details[kvp[0].trim()] = kvp[1].trim()
            }
        )
        //var details = {inClusters,secureInClusters}
        deviceDetailsMap.set(devId, details)
    } catch (error) {
        console.error(error);
        hubLog("error", `Error getting device detail: ${error}`)
    };
    return details

}

function findDeviceByDecId(devId) {
    return tableContent.find(row => row.id2 == devId)
}

function findDeviceByHexId(devId) {
    var origId = devId;
    // Hex id in the table should be length 2 for mesh devices and length 4 for LR devices, so normalize this
    if (devId.length == 4 && devId.startsWith("00")) { devId = devId.slice(2)}
    if (devId.length == 3) { devId = `0${devId}`}

    if (enableDebug && (devId != origId)) {
        console.log(`findDeviceByHexId: translated ${origId} to ${devId}`)
    }
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
    Object.entries(nodeData).forEach(e1 => {
        var devId = e1[0]
        var detail = e1[1]
        if (detail.neighbors) {
            var hasNonHubNeighbor = false;
            Object.entries(detail.neighbors).forEach(e2 => {
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
                var r = neighborsMapReverse.get(neighborId)
                r.push(devId)

                if (neighborDetail.repeater == '0') {
                    nonRepeaters.add(neighborId)
                }

                var nHex = ('00' + parseInt(neighborId).toString(16)).slice(-2).toUpperCase()
                if (!fullnameMap[nHex]) {
                    fullnameMap[nHex] = `${nHex} - UNKNOWN`
                }

                if (!hasNonHubNeighbor && parseInt(neighborId) > 1) {
                    hasNonHubNeighbor = true
                }

            })

            // Consider Devices with no neighbors and not seen by other devices (checked above) as nonRepeaters
            // This could result in false identification, but is acceptable for current nonRepeater use cases.
            if (!hasNonHubNeighbor) {
                if (enableDebug) hubLog("debug", "No neighbors: Adding to nonRepeaters: " + devId)
                nonRepeaters.add(devId)
            }
        }
    })
}


async function displayRowDetail(row) {
    var devId = row.id()
    var neighborList = []
    var deviceData = tableContent.find(row => row.id == devId)
    var data = row.data()

    // On demand data - scrape from device detail page
    if (!data.commandClasses && data.hubDeviceId) {
        var detailData = await getDeviceInfo(data.hubDeviceId)
        var inClusters = detailData.inClusters && detailData.inClusters.length > 1 ? detailData.inClusters.split(',') : []
        var secureInClusters = detailData.secureInClusters && detailData.secureInClusters.length > 1 ? detailData.secureInClusters.split(',') : []
        var commandClasses = inClusters.concat(secureInClusters)
        // Update data
        console.log("Command classes is: " + commandClasses)
        data.commandClasses = commandClasses
    }

    // /** NOTE: responsive properties not available unless responsive is enabled for the table **/
    // var isAnyColumnHiddenByResponsive = row.responsive.hasHidden()
    // var responsiveContext = row.responsive().context[0];
    // var responsiveColumns = responsiveContext._responsive._detailsObj
    // var tableAll = DataTable.Responsive.renderer.tableAll()
    // var rowDataTable = tableAll(row, row.index(), responsiveColumns[row.index()])
    var html = '<div>'
    //XXXXX TOOD: Device data table

    // if (deviceData.devDetail) {
    //     html += `Detail Properties:<br/>`
    //     var properties = []
    //     properties.forEach(p => {
    //         html += `${p}: ${deviceData.devDetail[p]}<br/>`
    //     })
    //     html += '<hr/>'
    // }

    html += '<div><table>'

    // Header Row
    html += '<tr>'
    html += '<th>Repeaters</th>'
    if (deviceData.routerOf && deviceData.routerOf.length > 0) {
        html += '<th>Routing For</th>'
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
        html += `<ul style="${neighborListStyle}">`
        neighborList.forEach((neighborId) => {
            var symetry = false
            if (neighborOfList && neighborOfList.includes(neighborId)) {
                symetry = true
            }
            var color
            if (!symetry) { color = "orange" }
            html += `<li ${color ? `style="color:${color}"` : ""}>`
            if (neighborId == 1) {
                html += useHex() ? '0x0' : '' // 0-pad for hex value
                html += `1 - HUB`
            } else {
                var deviceData = findDeviceByDecId(neighborId)
				if (deviceData) {
					html += useHex() ? `0x${deviceData.id}` : deviceData.id2
					html += ` - ${deviceData.label}`
					if (nonRepeaters.has(deviceData.id2.toString())) {
						html += '<sup>*</sup>'
					}
				} else if (neighborId > 0) {
					html += `Unknown Neighbor (${neighborId})`
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
        html += `<ul style="${neighborListStyle}">`
        neighborOfList.forEach((neighborId) => {
            var symetry = false
            if (neighborList && neighborList.includes(neighborId)) {
                symetry = true
            }
            var color
            if (!symetry) { color = "orange" }
            html += `<li ${color ? `style="color:${color}"` : ""}>`
            if (neighborId == 1) {
                html += useHex() ? '0x0' : ''
                html += `1 - HUB`
            } else {
                var deviceData = findDeviceByDecId(neighborId)
                if (deviceData) {
                    html += useHex() ? `0x${deviceData.id}` : deviceData.id2
                    html += ` - ${deviceData.label}`

                    if (nonRepeaters.has(deviceData.id2.toString())) {
                        html += '<sup>*</sup>'
                    }
                    // TODO: If deviceData.id is a router for neighborId
                } else {
                    html += "Unknown Device: " + neighborId
                }
            }
            html += '</li>'
        })
        html += '</ul>'
    }
    html += '</td>'

    // Command Classes
    if (data.commandClasses && data.commandClasses.length > 0) {
        html += '<td style="vertical-align: top;">'
        data.commandClasses.forEach(cc => {
            html += cc
            var ccVal = Number(cc)
            if (CMD_CLASS_Names[ccVal]) {
                html += ` - ${CMD_CLASS_Names[ccVal]}`
            }
            html += "<br/>"
        });
        html += '</td>'
    }

    html += '<td style="vertical-align: top;">'

    if (enableDebug) {
        html += '<button class="debug-control" onclick="showDetailDebug(this)" class="btn btn-danger btn-nodeDetail">Show Debug</button>'
        var pretty = JSON.stringify(data.detail, null, 'JSONS')
        html += '<div hidden="true" class="debug-content"><span>zwave NodeDetail</span><pre>'
        html += pretty.replace(/JSONS/g, '&nbsp;&nbsp;')
        html += '</pre></div>'
        if (data.devDetail) {
            pretty = JSON.stringify(data.devDetail, null, 'JSONS')
            html += '<div hidden="true" class="debug-content"><span>Device Detail</span><pre>'
            html += pretty.replace(/JSONS/g, '&nbsp;&nbsp;')
            html += '</pre></div>'
        } else {
			html += '<div hidden="true" class="debug-content"><span>Device Detail</span><pre>No Data - no auth or not zwave?</pre></div>'
        }
    }

    if (data.commandClasses && !data.commandClasses.includes('0x84')) {
        html += `<button onclick="zwaveNodeRepair(${data.id2})" class="btn btn-danger btn-nodeDetail">Repair</button>`
    }
    html += '</td>'

    html += '</tr></table>'
    html += '<p><sup>*</sup>Device is a non-repeater</p>'
    html += '</div>'
    return html
}

function showDetailDebug(btn) {
    $(btn.parentElement).find('.debug-content').show().prop('hidden', false)
}

function zwaveNodeRepair(zwaveNodeId) {

    $("#close-zwave-repair").attr("disabled", true)
    $("#abort-zwave-repair").attr("disabled", false)

    $.ajax({
        url: "/hub/zwaveNodeRepair2?zwaveNodeId=" + zwaveNodeId,
        type: "GET",
        success: function (data) {
            console.log(data)
            repairUpdateInterval = setInterval(checkZwaveRepairStatus, 3000)
            $("#zwave-repair-status").html('')
            if (zwaveRepairStatus.showModal) {
                zwaveRepairStatus.showModal();
            }
        },
        error: function (data) {

        }
    });
};

function checkZwaveRepairStatus() {
    $.ajax({
        url: "/hub/zwaveRepair2Status",
        type: "GET",
        dataType: 'JSON',
        success: function (data) {
            console.log(data.stage)
            $("#zwave-repair-status").html(data.html)
            if (data.stage === "IDLE") {
                $("#close-zwave-repair").attr("disabled", false)
                $("#abort-zwave-repair").attr("disabled", true)
                clearInterval(repairUpdateInterval)
            } else {
                $("#close-zwave-repair").attr("disabled", true)
                $("#abort-zwave-repair").attr("disabled", false)
            }
        },
        error: function (data) {

        }
    });
}

function labelTopologyHeads(sel, placement) {
    sel.each((i, data) => {
        var td = $(data)
        var str = data.innerHTML
        //console.log(str)
        if (str.match(/[A-F0-9]/)) {
            if (str == "01") {
                td.attr("aria-label", "HUB")
                //data-bs-toggle="tooltip" data-bs-placement="top" data-bs-title="Tooltip on top"
                td.attr("data-bs-toggle", "tooltip")
                td.attr("data-bs-placement", placement)
                td.attr("data-bs-title", "HUB")

                td.addClass("mesh-tooltip")
            } else {
                var d = findDeviceByHexId(str)
                if (d != null) {
                    td.attr("aria-label", d.label)
                    td.attr("data-bs-toggle", "tooltip")
                    td.attr("data-bs-placement", placement)
                    td.attr("data-bs-title", d.label)    
                    td.addClass("mesh-tooltip")
                }
            }
        }
    })
}

function labelTopologyCells(index, row, labels, placement) {
    row.find('td:nth-child(n+2)').each((i, o) => {
        var seen = "not seen";
        if (o.bgColor == 'white') return;
        if (o.bgColor == 'blue') seen = "seen";
        var myLabel = labels[index]
        var dstLabel = labels[i]
        var td = $(o)
        const cellTooltip = myLabel + " -> " + dstLabel + ":" + seen
        td.attr("aria-label", cellTooltip)
        td.addClass("topologyCell")
        td.attr("data-bs-toggle", "tooltip")
        td.attr("data-bs-placement", placement)
        td.attr("data-bs-title", cellTooltip)

    })
}

// onclick action from View Topology button
function getTopologyModal(e) {
    const myModal = new bootstrap.Modal('#topologyModal')
    addSpinner(e, "loadTopologySpinner", "Loading...");

    $.ajax({
        url: "/hub/zwaveTopology",
        type: "GET",
        success: function (result) {
            $("#zwave-topology-table").html(result);
            $(topologyDialog).find('table').addClass("table table-sm")
            // Insert tooltips
			var topr = $('#topologyDialog table tr:nth-child(1) td:nth-child(n+2)')
			var c1 = $('#topologyDialog table tr:nth-child(n+1) td:nth-child(1)')

            var deviceHexIds = topr.map(function () { return this.innerHTML })
            var deviceLabels = deviceHexIds.map((i, o) => { if (o === '01') { return "HUB" } else return findDeviceByHexId(o).label })

            labelTopologyHeads(topr, "top")
            labelTopologyHeads(c1, "right")

			var tRows = $('#topologyDialog table tr:nth-child(n+2)')
            tRows.each((i, row) => {
                labelTopologyCells(i, $(row), deviceLabels, "top")
            })

            var cells = $('td.topologyCell[bgcolor]');
            cells.each( (i, cell) => {
                var theColor = cell.bgColor;
                cell.className = cell.className + " bg-" + theColor;
            })
            // init tooltips
            const tooltipTriggerList = document.querySelectorAll('[data-bs-toggle="tooltip"]')
            const tooltipList = [...tooltipTriggerList].map(tooltipTriggerEl => new bootstrap.Tooltip(tooltipTriggerEl))
            $('#topologyDialog').css('max-width', 'fit-content');
            $('#loadTopologySpinner').remove()
            myModal.show();
            $('#hideNonRepeatersBtn').show()
            $('#showNonRepeatersBtn').hide()
        }
    });
}

function cancelRepair() {
    $.ajax({
        url: "/hub/zwaveCancelRepair",
        type: "GET",
        success: function (result) {

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
    $('#topologyDialog table tbody tr td').show()
    $('#topologyDialog table tbody tr').show()
    $('#hideNonRepeatersBtn').show()
    $('#showNonRepeatersBtn').hide()
}

function hideNonRepeaters() {
    topr = $('#topologyDialog table tbody tr:nth-child(1)') // Get the top row with nodes (hex starting in position 2)
    rowItems = topr[0].innerText.split(/\s+/) // Split into a list
    rowItems.slice(2).forEach((item, index) => {
        if (item.match(/[A-F0-9]/)) {
            $('#hideNonRepeatersBtn').hide()
            $('#showNonRepeatersBtn').show()
            if (enableDebug) console.log(`hideNonRepeaters: Testing ${item}`)
            const d = findDeviceByHexId(item)

            if (nonRepeaters.has(d.id2.toString())) {
                if (enableDebug) console.log(`hideNonRepeaters: ${item} is not a repeater; hiding`)
                $(`#topologyDialog table tbody tr td:nth-child(${index + 3})`).hide()
                $(`#topologyDialog table tbody tr:nth-child(${index + 3})`).hide()
            } else {
                if (enableDebug) console.log('hideNonRepeaters: not in nonrepeaters list')
            }

            var neighborOfMap = neighborsMapReverse.get(d.id2.toString())
            if (!neighborOfMap || neighborOfMap.length == 0) {
                if (enableDebug) console.log(`hideNonRepeaters: ${item} is not seen by any other device; hiding`)
                $(`#topologyDialog table tbody tr td:nth-child(${index + 3})`).hide()
                $(`#topologyDialog table tbody tr:nth-child(${index + 3})`).hide()
            } else {
                if (enableDebug) console.log(`hideNonRepeaters: has neighbors: ${neighborOfMap}`)
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

            // Merge head from fetched content into current page
            var h = doc.find('head').children()
            $('head').append(h)

            // Hide current page content and add/show the fetched doc
            var c = doc.find('body').children()
            $('main > :first-child').children().hide()
            $('main > :first-child').append(c)

            var currentPage = $('#currentPage').val()
            history.pushState({ currentPage: currentPage, previousPage: null, statsLoaded: true, appURI: appURI }, "View Hub Stats", "?page=view&debug=true")
        })
        .catch(error => { console.error(error); updateLoading("Error", error); });
}

window.onpopstate = function (event) {
        location.reload()
}

$.ajaxSetup({
    cache: true
});
var tableContent;
var tableHandle;


/**
 * Utility function to call refreshData() with the global tableHandle
 * onclick action from refresh stats button
 */
function handleRefreshStats(e) {
    console.log("Refreshing stats...");
    addSpinner(e, "refreshSpinner", "Loading...");
	refreshData(tableHandle);
}

function refreshData(dt) {
    updateLoading('Refreshing..', 'Refreshing device data');

    return refreshStatistics().then(
        recordAppEvent("refreshStatistics", true, "Z-wave Statistics refreshed")
    ).always(() =>  getData().then(d => {
        updateLoading('Loading..', 'Creating table (refresh)');
        tableContent = d;
        dt.clear().rows.add(d).searchPanes.clearSelections().searchPanes.rebuildPane().draw()
        // After redraw, click showAll to fix button states
        $('.dtsp-showAll').click()
        updateLoading('', '');
        updateHeaderMessage(new Date().toString())
        hubLog('info', 'Datatables Refresh Statistics completed');
        $('#refreshSpinner').remove()
        return;
    }))
}

/**
 * Use a simple jQuery get to refresh stats
 * @returns 
 */
function refreshStatistics() {
	return $.get('/hub/zwaveNodeDetailGet')
}


var mainAppStarted;
/**
 * doWork is the Main method that builds Mesh Details search panes and data table
 * @returns Promise(void)
 */
async function doWork() {
    mainAppStarted = true;
    await setupScripts()
    hubLog("info", "UserAgent: " + navigator.userAgent)
    if (!window.axios) {
        await loadAxios()
    }

    // Setup State handler
    $('#mainTable').on('requestChild.dt', async function (e, row) {
        console.log(`Restoring child for row`)
        console.log(row)
        console.log(e)
        if (row.data().hubDeviceId != '') {
            var content = await displayRowDetail(row)
            row.child(content).show();
        }
    });

    var idCol = useHex() ? 'id' : 'id2';
    tableHandle = $('#mainTable').DataTable({
        //data: tableContent,
        ajax: function (data, callback, settings) {

            updateLoading('Loading..', 'Getting device data');

            getData().then((d) => {
                console.log("Data load complete");
                tableContent = d;
                sendDebugData();
                updateLoading('Loading..', 'Creating table');
                callback({data: tableContent});
                updateLoading('', '');
                hubLog('info', 'Datatables Loaded')
                updateHeaderMessage(new Date().toString())
                getZwaveVersion().then (v => {
                    versionStr = `${v.firmware0Version}.${v.firmware0SubVersion}.${v.hardwareVersion}`;
                    $('#zwaveVersion')[0].innerText = `Z-Wave Firmware: ${versionStr}`;
                    if ("isRadioUpdateNeeded" in zwaveDetailsJson) {
                        if (zwaveDetailsJson.isRadioUpdateNeeded) {
                            updateStr = "Needs Update";
                            badgeClass = "text-bg-warning";
                        } else {
                            updateStr = "Current";
                            badgeClass = "text-bg-success";
                        }
                        const span = document.createElement('span');
                        span.innerText = updateStr;
                        span.className = `badge rounded-pill ms-2 ${badgeClass}`;
                        $('#zwaveVersion').append(span);
                    }
                });
            })
        },
        rowId: 'id2',

        order: [[1, 'asc']],
        columns: [
            {
                "className": 'dt-control',
                "orderable": false,
                "data": null,
                "defaultContent": '',
                "responsivePriority": 1
            },
            {
                data: useHex() ? 'id' : 'id2', title: 'Node',
                render: function (data, type, row) {
                    if (type === 'sort') {
                        return row.id2
                    }
                    return useHex() ? `0x${data}` : data
                },
                createdCell: function (td, cellData, rowData, row, col) {
                    if (appSettings.includeDecNodeId) {
                        var formattedDecId = rowData.id2.toString().padStart(3, '0');
                        td.innerText += ' ';
                        s = document.createElement("span");
                        s.textContent = `(${formattedDecId})`
                        td.appendChild(s)
                    }
                },
                responsivePriority: 2
            },
            { data: 'networkType', title: 'Network Type', visible: false, searchPanes: {orderable: false, dtOpts:{searching:false}} },
            {
                data: 'deviceStatus', title: 'Status', 
                render: function (data, type, row) {
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
                            return `3${data}`
                    }
                },
                "createdCell": function (td, cellData, rowData, row, col) {
                    var isRepeater = nonRepeaters.has(rowData.id2.toString())
                    if (cellData != "OK") {
                        if (!isRepeater) {
                            $(td).css('color', 'red')
                        } else {
                            $(td).wrapInner('<strike>')
                        }
                    }
                }
            },
            {
                data: 'label', title: 'Device name',
                render: function (data, type, row) {
                    if (type === 'display') {
                        if (!data) {
                            return "NO DEVICE FOUND"
                        }
                    }
                    return data
                },
                createdCell: function (td, cellData, rowData, row, col) {
                    if (appSettings.deviceLinks == true && rowData.deviceLink) {
                        $(td).wrapInner(`<a href="${rowData.deviceLink}"></a>`)
                    }
                    if (rowData.devDetail?.isDisabled) {
                        $(td).wrapInner('<strike></strike>')
                    }
                    if (cellData == "") {
                        $(td).css('color', 'red')
                    }
                },
                responsivePriority: 2
            },
            { 
                data: 'devDetail.room', title: "Room", defaultContent: "no room"
            },
            {
                data: 'type', title: 'Device Type', defaultContent: "!NO DEVICE!"
            },
            {
                data: 'manufacturer', title: 'Manufacturer', defaultContent: "!NO DEVICE!"
            },
            {
                data: 'routersFull', title: 'Repeater', visible: false,
                render: { '_': '[, ]', sp: '[]' },
                defaultContent: "None",
                searchPanes: { orthogonal: 'sp', className: 'sp-only' },
                type: 'initialNumber',
                className: 'sp-only dtr-never'
            },
            {
                data: 'connection', title: 'Connection <br/>Speed', defaultContent: "Unknown",
                searchPanes: { header: 'Speed' }
            },
            {
                data: 'metrics.RTT Avg', title: 'RTT Avg', defaultContent: "n/a", searchPanes: { orthogonal: 'sp'},
                render: function (data, type, row) {
                    var val = data.match(/(\d*)ms/)[1]
                    if (type === 'filter' || type === 'sp') {
                        return (val === undefined || val === '' || val === 'unknown') ? 'unknown' : val < 100 ? '0-100ms' : val <= 500 ? '100-500ms' : '> 500ms'
                    } else if (type === 'sort' || type === 'type') {
                        return val
                    } else {
                        return val ?
                            `${val} ms`
                            : 'unknown'
                    }
                },
                createdCell: function (td, cellData, rowData, row, col) {
                    var val = cellData.match(/(\d*)ms/)[1]
                    if (val > 500) {
                        $(td).css('color', 'red')
                    } else if (val > 100) {
                        $(td).css('color', 'darkorange')
                    }
                    if (val > 0) {
                        $(td).append(`<div style="font-size: small;">count: ${rowData.detail.transmissionCount}</div>`)
                    }
                }

            },
            {
                data: 'metrics.std_dev', name: "std_dev", title: 'RTT StdDev', defaultContent: "n/a", searchPanes: { orthogonal: 'sp' },
                render: function (data, type, row) {
                    var val = data
                    if (type === 'filter' || type === 'sp') {
                        return ((val == undefined || val == '') || val.toString() == 'NaN') ? 'unknown' : val < 50 ? '0-50ms' : val <= 500 ? '50-500ms' : val < 1000 ? '500-1000ms' : '> 1000ms'
                    } else if (type === 'sort' || type === 'type') {
                        return val.toString() == 'NaN' ? -2 : val < 0 ? -1 : val
                    } else {
                        return val >= 0 ?
                            `${val} ms`
                            : "unknown"
                    }
                },
                createdCell: function (td, cellData, rowData, row, col) {
                    var val = cellData
                    var avg = parseInt(rowData.metrics["RTT Avg"].match(/(\d*)ms/)[1])
                    if (val > (2 * avg)) {
                        $(td).css('color', 'red')
                    } else if (avg > 0 && val > avg) {
                        $(td).css('color', 'darkorange')
                    }

                }

            },
            {
                data: 'metrics.LWR RSSI', title: 'LWR RSSI', defaultContent: "unknown", searchPanes: { orthogonal: 'sp' },

                render: function (data, type, row) {
                    var val = (data === '' ? '' : data.match(/([-0-9]*)dB/)[1])
                    if (type === 'filter' || type === 'sp') {
                        return (val == undefined || val == '') ? 'unknown'
                            : val < -20 ? '-20dB - -11dB'
                                : val <= 0 ? '-10dB - -1dB'
                                    : val <= 10 ? '0dB - 10dB'
                                        : '> 10dB'
                    } else if (type === 'sort' || type === 'type') {
                        return (val == undefined || val == '') ? -1 : val
                    } else {
                        return val ?
                            `${val} dB`
                            : 'unknown'
                    }
                },
                createdCell: function (td, cellData, rowData, row, col) {
                    var val = (cellData === '' ? '' : cellData.match(/([-0-9]*)dB/)[1])
                    if (val > 0 && val < 17) {
                        $(td).css('color', 'darkorange')
                    } else if (val <= 0) {
                        $(td).css('color', 'red')
                    }

                }

            },
            {
                data: 'routerOf', title: "RoutingFor <br/>Count", defaultContent: 0,
                render: function (data, type, row) {
                    if (type === 'sort' || type === 'type') {
                        return (row.networkType == "LR" || data == "N/A") ? 256 : (data)  ? data.length : 0
                    } else {
                        return row.networkType == "LR" ? "N/A" : data ? data.length : 0
                    }
                }
            },
            {
                data: 'metrics.Neighbors', title: 'Neighbor <br/>Count', defaultContent: "n/a",
                searchPanes: { show: false },
                createdCell: function (td, cellData, rowData, row, col) {
                    if (cellData == 2) {
                        $(td).css('color', 'darkorange')
                    } else if (cellData <= 1) {
                        $(td).css('color', 'red')
                    }
                    $(td).addClass('neighbors-' + rowData.id2)
                },
                render: function (data, type, row) {
                    if (type === 'sort' || type === 'type') {
                        return (data == "N/A") ? 256 : (data == "") ? -1 : data
                    } else {
                        return data
                    }
                }                
            },
            {
                data: 'metrics.Route Changes', title: 'Route <br/>Changes', defaultContent: "n/a",
                searchPanes: { show: false },
                createdCell: function (td, cellData, rowData, row, col) {
                    if (cellData > 1 && cellData <= 4) {
                        $(td).css('color', 'darkorange')
                    } else if (cellData > 4) {
                        $(td).css('color', 'red')
                    }
                },
                render: function (data, type, row) {
                    if (type === 'sort' || type === 'type') {
                        return (data == "N/A") ? 256 : (data == "") ? -1 : data
                    } else {
                        return data
                    }
                }
            },
            { data: 'metrics.PER', title: 'Error <br/>Count', defaultContent: "n/a", searchPanes: { show: false } },
            {
                data: 'deviceSecurity', title: 'Security', defaultContent: "Unknown"
            },
            { data: 'routeHtml', title: 'Route <br/>(from&nbsp;Hub)', searchPanes: { show: false } },
            {
                data: 'devDetail.lastActiveTS', title: "Last Activity", defaultContent: "unknown",
                searchPanes: { show: false },
                render: function (data, type, row) {
                    if (type === 'sort' || type === 'type') {
                        return data
                    } else if (type === 'display') {
                        if (row.devDetail && row.devDetail.lastActiveStrLocal) {
                            return row.devDetail.lastActiveStrLocal
                        } else {
                            return null
                        }
                    } else {
                        return data
                    }
                }
            },
            {
                data: 'listening', title: "Listening", defaultContent: "unknown"
            },
            {
                data: 'flirs', title: "FLiRS", defaultContent: "unknown"
            },
            {
                data: 'devDetail.zwavePlus', title: "Z-Wave Plus", defaultContent: "unknown"
            },
            {
                data: 'commandClasses', title: 'Command Class', visible: false,
                render: function (data, type, row) {
                    if (type == 'sp' && data) {
                        return data.map( (cc) => {
                            var ccVal = Number(cc);
                            return cc + " - " + CMD_CLASS_Names[ccVal];
                        })
                    } else {
                        return data;
                    }
                },
                defaultContent: "None",
                searchPanes: { orthogonal: 'sp', className: 'sp-only' },
                type: 'initialNumber',
                className: 'sp-only dtr-never'
            }
        ],
        "pageLength": -1,
        "rowId": 'id',
        "lengthChange": false,
        "paging": false,
        "scrollX": true,

        "responsive": !appSettings.enableResponsive ? false : {
            details: {
                type: 'none',
                target: ''
            } 
        },

        "stateSave": appSettings.stateSave,

        "fixedHeader": {
            header: (appSettings.disableFixedHeader) ? false : true,
            headerOffset: $('#pageTitle').height()
        },

        "layout": {
            "top4start": {
                buttons: [
                    {
                        text: 'Reload',
                        name: 'reload',
                        action: function ( e, dt, node, config ) {
                            refreshData(dt);
                        },
                        className: 'd-none'
                    }
                ]
            },
        
            "top3": "searchPanes",
            "top2start": {
                buttons: [ {
                    extend: 'colvis',
                    name: 'colvis',
                    columns: ':not(.dt-control):not(.sp-only)'
                }
                ]
            },
            "top2end": { 
                buttons: [ 
                {
                    text: "Export",
                    name: 'exports',
                    extend: 'collection',   
                    buttons: [
                        {
                            extend: 'csvHtml5',
                            text: 'Export CSV',
                            name: 'csvExport',
                            exportOptions: {
                                columns: 'th:not(.dt-control):not(.sp-only)'
                            }
                        },
                        {
                            extend: 'pdfHtml5',
                            text: 'Export PDF',
                            name: 'pdfExport',
                            orientation: 'landscape',
                            // columns: 'th:visible:nth-child(n+2):not(.sp-only)'
                            exportOptions: {
                                columns: 'th:not(.dt-control):not(.sp-only)'
                            }
                        }
                    ]
                }                
                ]
            },
            // Defaults (paging is noop since we disable paging in main options)
            //topStart: 'pageLength',
            topEnd: 'search',
            bottomStart: 'info',
            //bottomEnd: 'paging'
        },

        "searchPanes": {
            layout: appSettings.spLayout ? appSettings.spLayout : 'columns-3',
            className: "table-hover",
            cascadePanes: true,
            order: appSettings.spOrder ? JSON.parse(appSettings.spOrder) : defaultSearchPanesOrder()            
        }
    });

    $('.dtsp-panes .table').addClass("hover table-hover")
    $('#mainTable tbody').on('click', 'td.details-control', detailOnClick);
    $('#mainTable tbody').on('click', 'td.dt-control', detailOnClick);

    if (appSettings.stateSave) {
        $('#clearStateSaveBtn').show()
        $('#clearStateSaveBtn').on('click', (e) => {
            console.log("Clearing saved state");
            tableHandle.state.clear();
            location.reload();
        })
    }

}; // END doWork()

/**
 * Hide/Show Row details when control is clicked
 */
async function detailOnClick() {
    var tr = $(this).closest('tr');
    var row = tableHandle.row(tr);

    if (row.child.isShown()) {
        // console.log("Child shown, hidding..");
        row.child.hide();
        tr.removeClass('shown');
    }
    else {
        // console.log("Child opening..");
        if (row.data().hubDeviceId != '') {
            var content = await displayRowDetail(row)
            row.child(content).show();
            tr.addClass('shown');
        }
    }
}

function sendDebugData() {
    /* Globals:
        deviceDetailsMap
        neighborsMap
        tableContent
        tableHandle
    
    */
    var message = `
    # of Devices: ${tableContent.length}
    # of Devices with details: ${tableContent.filter(x => x.detail != null).length}
    Size of Neighbors Map (includes hub): ${neighborsMap.size}`

    if (enableDebug)
        hubLog("debug", message)
}

function setTheme(theme) {
    const htmlElement = document.documentElement;
    htmlElement.setAttribute('data-bs-theme', theme);
    localStorage.setItem('bsTheme', theme);
}

function initTheme() {
    const theme = localStorage.getItem('bsTheme');
    if (theme) {
        const htmlElement = document.documentElement;
        htmlElement.setAttribute('data-bs-theme', theme);    
    }
}

function populateNameBadge() {
    const el = document.getElementById('hubNameBadge');
    el.innerText=appData?.hub?.name
}

var currentColumnVis;
// MAIN Entry point
$(document).ready(function() {
    $('#themeLight').on('click', function() {
        setTheme('light');
    });
    $('#themeDark').on('click', function() {
        setTheme('dark');
    });
    initTheme();

    populateNameBadge();

    versionContainer = $('#platformVersion');
    versionContainer[0].innerText = `Platform Version: ${appData.hub?.firmwareVersion}`;
    versionContainer.removeClass('d-none');

    $('#mainTable').on('init.dt',
        function(e, settings, data) {
            console.log("Datatables INIT");

            const reorderButton = document.getElementById('panesConfigurationButton')
            const reorderPopoverOptions = {
                placement: "right",
                trigger: "click",
                title: "Filter Panes Configuration",
                content: () => getPaneOrderingContent(),
                container: 'body',
                customClass: "popover-400",
                html: true
            }
            new bootstrap.Popover(reorderButton, reorderPopoverOptions);

            // tableHandle.on('stateSaveParams.dt', function (e, settings, data) {
            //     console.log("State save...");
            //     currentColumnVis = data.columns.map((i, d) => { 
            //         return { vis: i.visible, colIndex: d, title: tableHandle.column(d).title() };
                    
            //     });
            //     console.log(currentColumnVis);
            // })

            tableHandle.on('buttons-action', function (e, buttonApi, dataTable, node, config) {
                if (config?.parent?.name === "colvis") {
                    name = buttonApi.text();
                    visible = buttonApi.active();
                    colIdx = config.columns;
                    colTitle = buttonApi.column(colIdx).title()
                    console.log(`colvis toggle: ${name} [${colTitle}] (idx: ${colIdx}) is ${visible ? "active" : "inactive"}`)
                }
            });
            cacheColumnToIndexMap(tableHandle);

        }
    )

    $('#sidebarMenu').on('hidden.bs.offcanvas', 
        event => {
            closeReorderPanePopover();
        }
    )

    $('#panesConfigurationButton').on('inserted.bs.popover', () => {
        var mainList = document.getElementById("reorderPanesList")
        sortablePanes = Sortable.create(mainList, {
            group: "panesOrder",
            handle: ".sort-handle",
            chosenClass: "list-group-item-success"
        });
        const removeList = document.getElementById("removedPanesList")
        sortablePanesDisabled = Sortable.create(removeList, {
            group: "panesOrder",
            handle: ".sort-handle",
            chosenClass: "list-group-item-success"
        });
        const layoutSelector = document.getElementById('layoutOption');
        layoutSelector.value = appSettings.spLayout;

        const searchPanes = getSearchPanes(tableHandle);

        // Popover is created and destroyed on each open, so listeners need to be added after each inserted event.
        $('#savePanesConfig').on('click', (e) => {
            addSpinner(e.target, "saveSpinner", "Saving...").then(() => {
                // Add small delay to give browser time to complete rendering the spinner
                setTimeout( () => {
                    updatePaneOrder(sortablePanes);
                    const newLayout = layoutSelector.value;
                    updatePanesLayout(newLayout);
                    rebuildPanes(searchPanes).then(() => {
                        appSettings.spOrder = JSON.stringify(sortablePanes.toArray());
                        appSettings.spDisabled = JSON.stringify(sortablePanesDisabled.toArray());
                        appSettings.spLayout = newLayout;
                        saveAppSettings({
                            spOrder: appSettings.spOrder,
                            spDisabled: appSettings.spDisabled,
                            spLayout: appSettings.spLayout
                        })
                        reorderPopover = bootstrap.Popover.getOrCreateInstance('#panesConfigurationButton')
                        reorderPopover.hide();
                        $('#applySpinner').remove()
                        recordAppEvent("paneOrder", sortablePanes.toArray(), "Search Panes Order updated");
                    });
                },50);
            })
        })

        $('#applyPanesConfig').on('click', (e) => {
            addSpinner(e.target, "applySpinner", "Updating...").then(() => {
                // Add small delay to give browser time to complete rendering the spinner
                setTimeout( () => {
                    updatePaneOrder(sortablePanes);
                    const newLayout = layoutSelector.value;
                    updatePanesLayout(newLayout);
                    rebuildPanes(searchPanes).then(() => {
                        appSettings.spOrder = JSON.stringify(sortablePanes.toArray());
                        appSettings.spDisabled = JSON.stringify(sortablePanesDisabled.toArray());
                        appSettings.spLayout = newLayout;
                        reorderPopover = bootstrap.Popover.getOrCreateInstance('#panesConfigurationButton')
                        reorderPopover.hide();
                        $('#applySpinner').remove()
                    });
                },50);
            })
        })

        $('#cancelPanesConfig').on('click', (e) => {
            reorderPopover = bootstrap.Popover.getOrCreateInstance('#panesConfigurationButton')
            reorderPopover.hide();
        })

    })

    doWork();

});

function updatePaneOrder(sortablePanes) {
    const newPaneList = sortablePanes.toArray();
    const searchPanes = getSearchPanes(tableHandle);

    const paneDetails = buildPaneDetailsList(searchPanes, newPaneList);
    reorderPanes(searchPanes, paneDetails);
}

function updatePanesLayout(newLayout) {
    const searchPanes = getSearchPanes(tableHandle);
    applyPanesLayout(searchPanes, newLayout);
}

function removeAppSetting(endpoint, settingName) {
    return writeAppSetting(endpoint, settingName, null)
}

function saveAppSettings(settingsData) {
    writeAppSettings(appData.links.settings, settingsData);
}

function writeAppSettings(endpoint, settingsData) {
    return axios.post(endpoint, settingsData);
}

function writeAppSetting(endpoint, settingName, value) {
    const data = {
    };
    data[settingName] = value;

    return axios.post(endpoint, data);

}

function recordAppEvent(name, value, descriptionText, isStateChanged = true) {
    const data = {
        name: name,
        value: value,
        descriptionText: descriptionText
    };
    return axios.post(appData.links.events, data)
}
