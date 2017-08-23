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
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.WakefulBroadcastReceiver;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationService extends Service {

    private final static String TAG = NotificationService.class.getSimpleName();
    private final static int NOTIFICATION_ID = 1;

    private List<Network> networks = new ArrayList<>();
    private List<Network> foundNetworks = new ArrayList<>();


    private NotificationManager mNotificationManager;
    private WifiManager wm;
    private SharedPreferences prefMan;
    private ConnectivityManager connMan;


    private final BroadcastReceiver wifiReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "wifiReceiver: onReceive was called");
            new Thread(() -> checkIfNotificationShouldBeSend()).start();
        }
    };

    private final BroadcastReceiver updateSSIDsReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getStringExtra(DownloadSsidJsonService.STATUS_TYPE)) {
                case DownloadSsidJsonService.STATUS_TYPE_REPLACED:
                    Log.d(TAG, "UpdateSSIDsReceiver : onReceive was called with STATUS_TYPE_REPLACED");
                    new Thread(() -> {
                        try {
                            getSSIDs();
                        } catch (IOException | JSONException e) {
                            e.printStackTrace();
                            Log.e(TAG, "Updating SSIDs failed.", e);
                        }
                    }).start();
                    break;
            }
        }
    };

    /**
     * This function checks if a notification should be send to the user or if a sent notification
     * should be canceled and then sends or cancels the notification.
     * This function is synchronized to avoid calling it while another thread is running getSSIDs().
     */
    synchronized private void checkIfNotificationShouldBeSend(){
        // Check if Freifunk networks are in reach.
        List<ScanResult> results = wm.getScanResults();
        List<Network> oldFoundNetworks = new ArrayList<>(foundNetworks);
        foundNetworks = new ArrayList<>();

        if(results != null) {
            for (ScanResult r : results) {
                    /* Create a new Network object for each network in range. Then check if the list
                     * of Freifunk networks contains this network. This works because the equals()
                     * method of Network was overwritten to return true if the SSIDs are equal.
                     */
                Network compareWith = new Network('"' + r.SSID + '"');
                if (networks.contains(compareWith)) {
                    // foundNetworks holds all Freifunk Networks that are currently in reach.
                    if (!foundNetworks.contains(compareWith)) {
                        foundNetworks.add(compareWith);
                    }
                }
            }
        }

        if(foundNetworks.isEmpty()){
            // Cancel the notification if no Freifunk network is in reach anymore.
            mNotificationManager.cancel(NOTIFICATION_ID);
        }
        else {
            // Look if device is already connected to a network
            boolean alreadyConnectedToAnyNetwork = checkIfAlreadyConnectedToAnyNetwork();
            boolean noNotifiactionWhenConnected = prefMan.getBoolean("pref_no_notification_connected", true);

            if (noNotifiactionWhenConnected && alreadyConnectedToAnyNetwork){
                // Do not send a notification if the device is already connected to a network and the user
                // does not wish for a notification in this case.
                return;
            }

            boolean alreadyConnectedToFreifunk = checkIfAlreadyConnectedToFreifunk();
            if(alreadyConnectedToFreifunk){
                // Do not send a notification if the device is already connected to Freifunk
                return;
            }

            // Look if this scan found different Freifunk networks than the previous scan.
            boolean sameResults = oldFoundNetworks.containsAll(foundNetworks);

            // TODO Remove debug messages
            // Print debug messages with the old and new found networks
            StringBuilder foundNetworksStr = new StringBuilder();
            for(Network n: foundNetworks){
                foundNetworksStr.append(n.ssid).append(", ");
            }
            Log.d(TAG, "Found networks: " + foundNetworksStr.toString());

            StringBuilder oldfoundNetworksStr = new StringBuilder();
            for(Network n: oldFoundNetworks){
                oldfoundNetworksStr.append(n.ssid).append(", ");
            }
            Log.d(TAG, "Old Found networks: " + oldfoundNetworksStr.toString());

            if(sameResults){
                // Do not send a notification again if no new Freifunk network was found.
                return;
            }

            // Send the notification with the found Freifunk networks.
            sendNotification(foundNetworks);
            Log.d(TAG, "sendNotification was called");
        }
    }

    /**
     * Check if the device is already connected to any wifi network
     * @return Returns true if the device is connected to any wifi network
     */
    private boolean checkIfAlreadyConnectedToAnyNetwork(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            @SuppressWarnings("deprecation")
            NetworkInfo mwifi = connMan.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return mwifi.isConnectedOrConnecting();
        } else {
            // Go through all tracked networks and check if one is connected or connecting.
            android.net.Network[] connectedNetworks = connMan.getAllNetworks();
            for (android.net.Network n : connectedNetworks) {
                if (connMan.getNetworkInfo(n).isConnectedOrConnecting()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if the device is already connected to a Freifunk network
     * @return Returns true if te device is already connected to a Freifunk network.
     */
    private boolean checkIfAlreadyConnectedToFreifunk(){
        String currentSSID = wm.getConnectionInfo().getSSID();
        if(currentSSID != null){
            Log.d(TAG, "Check if " + currentSSID + " is a Freifunk network");
            Network compareWith = new Network(currentSSID);
            if(foundNetworks.contains(compareWith)){
                Log.d(TAG, "Already connected with " + compareWith.ssid);
                return true;
            }
        }
        return false;
    }

    private void sendNotification(List<Network> networks){
        String text = getString(R.string.notification_text);
        StringBuilder textBuilder = new StringBuilder();
        for(Network n: networks){
            textBuilder.append(n.ssid).append(", ");
        }
        text += textBuilder.toString();
        text = text.substring(0, text.length()-2);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(NotificationService.this)
                        .setSmallIcon(R.mipmap.ic_wifi_white_48dp)
                        .setContentTitle(getString(R.string.notification_title))
                        .setContentText(text)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(PendingIntent.getActivity(NotificationService.this,0, new Intent(Settings.ACTION_WIFI_SETTINGS),0));

        boolean vibrate = prefMan.getBoolean("pref_notification_vibrate", true);
        boolean playSound = prefMan.getBoolean("pref_notification_sound", false);
        if(vibrate){
            if(playSound){
                mBuilder.setDefaults(Notification.DEFAULT_ALL);
            }
            else{
                mBuilder.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);
            }
        }
        else{
            if(playSound){
                mBuilder.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND);
            }
            else{
                mBuilder.setDefaults(Notification.DEFAULT_LIGHTS);
            }
        }
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        Log.d(TAG, "Notification was sent.");
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        // Release the wake lock
        if(intent != null) {
            WakefulBroadcastReceiver.completeWakefulIntent(intent);
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "onCreate");

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        prefMan = PreferenceManager.getDefaultSharedPreferences(this);

        new Thread(() -> {
            try {
                getSSIDs();
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                Log.e(TAG, "Updating SSIDs failed.", e);
            }
        }).start();

        // Register wifiReceiver
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        // Register updateSSIDsReceiver
        registerReceiver(updateSSIDsReceiver, new IntentFilter(DownloadSsidJsonService.BROADCAST_ACTION));
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        unregisterReceiver(wifiReceiver);
        unregisterReceiver(updateSSIDsReceiver);
        mNotificationManager.cancel(NOTIFICATION_ID);
        super.onDestroy();
    }

    /**
     * Update the list of all Freifunk SSIDs by reading the ssids.json file
     * This function is synchronized to avoid calling it while another thread is running
     * checkIfNotificationShouldBeSend()
     */
    synchronized private void getSSIDs() throws IOException, JSONException {
        // Read SSIDs from JSON file
        JSONObject json = SsidJsonReader.readSsisJsonFromFile(this);
        networks.clear();
        JSONArray ssidsJsonArray = json.getJSONArray("ssids");
        for(int i = 0; i<ssidsJsonArray.length(); i++){
            networks.add(new Network('"' + ssidsJsonArray.getString(i) + '"'));
        }
        Collections.sort(networks);
    }
}
