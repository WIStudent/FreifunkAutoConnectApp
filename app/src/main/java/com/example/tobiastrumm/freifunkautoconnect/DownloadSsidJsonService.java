package com.example.tobiastrumm.freifunkautoconnect;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadSsidJsonService extends IntentService {

    private final static String TAG = DownloadSsidJsonService.class.getSimpleName();
    private final static String SSID_URL = "https://raw.githubusercontent.com/WIStudent/freifunk-ssids/freifunk_auto_connect_production/ssids.json";
    public static final String BROADCAST_ACTION = "com.example.tobiastrumm.freifunkautoconnect.downloadssidjsonservice.BROADCAST";
    public static final String STATUS_TYPE = "status_type";
    public static final String STATUS_TYPE_REPLACED = "type_replaced";
    public static final String STATUS_TYPE_NO_NEW_FILE = "type_no_new_file";


    public DownloadSsidJsonService(){
        super("DownloadSsidJsonService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        String downloaded_ssids = downloadLatestSsidList();
        if(downloaded_ssids == null){
            responseNoNewFile();
            return;
        }

        String existing_ssids = readSSsidListFromFile();
        if(existing_ssids == null){
            responseNoNewFile();
            return;
        }

        // Compare version of downloaded and existing ssid files.
        int version_existing_ssids, version_downloaded_ssids;
        try {
            JSONObject downloaded_ssids_json = new JSONObject(downloaded_ssids);
            JSONObject existing_ssids_json = new JSONObject(existing_ssids);

            version_existing_ssids = existing_ssids_json.getInt("version");
            version_downloaded_ssids = downloaded_ssids_json.getInt("version");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse JSON.", e);
            responseNoNewFile();
            return;
        }

        // If version of downloaded json file is bigger, replace existing json file.
        Log.d(TAG, "Version of existing ssids.json: " + version_existing_ssids +  " Version of downloaded ssids.json: " + version_downloaded_ssids);
        if(version_downloaded_ssids <= version_existing_ssids){
            responseNoNewFile();
            return;
        }

        try(FileOutputStream outputStream = openFileOutput("ssids.json", Context.MODE_PRIVATE)){
            outputStream.write(downloaded_ssids.getBytes());
            responseFileReplaced();
        } catch (IOException e) {
            Log.e(TAG, "Failed to override existing ssids.json file.", e);
            responseNoNewFile();
        }
    }

    /**
     *
     * @return Returns the downloaded SSID list or null, if an error occurred.
     */
    private String downloadLatestSsidList(){

        HttpURLConnection urlConnection = null;
        try {
            // Download json File.
            Log.d(TAG, "Start downloading ssid file");
            URL url = new URL(SSID_URL);
            urlConnection = (HttpURLConnection) url.openConnection();

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(DownloadSsidJsonService.this);
            String savedEtag = sharedPreferences.getString("pref_etag_ssids_json", null);
            if (savedEtag != null) {
                Log.d(TAG, "Setting Etag in http request " + savedEtag);
                urlConnection.setRequestProperty("If-None-Match", savedEtag);
            }

            int statusCode = urlConnection.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, url.toExternalForm() + " : Failed to download file. Response Code " + statusCode);
                return null;
            }
            // Update saved Etag
            String receivedEtag = urlConnection.getHeaderField("Etag");
            SharedPreferences.Editor sharedPrefEditor = sharedPreferences.edit();
            sharedPrefEditor.putString("pref_etag_ssids_json", receivedEtag);
            sharedPrefEditor.apply();

            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader readerDownloadedJson = new BufferedReader(new InputStreamReader(in));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = readerDownloadedJson.readLine()) != null) {
                builder.append(line);
            }
            Log.d(TAG, "ssids.json was downloaded");
            return builder.toString();

        } catch (IOException e) {
            Log.e(TAG, "Something went wrong while downloading lates ssids.json file", e);
            return null;
        } finally{
            if(urlConnection != null){
                urlConnection.disconnect();
            }
        }
    }

    private String readSSsidListFromFile(){
        // Read ssids.json from internal storage.
        StringBuilder builder = new StringBuilder();
        try (BufferedReader existing_file_reader = new BufferedReader(new InputStreamReader(openFileInput("ssids.json")))){

            String line;
            while ((line = existing_file_reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch ( IOException e) {
            Log.e(TAG, "Something went wrong when reading from exisiting ssids.json file.", e);
            return null;
        }
    }

    private void responseFileReplaced(){
        Intent localIntent = new Intent(BROADCAST_ACTION);
        localIntent.putExtra(STATUS_TYPE, STATUS_TYPE_REPLACED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }

    private void responseNoNewFile(){
        Intent localIntent = new Intent(BROADCAST_ACTION);
        localIntent.putExtra(STATUS_TYPE, STATUS_TYPE_NO_NEW_FILE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }
}
