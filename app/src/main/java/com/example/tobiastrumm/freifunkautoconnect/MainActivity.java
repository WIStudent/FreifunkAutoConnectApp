package com.example.tobiastrumm.freifunkautoconnect;

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MainActivity extends ActionBarActivity implements AdapterView.OnItemClickListener, RemoveAllDialogFragment.OnRemoveAllListener, AddAllDialogFragment.OnAddAllListener{

    public static String DIRECTORY = "freifunkautoconnect";
    public static String USER_SSIDS_FILE = "user_ssids.csv";

    private static final String STATE_PROGRESSBAR_RUNNING = "state_progressbar_running";
    private static final String STATE_PROGRESSBAR_MAX = "state_progressbar_max";
    private static final String STATE_PROGRESSBAR_PROGRESS = "state_progressbar_progress";

    private static String TAG = MainActivity.class.getSimpleName();

    private ArrayList<Network> networks;

    private ListView lv;
    private NetworkAdapter na;
    private WifiManager wm;

    private ProgressDialog progress;
    private int progressBarMax;
    private int progressBarProgress;
    private boolean progressBarRunning = false;


    private class AddAllNetworksResponseReceiver extends BroadcastReceiver{
        private AddAllNetworksResponseReceiver(){}

        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getStringExtra(AddAllNetworksService.STATUS_TYPE)){
                case AddAllNetworksService.STATUS_TYPE_FINISHED:
                    checkActiveNetworks();
                    if(progress != null){
                        progress.cancel();
                    }
                    progressBarRunning = false;
                    break;

                case AddAllNetworksService.STATUS_TYPE_PROGRESS:
                    progressBarProgress = intent.getIntExtra(AddAllNetworksService.STATUS_PROGRESS, 0);
                    if(progress != null) {
                        progress.setProgress(progressBarProgress);
                    }
                    break;
            }
        }
    }

    private class RemoveAllNetworksResponseReceiver extends BroadcastReceiver{
        private RemoveAllNetworksResponseReceiver(){}

        @Override
        public void onReceive(Context context, Intent intent){
            switch(intent.getStringExtra(RemoveAllNetworksService.STATUS_TYPE)){
                case RemoveAllNetworksService.STATUS_TYPE_FINISHED:
                    checkActiveNetworks();
                    if(progress != null){
                        progress.cancel();
                    }
                    progressBarRunning = false;
                    break;

                case RemoveAllNetworksService.STATUS_TYPE_PROGRESS:
                    progressBarProgress = intent.getIntExtra(RemoveAllNetworksService.STATUS_PROGRESS, 0);
                    if(progress != null){
                        progress.setProgress(progressBarProgress);
                    }
                    break;
            }
        }
    }

    private class DownloadSsidJsonResponseReceiver extends BroadcastReceiver{
        private DownloadSsidJsonResponseReceiver(){}

        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getStringExtra(DownloadSsidJsonService.STATUS_TYPE)){
                case DownloadSsidJsonService.STATUS_TYPE_REPLACED:
                    try {
                        // Read ssid from file again.
                        getSSIDs();
                        checkActiveNetworks();
                        Log.d(TAG, "SSIDs were refreshed");

                        // Notify user that a new SSID list was downloaded.
                        Toast toast = Toast.makeText(MainActivity.this, getString(R.string.message_ssids_updated), Toast.LENGTH_LONG);
                        toast.show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    }

    private void getSSIDs() throws IOException {
        // Check if ssids.json exists in internal storage.
        File ssidsJson = getFileStreamPath("ssids.json");
        if(!ssidsJson.exists()){
            Log.d(TAG, "Copy ssids.json to internal storage.");
            // If not, copy ssids.json from assets to internal storage.
            FileOutputStream outputStream = openFileOutput("ssids.json", Context.MODE_PRIVATE);
            InputStream inputStream = getAssets().open("ssids.json");
            byte[] buffer = new byte[1024];
            int bytesRead;
            while((bytesRead = inputStream.read(buffer)) != -1){
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outputStream.close();
            Log.d(TAG, "Finished copying ssids.json to internal storage");
        }

        // Read ssids.json from internal storage.
        String jsonString = "";
        InputStreamReader is = new InputStreamReader(new FileInputStream(ssidsJson));
        BufferedReader reader = new BufferedReader(is);
        String line;
        while ((line = reader.readLine()) != null) {
            jsonString += line;
        }
        reader.close();

        // Read SSIDs from JSON file
        networks.clear();
        try {
            JSONObject json = new JSONObject(jsonString);
            JSONArray ssidsJsonArray = json.getJSONArray("ssids");
            for(int i = 0; i<ssidsJsonArray.length(); i++){
                networks.add(new Network('"' + ssidsJsonArray.getString(i) + '"'));
            }

        } catch (JSONException e) {
            e.printStackTrace();
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
        Collections.sort(networks);
    }

    private void checkForNewSsidFile(){
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        long lastCheck = sharedPref.getLong(getString(R.string.preference_timestamp_last_ssid_download), 0);
        long currentTime = System.currentTimeMillis() / 1000L;

        Log.d(TAG, "Current timestamp: " + currentTime +  " last check timestamp: " + lastCheck);

        if(currentTime - lastCheck > 24*60*60){
            // Start DownloadSsidJsonService to check if a newer ssids.json file is available.
            Intent intent = new Intent(this, DownloadSsidJsonService.class);
            startService(intent);
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
                    new String[]{directory.getAbsolutePath(), user_ssids.getAbsolutePath(),},
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

    private void setupBroadcastReceivers(){
        IntentFilter addAllIntentFilter = new IntentFilter(AddAllNetworksService.BROADCAST_ACTION);
        AddAllNetworksResponseReceiver addAllNetworksResponseReceiver = new AddAllNetworksResponseReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(addAllNetworksResponseReceiver, addAllIntentFilter);

        IntentFilter removeAllIntentFilter = new IntentFilter(RemoveAllNetworksService.BROADCAST_ACTION);
        RemoveAllNetworksResponseReceiver removeAllNetworksResponseReceiver = new RemoveAllNetworksResponseReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(removeAllNetworksResponseReceiver, removeAllIntentFilter);

        IntentFilter downloadSsidJsonIntentFilter = new IntentFilter(DownloadSsidJsonService.BROADCAST_ACTION);
        DownloadSsidJsonResponseReceiver downloadSsidJsonResponseReceiver = new DownloadSsidJsonResponseReceiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(downloadSsidJsonResponseReceiver, downloadSsidJsonIntentFilter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Recreate ProgressBar if screen was rotated.
        if(savedInstanceState != null){
            progressBarRunning = savedInstanceState.getBoolean(STATE_PROGRESSBAR_RUNNING);
            // Don't recreate the progress dialog if the service has stopped running while the activity was recreated.
            if(progressBarRunning && (isAddAllNetworkServiceRunning() || isRemoveAllNetworkServiceRunning())){
                progressBarMax = savedInstanceState.getInt(STATE_PROGRESSBAR_MAX);
                progressBarProgress = savedInstanceState.getInt(STATE_PROGRESSBAR_PROGRESS);
                startProgressDialog(progressBarMax);
            }
        }

        setupBroadcastReceivers();

        // Use Toolbar instead of ActionBar. See:
        // http://blog.xamarin.com/android-tips-hello-toolbar-goodbye-action-bar/
        // https://stackoverflow.com/questions/29055491/android-toolbar-for-api-19-for-api-21-works-ok
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        networks = new ArrayList<>();
        try{
            getSSIDs();
        }
        catch(IOException e){
            // Could not read SSIDs from csv file.
        }

        wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        setupUI();

        // Start NotificationService if it should running but isn't
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean notifications = sharedPref.getBoolean("pref_notification", false);
        if(notifications && !isNotificationServiceRunning()){
            startService(new Intent(this, NotificationService.class));
        }

        checkForNewSsidFile();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check which Networks are already saved in the network configuration
        checkActiveNetworks();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(STATE_PROGRESSBAR_RUNNING, progressBarRunning);
        savedInstanceState.putInt(STATE_PROGRESSBAR_MAX, progressBarMax);
        savedInstanceState.putInt(STATE_PROGRESSBAR_PROGRESS, progressBarProgress);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        if(progress != null){
            progress.dismiss();
        }
        super.onDestroy();
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
            Intent intent = new Intent(MainActivity.this, InfoActivity.class);
            startActivity(intent);
            return true;
        }

        else if (id == R.id.action_settings){
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }


        return super.onOptionsItemSelected(item);
    }

    public void onClickAddAllNetworks(View view){
        AddAllDialogFragment df = new AddAllDialogFragment();
        df.show(this.getFragmentManager(),"");
    }

    private void startProgressDialog(int maxValue){
        progress = new ProgressDialog(MainActivity.this);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(false);
        progress.setMax(maxValue);
        progress.setCancelable(false);
        progress.show();
        progressBarRunning = true;
    }

    public void addAllNetworks(){
        // Create ProgressDialog and show it
        progressBarMax = networks.size();
        startProgressDialog(progressBarMax);

        //Start AddAllNetworksService
        Intent intent = new Intent(this, AddAllNetworksService.class);
        intent.putParcelableArrayListExtra(AddAllNetworksService.INPUT_NETWORKS, networks);
        startService(intent);
    }

    public void onClickRemoveAllNetworks(View view){
        RemoveAllDialogFragment df = new RemoveAllDialogFragment();
        df.show(this.getFragmentManager(), "");
    }

    public void removeAllNetworks(){
        // Create ProgressDialog and show it
        progressBarMax = networks.size();
        startProgressDialog(progressBarMax);

        //Start RemoveAllNetworksService
        Intent intent = new Intent(this, RemoveAllNetworksService.class);
        intent.putParcelableArrayListExtra(RemoveAllNetworksService.INPUT_NETWORKS, networks);
        startService(intent);
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
            long timeStart = System.currentTimeMillis();
            Collections.sort(wifiConf, new WifiConfigurationComparator());
            for (Network n : networks) {
                // Search for the current network in the network configuration. If it is found (index will be >= 0), set active to true
                int index = Collections.binarySearch(wifiConf, n.ssid, new WifiConfigurationSSIDComparator());
                n.active = index >= 0;
            }

            long timeEnd = System.currentTimeMillis();
            Log.d(TAG, "Duration checkActiveNetworks: " + (timeEnd - timeStart) + "ms");
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

    private boolean isAddAllNetworkServiceRunning(){
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (AddAllNetworksService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isRemoveAllNetworkServiceRunning(){
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for( ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if(RemoveAllNetworksService.class.getName().equals((service.service.getClassName()))) {
                return true;
            }
        }
        return false;
    }

    private boolean isNotificationServiceRunning(){
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for ( ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if(NotificationService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}