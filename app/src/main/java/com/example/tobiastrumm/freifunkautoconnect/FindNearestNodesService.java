package com.example.tobiastrumm.freifunkautoconnect;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.mapzen.android.lost.api.LocationServices;
import com.mapzen.android.lost.api.LostApiClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

public class FindNearestNodesService extends IntentService {

    private final static String TAG = FindNearestNodesService.class.getSimpleName();
    private final static int DEFAULT_NUMBER_OF_NODES = 10;
    private final static boolean DEFAULT_SHOW_OFFLINE_NODES = false;
    private final static String NODES_JSON_URL = "http://freifunkapp.tobiastrumm.de/nodes2.json.gz";
    private final static String NODES_JSON_FILE_NAME = "nodes.json";
    private final static long UPDATE_INTERVAL = 60;
    private final static int HTTP_REQUEST_TIMEOUT = 5000;

    public static final String BROADCAST_ACTION = "com.example.tobiastrumm.freifunkautoconnect.findnearestnodesservice.BROADCAST";
    public static final String STATUS_TYPE = "status_type";
    public static final String STATUS_TYPE_FINISHED = "type_finished";
    public static final String STATUS_TYPE_ERROR = "type_error";
    public static final String RETURN_NODES = "return_nodes";
    public static final String RETURN_LAST_UPDATE = "return_last_update";

    private boolean showOfflineNodes;
    private int numberOfNodes;
    private SharedPreferences sharedPreferences;

    public FindNearestNodesService(){
        super("FindNearestNodesService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(FindNearestNodesService.this);
        showOfflineNodes = sharedPreferences.getBoolean("pref_nearest_ap_show_offline_nodes", DEFAULT_SHOW_OFFLINE_NODES);
        numberOfNodes = sharedPreferences.getInt("pref_nearest_ap_number_nodes", DEFAULT_NUMBER_OF_NODES);


        LostApiClient lostApiClient = new LostApiClient.Builder(this).build();
        lostApiClient.connect();

        onConnected();
    }

    private JSONObject getJsonFromLocalFile() {
        // Check if nodes.json exists in internal storage.
        File nodesJson = getFileStreamPath(NODES_JSON_FILE_NAME);
        if(!nodesJson.exists()){
            Log.d(TAG, "Copy " + NODES_JSON_FILE_NAME + " to internal storage.");
            // If not, copy nodes.json from assets to internal storage.
            try(FileOutputStream outputStream = openFileOutput(NODES_JSON_FILE_NAME, Context.MODE_PRIVATE);
                InputStream inputStream = getAssets().open(NODES_JSON_FILE_NAME)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while((bytesRead = inputStream.read(buffer)) != -1){
                    outputStream.write(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                Log.e(TAG, "", e);
                return null;
            }
            Log.d(TAG, "Finished copying " + NODES_JSON_FILE_NAME + " to internal storage");
        }

        // Read nodes.json from internal storage.
        String nodes_json_string;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(nodesJson)))){

            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            nodes_json_string = stringBuilder.toString();

        } catch (IOException e) {
            Log.e(TAG, "Failed to read nodes.json from internal storage.", e);
            return null;
        }

        // Parse json
        try{
            return new JSONObject(nodes_json_string);
        } catch (JSONException e) {
            Log.e(TAG, "Failed parsing nodes.json.", e);
            return null;
        }
    }

    private Node[] getNodesFromJson(JSONObject json) {
        try {
            JSONObject nodesJson = json.getJSONObject("nodes");
            ArrayList<Node> nodes = new ArrayList<>();
            for(Iterator<String> iter = nodesJson.keys();iter.hasNext();){
                JSONObject nodeJson = nodesJson.getJSONObject(iter.next());
                String name = nodeJson.getString("name");
                boolean online = nodeJson.getBoolean("online");
                double lat = nodeJson.getDouble("lat");
                double lon = nodeJson.getDouble("lon");
                Node node = new Node(name, lat, lon, online);
                nodes.add(node);
            }
            return nodes.toArray(new Node[nodes.size()]);
        } catch (JSONException e) {
            e.printStackTrace();
            return new Node[0];
        }
    }

