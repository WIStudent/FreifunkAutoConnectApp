package com.example.tobiastrumm.freifunkautoconnect;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

public class FindNearestNodesService extends IntentService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private final static String TAG = FindNearestNodesService.class.getSimpleName();
    private final static int DEFAULT_NUMBER_OF_NODES = 10;
    private final static boolean DEFAULT_SHOW_OFFLINE_NODES = false;

    public static final String BROADCAST_ACTION = "com.example.tobiastrumm.freifunkautoconnect.findnearestnodesservice.BROADCAST";
    public static final String STATUS_TYPE = "status_type";
    public static final String STATUS_TYPE_FINISHED = "type_finished";
    public static final String STATUS_TYPE_ERROR = "type_error";
    public static final String RETURN_NODES = "return_nodes";
    public static final String RETURN_LAST_UPDATE = "return_last_update";


    private GoogleApiClient mGoogleApiClient;

    private boolean showOfflineNodes;
    private int numberOfNodes;

    private boolean googleApiClientRunning;

    public FindNearestNodesService(){
        super("FindNearestNodesService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        googleApiClientRunning = true;
        Log.d(TAG, "Build GoogleApiClient");
        buildGoogleApiClient();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(FindNearestNodesService.this);
        showOfflineNodes = sharedPreferences.getBoolean("pref_nearest_ap_show_offline_nodes", DEFAULT_SHOW_OFFLINE_NODES);
        numberOfNodes = sharedPreferences.getInt("pref_nearest_ap_number_nodes", DEFAULT_NUMBER_OF_NODES);
        mGoogleApiClient.blockingConnect();

        // Necessary so that the service keeps running until the other thread finishes the distance calculations.
        while(googleApiClientRunning){
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "onHandleIntent is returning");
    }

    private JSONObject getJsonFromLocalFile() {

        try {
            InputStream is = getAssets().open("nodes.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");
            return  new JSONObject(json);
        } catch (IOException e){
            e.printStackTrace();
            return null;
        } catch (JSONException e) {
            e.printStackTrace();
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

    private void updateNodesJson() {
    }

    private void calculateManhattanDistance(Node[] nodes, Location location) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        for (Node node : nodes) {
            node.distance = Math.abs(node.lat - lat) + Math.abs(node.lon - lon);
        }
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public void onConnected(Bundle bundle) {
        // Necessary or the callback will be executed on the main/ui thread.
        Thread thread = new Thread(){
            @Override
            public void run() {
                Log.d(TAG, "GoogleApiClient: running onConnected");
                JSONObject json = getJsonFromLocalFile();
                if(json == null){
                    responseError();
                    return;
                }
                updateNodesJson();
                Node[] nodes = getNodesFromJson(json);
                Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                calculateManhattanDistance(nodes, mLastLocation);
                Node[] nearest_nodes = getNearestNodes(nodes, mLastLocation);
                Log.d(TAG, "Current location: lat: " + mLastLocation.getLatitude() + " lon: " + mLastLocation.getLongitude());

        for(int i = 0; i<nearest_nodes.length; i++){
            Log.d(TAG, "name: " + nearest_nodes[i].name + " online: " + nearest_nodes[i].online + " lat: " + nearest_nodes[i].lat + " lon: " + nearest_nodes[i].lon + " dist: " + nearest_nodes[i].distance);
        }


                long last_update;
                try {
                    last_update = json.getLong("timestamp");
                } catch (JSONException e) {
                    responseError();
                    return;
                }

                Log.d(TAG, "Return nodes and timestamp");
                // Return nearest nodes to fragment.
                Intent localIntent = new Intent(BROADCAST_ACTION);
                localIntent.putExtra(STATUS_TYPE, STATUS_TYPE_FINISHED);
                localIntent.putExtra(RETURN_NODES, nearest_nodes);
                localIntent.putExtra(RETURN_LAST_UPDATE, last_update);
                LocalBroadcastManager.getInstance(FindNearestNodesService.this).sendBroadcast(localIntent);
                googleApiClientRunning = false;
            }
        };
        thread.start();
        Log.d(TAG, "onConnected is returning");
    }

    private void responseError(){
        Intent localIntent = new Intent(BROADCAST_ACTION);
        localIntent.putExtra(STATUS_TYPE, STATUS_TYPE_ERROR);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private Node[] getNearestNodes(Node[] nodes, Location location) {
        Arrays.sort(nodes, new Comparator<Node>(){

            @Override
            public int compare(Node n1, Node n2) {
                return Double.compare(n1.distance, n2.distance);
            }
        });

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
        for(Node n: nearest_nodes){
            Log.d(TAG, "name: " + n.name + " online: " + n.online + " lat: " + n.lat + " lon: " + n.lon + " dist: " + n.distance);
        }

        // Calculate distance between nodes and current position in meters.
        for(int i = 0; i<nearest_nodes.length; i++){
            Location locationNode = new Location("Node" + i);
            locationNode.setLatitude(nearest_nodes[i].lat);
            locationNode.setLongitude(nearest_nodes[i].lon);
            nearest_nodes[i].distance = location.distanceTo(locationNode);
        }
        return nearest_nodes;
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        googleApiClientRunning = false;
    }
}
