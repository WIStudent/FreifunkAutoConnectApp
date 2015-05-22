package com.example.tobiastrumm.freifunkautoconnect;

import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity implements AdapterView.OnItemClickListener, RemoveAllDialogFragment.OnRemoveAllListener, AddAllDialogFragment.OnAddAllListener{

    private static String DIRECTORY = "freifunkautoconnect";
    private static String USER_SSIDS_FILE = "user_ssids.csv";

    private static String TAG = MainActivity.class.getSimpleName();


    private ArrayList<Network> networks;

    private ListView lv;
    private NetworkAdapter na;
    private WifiManager wm;



    private void getSSIDs() throws IOException {
        InputStreamReader is = new InputStreamReader(getAssets().open("ssids.csv"));
        BufferedReader reader = new BufferedReader(is);
        String line;
        while ((line = reader.readLine()) != null) {
            networks.add(new Network(line));
        }

        // Read user defined ssids
        // Check if external storage is available
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            File user_ssids = new File(Environment.getExternalStorageDirectory() + File.separator + DIRECTORY + File.separator + USER_SSIDS_FILE );
            // Check if file exists
            if(!user_ssids.exists()){
                // If not, create the file
                Log.i(TAG, "Start creation of user_ssids.csv file");
                user_ssids = createUserSSIDFile();
            }
            else{
                Log.i(TAG, "user_ssids.csv already exists");
            }
            // If the file was found/created:
            if(user_ssids != null){
                is = new InputStreamReader(new FileInputStream(user_ssids));
                reader = new BufferedReader(is);
                while ((line = reader.readLine()) != null) {
                    networks.add(new Network(line));
                }
            }
            else{
                Log.w(TAG, "Could not find or create user_ssids file.");
            }

        }
    }

    private File createUserSSIDFile(){
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            File directory = new File(Environment.getExternalStorageDirectory() + File.separator + DIRECTORY);
            if(!directory.exists()){
                // Create directory
                Log.i(TAG, "Create freifunkautoconnect directory");
                directory.mkdir();
            }
            File user_ssids = new File(directory, USER_SSIDS_FILE);
            try {
                // Create empty file
                Log.i(TAG, "Create empty user_ssids.csv file");
                user_ssids.createNewFile();
            }
            catch (IOException e) {
                Log.w(TAG, "Could not create user_ssids.csv file.");
                return null;
            }

            // Make sure that the new file will be visible if the device is connected to a pc over USB cable.
            Log.i(TAG, "Scan for user_ssids.csv file");
            MediaScannerConnection.scanFile(
                    this,
                    new String[]{directory.getAbsolutePath(), user_ssids.getAbsolutePath(), },
                    null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i(TAG, "Scanned " + path + ":");
                            Log.i(TAG, "-> uri=" + uri);
                        }
                    }
            );
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Use Toolbar instead of ActionBar. See:
        // http://blog.xamarin.com/android-tips-hello-toolbar-goodbye-action-bar/
        // https://stackoverflow.com/questions/29055491/android-toolbar-for-api-19-for-api-21-works-ok
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        networks = new ArrayList<Network>();
        try{
            getSSIDs();
        }
        catch(IOException e){
            // Could not read SSIDs from csv file.
        }


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

        // Setup SearchView
        SearchManager searchManager = (SearchManager)getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchItem = menu.findItem(R.id.search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                na.getFilter().filter(newText);
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_info) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void onClickAddAllNetworks(View view){
        AddAllDialogFragment df = new AddAllDialogFragment();
        df.setListener(this);
        df.show(this.getFragmentManager(),"");
    }

    private class AddAllTask extends AsyncTask<Void, Integer, Void> {
        ProgressDialog progress;
        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            // Update progressbar
            progress.setProgress(values[0]);
        }

        @Override
        protected Void doInBackground(Void... params) {
            // Add all networks to network configuration
            int i = 0;
            WifiManager wmAsync = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            for(Network n: networks){
                if(!n.active){
                    n.active = true;
                    // Create WifiConfiguration and add it to the known networks.
                    WifiConfiguration wc = new WifiConfiguration();
                    wc.SSID = n.ssid;
                    wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    int networkId = wmAsync.addNetwork(wc);
                    wmAsync.enableNetwork(networkId, false);
                }
                i++;
                publishProgress(i);
            }
            // Save configuration
            wmAsync.saveConfiguration();
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Create ProgressDialog and show it
            progress = new ProgressDialog(MainActivity.this);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setIndeterminate(false);
            progress.setMax(networks.size());
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            // Notify the NetworkAdapter that the content of ArrayList networks was changed.
            na.notifyDataSetChanged();
            // Close ProgressDialog
            progress.cancel();
        }
    }

    public void addAllNetworks(){
        // Start AsyncTask to add all networks.
        new AddAllTask().execute();
    }

    public void onClickRemoveAllNetworks(View view){
        RemoveAllDialogFragment df = new RemoveAllDialogFragment();
        df.setListener(this);
        df.show(this.getFragmentManager(),"");
    }

    private class RemoveAllTask extends AsyncTask<Void, Integer, Void> {
        ProgressDialog progress;

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            // Update progressbar
            progress.setProgress(values[0]);
        }

        @Override
        protected Void doInBackground(Void... params) {
            // Remove all networks from network configuration
            // WARNING: This could cause some performance issues depending of the number of networks and saved networks.
            int i = 0;
            WifiManager wmAsync = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            List<WifiConfiguration> wificonf = wmAsync.getConfiguredNetworks();
            for(Network n: networks){
                if(n.active){
                    n.active = false;
                    if(wificonf != null) {
                        for (WifiConfiguration wc : wificonf) {
                            if (wc.SSID.equals(n.ssid)) {
                                wmAsync.removeNetwork(wc.networkId);
                            }
                        }
                    }
                }
                i++;
                publishProgress(i);
            }
            // Save configuration
            wmAsync.saveConfiguration();
            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Create ProgressDialog and show it
            progress = new ProgressDialog(MainActivity.this);
            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progress.setIndeterminate(false);
            progress.setMax(networks.size());
            progress.setCancelable(false);
            progress.show();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            // Notify the NetworkAdapter that the content of ArrayList networks was changed.
            na.notifyDataSetChanged();
            // Close ProgressDialog
            progress.cancel();
        }
    }
    public void removeAllNetworks(){
        // WARNING: This could cause some performance issues depending of the number of networks and saved networks.
        /*for(Network n: networks){
            if(n.active){
                n.active = false;
                List<WifiConfiguration> wificonf = wm.getConfiguredNetworks();
                if(wificonf != null) {
                    for (WifiConfiguration wc : wificonf) {
                        if (wc.SSID.equals(n.ssid)) {
                            wm.removeNetwork(wc.networkId);
                        }
                    }
                }
            }
        }
        // Save configuration
        wm.saveConfiguration();
        // Notify the NetworkAdapter that the content of ArrayList networks was changed.
        na.notifyDataSetChanged();*/
        new RemoveAllTask().execute();
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
        if(wifiConf != null) {
            for (Network n : networks) {
                n.active = false;
                for (WifiConfiguration wc : wifiConf) {
                    if (wc.SSID.equals(n.ssid)) {
                        n.active = true;
                    }
                }
            }
            na.notifyDataSetChanged();
        }

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Get the Network object
        Network n = networks.get(position);
        // If the network is already saved in the network configuration, remove it
        if(n.active){
            n.active = false;
            List<WifiConfiguration> wificonf = wm.getConfiguredNetworks();
            if(wificonf != null) {
                for (WifiConfiguration wc : wificonf) {
                    if (wc.SSID.equals(n.ssid)) {
                        wm.removeNetwork(wc.networkId);
                    }
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
