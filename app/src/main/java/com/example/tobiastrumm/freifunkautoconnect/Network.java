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
        return ssid.compareToIgnoreCase(o.ssid);
    }
}
