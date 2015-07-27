package com.example.tobiastrumm.freifunkautoconnect;

/**
 * Created by Tobias on 06.05.2015.
 */
public class Network implements Comparable<Network>{

    String ssid = "";
    boolean active = false;

    public Network(String ssid){
        this.ssid = ssid;
    }

    @Override
    public int compareTo(Network o) {
        // Cut of the quotation marks before compairing the SSIDs.
        String ssid1 = ssid.substring(1,ssid.length()-1);
        String ssid2 = o.ssid.substring(1, o.ssid.length()-1);
        return ssid1.compareToIgnoreCase(ssid2);
    }
}
