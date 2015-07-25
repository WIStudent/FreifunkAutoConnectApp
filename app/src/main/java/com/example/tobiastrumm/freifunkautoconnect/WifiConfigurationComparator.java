package com.example.tobiastrumm.freifunkautoconnect;

import android.net.wifi.WifiConfiguration;

import java.util.Comparator;

/**
 * Created by tobia_000 on 25.07.2015.
 */
public class WifiConfigurationComparator implements Comparator<WifiConfiguration> {
    @Override
    public int compare(WifiConfiguration wifiConfiguration, WifiConfiguration wifiConfiguration2) {
        return wifiConfiguration.SSID.compareTo(wifiConfiguration2.SSID);
    }
}
