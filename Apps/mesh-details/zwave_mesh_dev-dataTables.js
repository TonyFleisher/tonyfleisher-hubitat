/**
 * Misc. Functions to work with DataTables
 */

/**
 * Get titles of all columns in given DataTable
 * @param {*} tableHandle 
 * @returns 
 */
function getColumnLabels(tableHandle) {
    const columns = tableHandle.columns()
    const labels = []
    columns.every (function () { 
        labels.push(this.title())
    })
    return labels;
}

// Function to create a dropdown list
function createDropdown(sanitizedNames) {
    let dropdownHTML = `
        <div class="dropdown">
            <button class="btn btn-secondary dropdown-toggle" type="button" id="dropdownMenuButton" data-bs-toggle="dropdown" aria-expanded="false">
                Toggle Search Panes
            </button>
            <ul class="dropdown-menu" aria-labelledby="dropdownMenuButton">`;

    sanitizedNames.forEach((name, index) => {
        dropdownHTML += `
            <li>
                <label class="dropdown-item">
                    <input type="checkbox" class="pane-toggle" data-pane-index="${index}" checked>
                    ${name}
                </label>
            </li>`;
    });

    dropdownHTML += `</ul></div>`;
    return dropdownHTML;
}

function getPanes(tableHandle) {
    return tableHandle.context[0]._searchPanes.s.panes
}

function getPaneName(pane) {
    return pane.s.name
}


function getPaneLabels(tableHandle) {
    const panes = getPanes(tableHandle);
    return panes.map(p => 
        getPaneName(p)
    );
}


/**
 * Reconfigure show value for the given pane.
 * @param {*} pane - Required; pane 
 * @param {*} show  - Required; values: [true, false, undefined]. If undefined, the auto calc will determine if the pane is included
 * @param {*} redraw  - Optional; If true, the search panes will be rebuilt.
 */
function setShowPane(pane, show=undefined, redraw=false) {
    if (!pane) {
        throw "setShowPane(): pane parameters is required!";
    }
    if (show === -1) {
        throw "setShowPane(): show parameter is required"
    }

    pane.s.colOpts.show = show;
    if (redraw) {
        // Rebuild searchPanes container
        pane.s.dt.searchPanes.rebuildPane()
    }

}

function getSearchPanes(tableHandle) {
    return tableHandle.searchPanes().context[0]._searchPanes
}

/**
 * Update order and set show for search panes
 * @param {SearchPanes} searchPanes - SearchPanes object (see getSearchPanes)
 * @param {PaneDetail[]} paneDetailsList - List of PaneDetail in order to show
 * PaneDetail: 
 * * name: Name of Pane
 * * show: Show/Hide pane (or auto compute). Values: true, false, undefined (auto)
 * * 
 */
function reorderPanes(searchPanes, paneDetailsList) {
    var newPanes = []
    searchPanes.c.order = paneDetailsList.map(detail => detail.name);
    paneDetailsList.forEach( detail => {
        const pane = searchPanes._findPane(detail.name);
        setShowPane(pane, detail.show);
        newPanes.push(pane);
    });

    searchPanes.s.panes = newPanes;
}

function applyPanesLayout(searchPanes, newLayout) {
    searchPanes.c.layout = newLayout;
    searchPanes.s.panes.map (p => p.resize(newLayout))
}

function rebuildPanes(searchPanes) {
    return new Promise( (resolve) => {
        searchPanes.s.dt.searchPanes.rebuildPane();
        resolve();
    })
}

var dtColumnNameMap = {};
function cacheColumnToIndexMap (tableApi) {
    dtColumnNameMap = Object.fromEntries(tableApi.columns().header().toArray().map ( (e,i) => { return {0: $(e).text(), 1: i}} ));
    return dtColumnNameMap;
}

/**
 * Build paneDetailsList to send to reorderPanes
 * @param {SearchPanes} searchPanes 
 * @param {String[]} paneNamesList - Ordered list of pains by name. Any panes not listed will be hidden. 
 * @param {Boolean} show - true, false, undefined (auto)
 */
function buildPaneDetailsList(searchPanes, paneNamesList, show) {
    var paneDetailList = []

    // For each pane
    paneNamesList.forEach( theName => {
        pane = searchPanes._findPane(theName);
        if (pane) {
            paneDetailList.push({name: theName, show: show});
        } else {
            if (dtColumnNameMap[theName]) {
                idx = dtColumnNameMap[theName];
                pane = new searchPanes.s.paneClass(searchPanes.s.dt.settings()[0], searchPanes.c, idx, searchPanes.dom.panes);
                searchPanes.s.panes.push(pane);
                paneDetailList.push({name: theName, show: show});
            } else {
                console.log(`Error: buildPaneDetailsList: Invalid name: ${theName}`)
            }
        }
    })
    // collect remaining panes and set show to false
    searchPanes.s.panes.forEach( pane => {
        const name = pane.s.name;
        if (! paneNamesList.includes(name)) {
            paneDetailList.push({name: name, show: false});
        }
    })

    return paneDetailList;
}


function getPaneOrderingContent() {
    const contentTemplate = document.querySelector('#reorderPanesContentTemplate');
    
    const paneLabels = appSettings.spOrder ? JSON.parse(appSettings.spOrder) : defaultSearchPanesOrder();
    const paneOrderCol = makeContentWithListFromTemplates('reorderPanesColumnTemplate', 'reorderPanesListContainer', '.col', 
        paneLabels,"reorderPanesList","reorderPanesListTemplate","reorderPanesListItemTemplate","ul","li>span");
    
    
    const disabledPanes = appSettings.spDisabled ? JSON.parse(appSettings.spDisabled) : [];
    const paneRemovedCol = makeContentWithListFromTemplates('reorderPanesColumnTemplate', 'removedPanesListContainer', '.col', 
        disabledPanes,"removedPanesList","reorderPanesListTemplate","reorderPanesListItemTemplate","ul","li>span");
        

    const newContent = contentTemplate.content.cloneNode(true);
    // newContent.firstElementChild.id = "reorderPanesContainer";
    const contentParent = newContent.querySelector('#reorderPanesSortableRow');
    contentParent.appendChild(paneOrderCol);
    contentParent.appendChild(paneRemovedCol);

    contentParent.querySelector('#reorderPanesListContainer').insertAdjacentHTML('afterBegin',"<div>Visible Filters</div>");
    contentParent.querySelector('#removedPanesListContainer').insertAdjacentHTML('afterBegin',"<div>Hidden Filters</div>");

    return newContent;
}

function closeReorderPanePopover() {
    bootstrap.Popover.getOrCreateInstance('#panesConfigurationButton').hide();
}