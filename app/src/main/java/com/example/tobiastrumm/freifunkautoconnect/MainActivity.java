package com.example.tobiastrumm.freifunkautoconnect;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends ActionBarActivity implements AdapterView.OnItemClickListener{

    private ArrayList<Network> networks;

    // TODO: Just a placeholder. In the final version, networks should come from a file that can easily be updated.
    private static final Network[] NETWORKS = {
            new Network("\"muenster.freifunk.net\""),
            new Network("\"placeholder1\""),
            new Network("\"placeholder2\""),
    };

    private ListView lv;
    private NetworkAdapter na;
    private WifiManager wm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        networks = new ArrayList<Network>();
        networks.addAll(Arrays.asList(NETWORKS));

        wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        setupUI();

    }

    @Override
    protected void onStart() {
        super.onStart();

        // Check which Networks are already saved in the network configuration
        // TODO: Run this check asynchronous from UI thread
        checkActiveNetworks();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupUI(){
        lv = (ListView) findViewById(R.id.lv_networks);
        lv.setOnItemClickListener(this);
        na = new NetworkAdapter(this, networks);
        lv.setAdapter(na);
    }

    private void checkActiveNetworks(){
        // Check which Network is already added to the network configuration
        // WARNING: Could cause performance issues for large lists of networks and network configurations
        List<WifiConfiguration> wifiConf = wm.getConfiguredNetworks();
        for(Network n: networks){
            n.active = false;
            for(WifiConfiguration wc: wifiConf){
                if(wc.SSID.equals(n.ssid)){
                    n.active = true;
                }
            }
        }
        na.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Get the Network object
        Network n = networks.get(position);
        // If the network is already saved in the network configuration, remove it
        if(n.active){
            n.active = false;
            List<WifiConfiguration> wificonf = wm.getConfiguredNetworks();
            for(WifiConfiguration wc: wificonf){
                if(wc.SSID.equals(n.ssid)){
                    wm.removeNetwork(wc.networkId);
                }
            }
        }
        // If the network is not in the network configuration, add it.
        else{
            n.active = true;
            // Add Network to network configuration
            WifiConfiguration wc = new WifiConfiguration();
            wc.SSID = n.ssid;
            wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            int networkId = wm.addNetwork(wc);
            wm.enableNetwork(networkId, false);
        }
        // Save configuration
        wm.saveConfiguration();
        // Notify the NetworkAdapter that the content of ArrayList networks was changed.
        na.notifyDataSetChanged();
    }
}
