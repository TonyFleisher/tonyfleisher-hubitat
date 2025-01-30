/**
 * Copyright 2023 Tony Fleisher
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
*/

logToConsole = false

function loadAxios() {
	//return $.getScript('https://unpkg.com/axios/dist/axios.min.js', function() {
	return $.getScript('https://unpkg.com/axios@1.6.7/dist/axios.min.js', function() {
		console.log("axios loaded")
	});
}

async function getZWaveDeviceIds() {
	var devList = await getZwaveList()
	if (!window.axios) {
		await loadAxios()
	}
	console.log("Collecting zwave device ids")
	var deviceIds = devList.reduce( (acc, val) => {
		if (acc.includes(val.hubDeviceId)) {
			console.log(`DUPLICATE device in ZwaveListing: ${val.hubDeviceId}`);
			return acc;
		}
		if (val.hubDeviceId) {
			acc.push(val.hubDeviceId); 
		}
		return acc;
	}, [])
	console.log("DeviceIds: " + deviceIds.toString())
	return deviceIds
}

var zwaveDetailsJson 
// Get transformed list of devices (see transformDevice) from hubitat zwave details webpage
async function getZwaveList() {
	if (!window.axios) {
		await loadAxios()
	}

	const instance = axios.create({
		timeout: 5000
		});

	return instance
		.get('/hub/zwaveDetails/json')
		.then(response => {
			zwaveDetailsJson = response.data;

			if (response.data.enabled == false) {
				addErrorAlert("Z-Wave Disabled")
				throw "No Z-Wave data available";
			}

			if (response.data.isRadioUpdateNeeded == true) {
				showRadioUpdateWarning()
			}
			return collectZwaveList(response.data)
		})
		.catch(error => { 
			console.error(error);
			updateLoading("Error", error);
			hubLog("error", `zwaveInfo: Error getting zwave Info: ${error}`)
		} );
}

function collectZwaveList(zwaveDetailsJson) {
	var zwaveList = [];

	var zwNodes = zwaveDetailsJson.nodes;
	var zwDevices = zwaveDetailsJson.zwDevices;
	var seenLR = false;
	var seenNodes = [];

	return zwNodes.map ( node => {
		var nodeId = node.nodeId;
		var isLR = nodeId > 255 ? true : false;
		if (seenNodes.includes(nodeId)) {
			console.log(`IGNORE DUPLIACTE: ${nodeId}`);
			return null;
		} else {
			seenNodes.push(nodeId);
		}
		// if (enableDebug && isLR) {
		// 	console.log(`collectZwaveList: LR Device found: ${JSON.stringify(node)}`)
		// }

		if (nodeId > 255) { seenLR = true}
		var zwDevice = zwDevices[nodeId]; // This will be null/undefined if there is no assigned device

		if (!zwDevice) {
			zwDevice = getZWDevicePlaceholder(node)
		}

		// "01 -> 08 -> 0C -> 1B 100kbps"
		var routesText = node.route;
		var routers = routesText ? routesText.split(' -> ') : []
		var routersForDisplay = ["INIT"] // Should always be replaced below; if seen in final output, needs investigation
		var routersList = []
		
		var connectionSpeed = "Unknown"
		if (routers.length > 0) {
			var lastParts = routers.splice(-1,1) // Remove Last element (this device w/ speed)
			routers.splice(0,1) // Remove first element (always 01; hub)
			connectionSpeed = lastParts[0].split(' ')[1]
			routersList = routers
			routersForDisplay = routers.map(r => useHex() ? "0x" + r : parseInt("0x"+r))

			if (routers.length == 0 && connectionSpeed != 'Unknown') {
				routersForDisplay = ['DIRECT']
			}
	
		} else {
			routersForDisplay = ['Unknown']
		}
	

		// FIXME: Don't format data
		var rtt = node.averageRtt + "ms";
		var lwr = node.lwrRssi ? (node.lwrRssi + "dB") : "";
		var statMap = {
			"PER": node.per,
			"RTT Avg": rtt,
			"LWR RSSI": lwr,
			"Neighbors": isLR ? "N/A" : node.neighbors,
			"Route Changes": isLR ? "N/A" : node.routeChanges
		};

		var isListening = node.listening ? "yes" : "no";
		var isFlirs = node.beaming ? "yes" : "no"; // as of 2.3.8, Node details uses "beaming: true" for FLiRS capable devices (and has listening: true)

		var dni = zwDevice.deviceNetworkId;
		var label = zwDevice.displayName;
		var hubDeviceId = zwDevice.id;
		var deviceLink = hubDeviceId ? "/device/edit/" + hubDeviceId : "";

		var deviceData = {
			id: dni, // hexId
			id2: nodeId, // intId
			devIdDec: nodeId,
			networkType: isLR ? "LR" : "Mesh",
			metrics: statMap,
			routers: routersForDisplay, // 	['0x06']
			routersList: routersList, // list of routers (hex), not including hub; ['06']
			label: label, // device displayName
			type: translateDeviceType(node.zwaveType), // "Power Switch Binary"
			manufacturer: node.zwaveManufacturer,
			deviceLink: deviceLink, // "/device/edit/2193"
			hubDeviceId: hubDeviceId, // "2193"
			deviceSecurity: node.security, // "None"
			routeHtml: routersForDisplay.reduce( (acc, v, i) => (v == 'DIRECT' || v == 'Unknown') ? v : acc + ` ->${v}`, "") + ( (routersForDisplay[0] == 'DIRECT' || routersForDisplay[0] == 'Unknown') ? '' : ` -> ${useHex() ? "0x" + dni : nodeId}`) ,
			deviceStatus: node.nodeState,
			connection: connectionSpeed,
			// commandClasses: node.commandClass, # Seeems to always be empty: 2.3.9
			listening: isListening,
			flirs: isFlirs,
			zwNode: node,
			zwDevice: zwDevice
		}
		return deviceData;
	}).filter(value => value !== null);

}

