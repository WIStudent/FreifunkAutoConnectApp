package com.example.tobiastrumm.freifunkautoconnect;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.support.v4.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by tobia_000 on 28.07.2015.
 */
public class RemoveAllNetworksService extends IntentService {
    public static final String INPUT_NETWORKS = "input_networks";
    public static final String STATUS_TYPE = "status_type";
    public static final String STATUS_TYPE_PROGRESS = "type_progress";
    public static final String STATUS_TYPE_FINISHED = "type_finished";
    public static final String STATUS_PROGRESS = "status_progress";
    public static final String BROADCAST_ACTION = "com.example.tobiastrumm.freifunkautoconnect.removeallnetworkservice.BROADCAST";
    ArrayList<Network> networks;

    public RemoveAllNetworksService(){
        super("RemoveAllNetworkService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        networks = intent.getParcelableArrayListExtra(INPUT_NETWORKS);

        // Remove all networks from network configuration
        int i = 0;
        WifiManager wmAsync = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        List<WifiConfiguration> wificonf = wmAsync.getConfiguredNetworks();
        if(wificonf != null) {
            Collections.sort(wificonf, new WifiConfigurationComparator());
        }
        for(Network n: networks){
            if(n.active){
                n.active = false;
                if(wificonf != null) {
                    int index = Collections.binarySearch(wificonf, n.ssid, new WifiConfigurationSSIDComparator());
                    if(index >= 0) {
                        wmAsync.removeNetwork(wificonf.get(index).networkId);
                    }
                }
            }
            i++;
            publishProgress(i);
        }
        // Save configuration
        wmAsync.saveConfiguration();

        responseFinished();
    }

    private void publishProgress(int i) {
        Intent localIntent = new Intent(BROADCAST_ACTION);
        localIntent.putExtra(STATUS_TYPE, STATUS_TYPE_PROGRESS);
        localIntent.putExtra(STATUS_PROGRESS, i);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private void responseFinished(){
        Intent localIntent = new Intent(BROADCAST_ACTION);
        localIntent.putExtra(STATUS_TYPE, STATUS_TYPE_FINISHED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }
}
