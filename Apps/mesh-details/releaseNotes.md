v1.1.34

* New UI design (bootstrap theme)
* Offcanvas menu for Info / Actions
    * Switch between Light and Dark themes
    * Refresh Statistics
    * View Topology
    * Filter Panes Configuration
    * (experimental; advanced) Clear Saved Table State
* Refactor to use HE File Manager for css and javascript
* BUGFIX: Show empty routes as "Unknown" (not "DIRECT")
* Remove column config in App
* Add Room column and filter
* Add Command Classes filter
* Column Visibility Controls (Choose which columns to display in table) 
* Show all filters and columns by default (Column Visibility Controls can remove unwanted columns)
* Export data to pdf or csv
* Activate controls on search panes
* Add option to display decimal with hex id in Node column (suggested by jtp10181)
* Add option to choose search panes layout
* Use Fixed headers on table (can be disabled in advanced options)
* Add Responsive option - hide columns that don't fit on screen (can be enabled in advanced options)
* Cleanup Deprecated code
* Toast message (info card) if a z-wave firmware update is available
* Remove Deprecated code (requires HEVersion to 2.3.8)
* Remove experimental embeded - inline style