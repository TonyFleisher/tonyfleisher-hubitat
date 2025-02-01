#!/bin/sh

## Examples:
# hpm manifest-create packageManifest.json --name="My Package" --author="Dominick Meglio" --version=1.0 --heversion=2.1.9 --datereleased=2019-04-07
# hpm manifest-add-app packageManifest.json --location=https://raw.githubusercontent.com/app.groovy --required=true --oauth=false
# hpm manifest-add-driver packageManifest.json --location=https://raw.githubusercontent.com/driver1.groovy --required=true
# hpm manifest-add-driver packageManifest.json --location=https://raw.githubusercontent.com/driver2.groovy --required=false

hpm manifest-create packageManifest.json --name="Hubitat Z-Wave Mesh Details" --author="Tony Fleisher" --version=0.1.4 --heversion=2.2.4 --datereleased=2020-11-26
hpm manifest-add-app packageManifest.json --location=https://raw.githubusercontent.com/TonyFleisher/tonyfleisher-hubitat/beta/Apps/mesh-details/mesh-details.groovy --required=true --oauth=true