    /**
     * Tries to download a new nodes.json file. If a newer file is available online, the local one
     * will be replaced and the JSONObject will be returned. If not, null will be returned. Null
     * will also be returned, if the last successful check for a new file was done in the last 60
     * seconds.
     *
     * @return JSONObject or null, if no newer version is available online.
     */
    private JSONObject downloadLatestNodesJson() {
        // Don't try downloading again until UPDATE_INTERVAL sec have passed since the last try.
        long currentTime = System.currentTimeMillis() / 1000L;
        long last_try_update_nodes = sharedPreferences.getLong("pref_nearest_ap_last_try_update_nodes", 0);
        if((currentTime - last_try_update_nodes) <= UPDATE_INTERVAL ){
            Log.d(TAG, "Not enough time has passed since the last try to download nodes.json.");
            return null;
        }

        HttpURLConnection urlConnection = null;
        String newer_nodes;
        try {
            // Download nodes.json.gz File.
            Log.d(TAG, "Start downloading " + NODES_JSON_FILE_NAME + " file");
            URL url = new URL(NODES_JSON_URL);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(HTTP_REQUEST_TIMEOUT);
            urlConnection.setReadTimeout(HTTP_REQUEST_TIMEOUT);
            Long nodes_json_last_modified = sharedPreferences.getLong("pref_nearest_ap_nodes_json_last_modified", 0);
            urlConnection.setIfModifiedSince(nodes_json_last_modified);
            urlConnection.setRequestProperty("Accept-Encoding", "gzip");

            int statusCode = urlConnection.getResponseCode();
            update_last_try_update_nodes();

            if (statusCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, url.toExternalForm() + " : Failed to download " + NODES_JSON_FILE_NAME + ". Response Code " + statusCode);
                return null;
            }
            String encoding = urlConnection.getContentEncoding();
            InputStream is = urlConnection.getInputStream();
            BufferedReader bufferedReader;

            // Download gzipped file
            if (encoding != null && encoding.toLowerCase().contains("gzip")) {
                bufferedReader = new BufferedReader(new InputStreamReader(new GZIPInputStream(is)));
                Log.d(TAG, NODES_JSON_URL + " is gzipped. Length: " + urlConnection.getContentLength());
            } else {
                bufferedReader = new BufferedReader(new InputStreamReader(is));
                Log.d(TAG, NODES_JSON_URL + " is not gzipped. Length: " + urlConnection.getContentLength());
            }

            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            newer_nodes = stringBuilder.toString();

            // Update last-modified value
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putLong("pref_nearest_ap_nodes_json_last_modified", urlConnection.getLastModified());
            editor.apply();

        } catch (SocketTimeoutException e) {
            Log.e(TAG, "Timeout:", e);
            return null;
        } catch (IOException e) {
            Log.e(TAG, "IOException:", e);
            return null;
        } finally {
            if(urlConnection != null){
                urlConnection.disconnect();
            }
        }

        // Try parsing downloaded json
        JSONObject newer_nodes_json;
        try{
            newer_nodes_json = new JSONObject(newer_nodes);
        } catch (JSONException e) {
            Log.e(TAG, "Failed parsing downloaded json.", e);
            return null;
        }


        // Write downloaded nodes.json to internal storage.
        try(FileOutputStream outputStream = openFileOutput(NODES_JSON_FILE_NAME, Context.MODE_PRIVATE)){
            outputStream.write(newer_nodes.getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Failed to write newer nodes.json to file.", e);
        }

        Log.d(TAG, NODES_JSON_FILE_NAME + " was downloaded");
        return newer_nodes_json;
    }

    private void update_last_try_update_nodes(){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("pref_nearest_ap_last_try_update_nodes", System.currentTimeMillis() / 1000L);
        editor.apply();
    }


    private void onConnected() {
        JSONObject json = downloadLatestNodesJson();
        // If no newer nodes.json file was found online, use the local one.
        if(json == null){
            json = getJsonFromLocalFile();
        }

        if(json == null){
            responseError();
            return;
        }

        long last_update;
        try {
            last_update = json.getLong("timestamp");
        } catch (JSONException e) {
            responseError();
            return;
        }
        Node[] nodes = getNodesFromJson(json);

        Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation();
        if(mLastLocation == null){
            responseError();
            return;
        }
        Node[] nearest_nodes = getNearestNodes(nodes, mLastLocation);

        Log.d(TAG, "Return nodes and timestamp");
        // Return nearest nodes to fragment.
        Intent localIntent = new Intent(BROADCAST_ACTION);
        localIntent.putExtra(STATUS_TYPE, STATUS_TYPE_FINISHED);
        localIntent.putExtra(RETURN_NODES, nearest_nodes);
        localIntent.putExtra(RETURN_LAST_UPDATE, last_update);
        LocalBroadcastManager.getInstance(FindNearestNodesService.this).sendBroadcast(localIntent);
    }

    private void responseError(){
        Intent localIntent = new Intent(BROADCAST_ACTION);
        localIntent.putExtra(STATUS_TYPE, STATUS_TYPE_ERROR);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private Node[] getNearestNodes(Node[] nodes, Location location) {
        long startTime = System.currentTimeMillis();
        // Calculate distance between nodes and current position in meters.
        for(Node n: nodes){
            Location locationNode = new Location("nodes.json");
            locationNode.setLatitude(n.lat);
            locationNode.setLongitude(n.lon);
            n.distance = location.distanceTo(locationNode);
        }
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        Log.d(TAG, "Duration calculating distances: " + elapsedTime + " ms");

        Arrays.sort(nodes, (n1, n2) -> Double.compare(n1.distance, n2.distance));

        // Get x nearest nodes
        Node[] nearest_nodes = new Node[numberOfNodes];
        int j = 0;
        int k = 0;
        while(j < numberOfNodes && k < nodes.length){
            if(showOfflineNodes || nodes[k].online){
                nearest_nodes[j] = nodes[k];
                j++;
            }
            k++;
        }
        return nearest_nodes;
    }
}
