# Freifunk Auto Connect App

&copy; 2015 [Tobias Trumm](mailto:tobiastrumm@uni-muenster.de) licensed under GPLv3  
Freifunk Logo: [freifunk.net](http://freifunk.net),  [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/)

## Info
The Freifunk Auto Connect App makes it easier to add multiple Freifunk SSIDs to the network configuration of your Android device.

## How to add missing SSIDs
Please visit the [freifunk-ssids repository](https://github.com/WIStudent/freifunk-ssids) to find out how to submit missing SSIDs.

## Where does the position and online status data about the Freifunk access points come from?
This [python script](https://github.com/WIStudent/FreifunkNodeLocationConverter) checks https://api.freifunk.net/data/freifunk-karte-data.json for updated position data and converts it into a format suitable for the Freifunk Auto Connect App. The script is executed every 10 minutes on a Raspberry Pi and the result is uploaded to a webserver. The app uses the 'If-Modified-Since' field in the http request to check if a new version was uploaded since its last check.

## Build status
Build status on [Travis CI](https://travis-ci.org/):
[![Build Status](https://travis-ci.org/WIStudent/FreifunkAutoConnectApp.svg?branch=master)](https://travis-ci.org/WIStudent/FreifunkAutoConnectApp)

## Availability in stores

This app is also available on [F-Droid](https://f-droid.org/packages/com.example.tobiastrumm.freifunkautoconnect/):

[![Matekarte on F-Droid](https://chart.googleapis.com/chart?chs=150x150&cht=qr&chl=https://f-droid.org/app/com.example.tobiastrumm.freifunkautoconnect&choe=UTF-8)](https://f-droid.org/app/com.example.tobiastrumm.freifunkautoconnect)

## Articles about this app
- [Appvorstellung: Freifunk Auto Connect](http://freifunk-kreis-steinfurt.de/2016/03/appvorstellung-freifunk-auto-connect/)