package com.example.tobiastrumm.freifunkautoconnect;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

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

/**
 * Created by Tobias on 29.07.2015.
 */
public class NotificationService extends Service {

    private final static String TAG = NotificationService.class.getSimpleName();
    private final static int NOTIFICATION_ID = 1;

    private List<Network> networks;
    private List<Network> foundNetworks = new ArrayList<>();

    private WifiReceiver wifiReceiver;

    private  NotificationManager mNotificationManager;

    private class WifiReceiver extends BroadcastReceiver{
        private WifiReceiver(){}

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive was called");
            boolean alreadyConnectedToFreifunk = false;
            boolean alreadyConnectedToAnyNetwork = false;

            WifiManager wm = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            List<ScanResult> results = wm.getScanResults();
            List<Network> oldFoundNetworks = new ArrayList<>(foundNetworks);
            foundNetworks = new ArrayList<>();

            if(results != null){
                // Look if known Freifunk networks are in reach.
                for(ScanResult r: results){
                    Network compareWith = new Network('"' + r.SSID + '"');
                    if(networks.contains(compareWith)){
                        if(!foundNetworks.contains(compareWith)){
                            foundNetworks.add(compareWith);
                        }

                    }
                }

                // Look if device is already connected to a network
                ConnectivityManager connMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo mwifi = connMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                alreadyConnectedToAnyNetwork = mwifi.isConnectedOrConnecting();

                // Look if device is already connected to a known Freifunk network
                String currentSSID = wm.getConnectionInfo().getSSID();
                if(currentSSID != null){
                    Log.d(TAG, "Check if " + currentSSID + " is a Freifunk network");
                    Network compareWith = new Network(currentSSID);
                    if(foundNetworks.contains(compareWith)){
                        alreadyConnectedToFreifunk = true;
                        Log.d(TAG, "Already connected with " + compareWith.ssid);
                    }
                }

                // Look if this scan found different Freifunk networks than the previous scan.
                boolean sameResults = oldFoundNetworks.containsAll(foundNetworks);

                String foundNetworksStr = "";
                for(Network n: foundNetworks){
                    foundNetworksStr += n.ssid + ", ";
                }
                Log.d(TAG, "Found networks: " + foundNetworksStr);

                String oldfoundNetworksStr = "";
                for(Network n: oldFoundNetworks){
                    oldfoundNetworksStr += n.ssid + ", ";
                }
                Log.d(TAG, "Old Found networks: " + oldfoundNetworksStr);

                Log.d(TAG, "!isEmpty: " + !foundNetworks.isEmpty() + " !sameResults: " + !sameResults + " !alreadyConnectedToFreifunk: " + !alreadyConnectedToFreifunk +  " !(alreadyConnectedToAnyNetwork && NoNotificationIfConnected): " + !(alreadyConnectedToAnyNetwork && PreferenceManager.getDefaultSharedPreferences(NotificationService.this).getBoolean("pref_no_notification_connected", false)));
                // Cancel the notification if no Freifunk network is in reach anymore.
                if(foundNetworks.isEmpty()){
                    mNotificationManager.cancel(NOTIFICATION_ID);
                }
                if(!foundNetworks.isEmpty() && !sameResults && !alreadyConnectedToFreifunk && !(alreadyConnectedToAnyNetwork && PreferenceManager.getDefaultSharedPreferences(NotificationService.this).getBoolean("pref_no_notification_connected", false))){
                    sendNotification(foundNetworks);
                    Log.d(TAG, "sendNotification was called");
                }
            }
        }

        private void sendNotification(List<Network> networks){
            String text = getString(R.string.notification_text);
            for(Network n: networks){
                text += n.ssid + ", ";
            }
            text = text.substring(0, text.length()-2);

            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(NotificationService.this)
                            .setSmallIcon(R.mipmap.ic_wifi_white_48dp)
                            .setContentTitle(getString(R.string.notification_title))
                            .setContentText(text)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .setContentIntent(PendingIntent.getActivity(NotificationService.this,0, new Intent(Settings.ACTION_WIFI_SETTINGS),0));

            SharedPreferences prefMan = PreferenceManager.getDefaultSharedPreferences(NotificationService.this);
            boolean vibrate = prefMan.getBoolean("pref_notification_vibrate", true);
            boolean playSound = prefMan.getBoolean("pref_notification_sound", false);
            if(vibrate && playSound){
                mBuilder.setDefaults(Notification.DEFAULT_ALL);
            }
            else if(!vibrate && playSound){
                mBuilder.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND);
            }
            else if(vibrate && !playSound){
                mBuilder.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);
            }
            else{
                mBuilder.setDefaults(Notification.DEFAULT_LIGHTS);
            }

            mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
            Log.d(TAG, "Notification was sent.");
        }


    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "NotificationService was started");
        networks = new ArrayList<>();
        try{
            getSSIDs();
        }
        catch(IOException e){
            Log.w(TAG, "Could not read SSIDs from csv file.");
            stopSelf();
        }

        // Register broadcast receiver
        wifiReceiver = new WifiReceiver();
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "On destroyed was called");
        unregisterReceiver(wifiReceiver);
        mNotificationManager.cancel(NOTIFICATION_ID);
        super.onDestroy();
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

        Collections.sort(networks);
    }
}