function getZWDevicePlaceholder(node) {
	var zwDevice = {
		"deviceNetworkId": node.nodeId.toString(16).toUpperCase(),
		"isPlaceholder": true,
		"displayName": "NO DEVICE"
	}

	return zwDevice;
}

async function getZwaveVersion() {
	if (!window.axios) {
		await loadAxios()
	}

	const instance = axios.create({
		timeout: 1000
		});

	return instance.get('/hub/zwaveVersion').then(response => {			
		zStr = response.data;
		versionInfo = parseVersionReportToJson(zStr);
		return versionInfo;
	})
}

/**
 * Parse the return from /hub/zwaveVersion into an object.
 * Example input: 
 * 		VersionReport(zWaveLibraryType:7, zWaveProtocolVersion:7, zWaveProtocolSubVersion:18, firmware0Version:7, firmware0SubVersion:18, hardwareVersion:1, firmwareTargets:1, targetVersions:[[target:1, version:7, subVersion:18]])
 * @param {String} input 
 * @returns 
 */
function parseVersionReportToJson(input) {
    // Extract the content inside "VersionReport()"
    const content = input.match(/VersionReport\((.*)\)/)?.[1];
    if (!content) throw new Error("Invalid input format");

    const result = {};
       // Split into key-value pairs by commas, ignoring commas inside nested brackets
    const pairs = content.split(/,\s*(?![^\[]*\])/);

    pairs.forEach(pair => {
        const [key, value] = pair.match(/^([^:]*):\s*(.*)$/).splice(1,2)
		if (value.startsWith('[')) {
			// Convert to valid JSON and parse
			const fixedArray = value
				.replace(/^\[(.*)\]/, '$1') // Remove extra outer brackets
				.replace(/\[(.*)\]/g, '{$1}') // replace inner brackets
				.replace(/(\w+):/g, '"$1":');  // Quote key
			result[key] = JSON.parse(`[${fixedArray}]`);
		} else if (!isNaN(value)) {
			// Convert to number
			result[key] = Number(value);
		} else {
			// Leave as string
			result[key] = value;
		}
    });

    return result;
}

function updateDevicesInApp(devices) {
	var updateLink = "/installedapp/update/json"
	var appLink = appData.links.self
	var appId = appData.appId

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
		// url: `/installedapp/configure/${appId}/devicesPage`

	}

	if (enableDebug) {
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
	var type = deviceType.replace(" ","_");
	switch (type) {
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
			return "Switch On/Off"
		case "SPECIFIC_TYPE_POWER_SWITCH_BINARY":
			return "Power Switch On/Off"
		case "SPECIFIC_TYPE_SCENE_SWITCH_BINARY":
			return "Scene Switch"
		case "SPECIFIC_TYPE_POWER_STRIP":
			return "Power Strip"
		case "SPECIFIC_TYPE_SIREN":
			return "Siren"
		case "SPECIFIC_TYPE_VALVE_OPEN_CLOSE":
			return "Valve Open/Close"
		case "SPECIFIC_TYPE_COLOR_TUNABLE_BINARY":
			return "On/Off Color Light"
		case "SPECIFIC_TYPE_IRRIGATION_CONTROLLER":
			return "Irrigation Controller"

		case "GENERIC_TYPE_SWITCH_MULTILEVEL": // 0x11
			return "Dimmer Switch"
		case "SPECIFIC_TYPE_CLASS_A_MOTOR_CONTROL":
			return "Class A Motor Control"
		case "SPECIFIC_TYPE_CLASS_B_MOTOR_CONTROL":
			return "Class B Motor Control"
		case "SPECIFIC_TYPE_CLASS_C_MOTOR_CONTROL":
			return "Class C Motor Control"
		case "SPECIFIC_TYPE_MOTOR_MULTIPOSITION":
			return "Motor Multiposition"
		case "SPECIFIC_TYPE_POWER_SWITCH_MULTILEVEL":
			return "Dimmer Switch"
		case "SPECIFIC_TYPE_SCENE_SWITCH_MULTILEVEL":
			return "Scene Switch Multilevel"
		case "SPECIFIC_TYPE_FAN_SWITCH":
			return "Fan Switch"
		case "SPECIFIC_TYPE_COLOR_TUNABLE_MULTILEVEL":
			return "Dimmable Color Light"

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
			return "On/Off Switch"
		case "SPECIFIC_TYPE_SWITCH_TOGGLE_BINARY":
			return "On/Off Switch"
		case "SPECIFIC_TYPE_SWITCH_TOGGLE_MULTILEVEL":
			return "On/Off Dimmable Switch"

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

/**
 * Get the default list of Search Panes (to use with searchPanes.order configuration)
 * @returns List of Column Labels for Search Panes
 */
function defaultSearchPanesOrder() {
	var labels = ['Network Type', 'Repeater', 'Status', 'Room', 'Security', 'Connection Speed', 'RTT Avg', 'RTT StdDev', 
		'LWR RSSI']
	labels.push('Listening')
	labels.push('FLiRS')
    labels.push('Z-Wave Plus')
    labels.push('Device Type')
    labels.push('Manufacturer')
    labels.push('Command Class')
	console.log("Default SearchPanes: " + labels)
    return labels
}

/**
 * Create html from list of items
 * @param {String[]} labelsList 
 * @param {String} listType - HTML element to use for list container ("ul")
 * @param {String} listId - HTML id for list container
 * @param {String} listClass - HTML class for list container (list-group)
 * @param {String} itemClass - HTML class for list item (list-group-item)
 */
function makeListItemsHtml(labelsList, listType="ul", listId, listClass="list-group", itemClass="list-group-item") {
	var html = ""

	html += `<${listType} id="${listId}" class="${listClass}">`;
	labelsList.forEach(label => html += `<li class="${itemClass}">${label}</li>`);
	html += `</${listType}>`;
	return html;
}

 /**
  * Create Content element from templates
  * @param {*} contentTemplateId - Outer content template (e.g. <template><div></div></template>)
  * @param {*} contentId 
  * @param {*} contentInsertSelector 
  * @param {*} labelsList 
  * @param {*} newContainerId 
  * @param {*} containerTamplateId - List item container template
  * @param {*} itemTemplateId - List item template
  * @param {*} itemInsertSelector 
  * @param {*} itemLabelSelector 
  * @returns 
  */
function makeContentWithListFromTemplates(contentTemplateId, contentId, contentInsertSelector, labelsList, newContainerId, containerTamplateId, 
	itemTemplateId, itemInsertSelector, itemLabelSelector) {

	/* */
	const contentTemplate = document.querySelector(`#${contentTemplateId}`);
	const newContent = contentTemplate.content.cloneNode(true);
	newContent.firstElementChild.id = contentId;
	const contentParent = newContent.querySelector(contentInsertSelector);

	listFragment = makeListItemsFragmentFromTemplate(labelsList, newContainerId, containerTamplateId, 
		itemTemplateId, itemInsertSelector, itemLabelSelector);

	contentParent.appendChild(listFragment);
	return newContent;
}


/**
 * Create htmlElement from list of items
 * 
 * @param {*} labelsList 
 * @param {*} newContainerId
 * @param {*} containerTamplateId 
 * @param {*} itemTemplateId 
 * @param {*} itemInsertSelector - Selector within containerTemplate where items are inserted
 * @param {*} itemLabelSelector 
 * @returns 
 */
function makeListItemsFragmentFromTemplate(labelsList, newContainerId, containerTamplateId, itemTemplateId, itemInsertSelector, itemLabelSelector) {

	const containerTemplate = document.querySelector(`#${containerTamplateId}`);
	const newContainer = containerTemplate.content.cloneNode(true);
	newContainer.firstElementChild.id = newContainerId;
	const listContainer = newContainer.querySelector(itemInsertSelector);

	const itemTemplate = document.querySelector(`#${itemTemplateId}`);

	labelsList.forEach(label => {
		const newItem = itemTemplate.content.cloneNode(true);
		newItem.firstElementChild.setAttribute('data-id', label);
		const loc = newItem.querySelector(itemLabelSelector);
		loc.insertAdjacentHTML('beforeEnd',label);
		listContainer.appendChild(newItem);

	});

	return newContainer;
}

/**
 * Add Spinner Icon to element
 * @param {HTMLElement} el 
 * @param {String} spinnerId - node id of the spinner
 * @param {String} desc - Spinner description (visually hidden)
 */
function addSpinner(el, spinnerId, desc) {
    return new Promise((resolve) => {
        el.insertAdjacentHTML('afterBegin',
            `<div id="${spinnerId}" class="spinner-border spinner-border-sm" role="status">` +
                `<span class="visually-hidden">${desc}</span>` +
            '</div>');
        resolve(); // Resolve immediately after adding the spinner
    });
}


function hubLog(level,log) {
  if (window.axios) {
	const instance = axios.create({
		timeout: 15000 // always async fire+forget, so don't care about timeouts here
	});
	if (logToConsole) { console.log(level + ":" + log)}
	return instance
	.post(appData.links.remoteLog, { level: level, log: log})
  }
}

/**
 * Update loading messages in page header to show load progress
 * @param {*} msg1 
 * @param {*} msg2 
 */
function updateLoading(msg1, msg2) {
	if (window.mainAppStarted) {
		$('#loading1').text(msg1);
		$('#loading2').text(msg2);
	}
	if (enableDebug) {
		if (msg1 || msg2) {
			hubLog("debug", `${msg1} - ${msg2}`)
		}
	}
}

/**
 * Update main page header message
 * @param {*} msg 
 */
function updateHeaderMessage(msg) {
	if (window.mainAppStarted) {
		$('#message1').text(msg)
	}
}

function addErrorAlert(msg) {
	if (window.mainAppStarted) {
		$('#errorAlerts').removeClass('d-none').append('<p>'+msg+'</p>')
	}
}

function addWarnAlert(msg) {
	if (window.mainAppStarted) {
		$('#warningAlerts').removeClass('d-none').append('<p>'+msg+'</p>')
	}
}

/**
 * Display toast with link to Platform Z-Wave Details page when firmware update is available
 */
function showRadioUpdateWarning() {
	if (window.mainAppStarted) {
		addToast('radioUpdate','bg-warning-subtle','Z-Wave Firmware Update Available',
			'<p>Firmware update is available from the <a class="icon-link" href="/hub/zwaveInfo" target="_blank">Z-Wave Details' +
			'<i class="bi bi-box-arrow-up-right"></i></a> page</p>'
		)
	}
}

initToast=false
function createToastContainer() {
	var html='<div id="mainToastContainer" class="toast-container position-fixed top-0 end-0 p-2"></div>'
	$(html).appendTo($('body'))
	initToast=true
}

function addToast(id, toastClass, header, body) {
	if (window.mainAppStarted) {
		if (!initToast) { createToastContainer() }
		var html=`<div id="${id}" class="toast ${toastClass}" data-bs-autohide="false" aria-live="assertive" aria-atomic="true">` +
		'<div class="toast-header">' + header +
		'<button type="button" class="btn-close ms-auto" data-bs-dismiss="toast" aria-label="Close"></button>' + '</div>' +
		'<div class="toast-body">' + body + '</div>';
		const newToast = $(html)
		newToast.appendTo('#mainToastContainer');
		const toastBootstrap = bootstrap.Toast.getOrCreateInstance(newToast);
		toastBootstrap.show()
	}
}


function useHex() {
	return appSettings.nodeBase === "base16"
}

function hasDeviceAccess() {
	return appSettings.permitDeviceAccess
}

function secs2UptimeStr(uptimeSeconds) {
	var s = uptimeSeconds%60;
	var m = ((uptimeSeconds - s)%3600)/60;
	var h = Math.floor(uptimeSeconds/3600);
	return `${h}h ${m}m ${s}s`;
}