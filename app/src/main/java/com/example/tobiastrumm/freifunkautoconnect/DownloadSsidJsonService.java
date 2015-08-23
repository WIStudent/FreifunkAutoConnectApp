package com.example.tobiastrumm.freifunkautoconnect;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by tobia_000 on 23.08.2015.
 */
public class DownloadSsidJsonService extends IntentService {

    private final static String TAG = DownloadSsidJsonService.class.getSimpleName();
    private final static String SSID_URL = "https://raw.githubusercontent.com/WIStudent/FreifunkAutoConnectApp/download_ssids/app/src/main/assets/ssids.json";
    public static final String BROADCAST_ACTION = "com.example.tobiastrumm.freifunkautoconnect.downloadssidjsonservice.BROADCAST";
    public static final String STATUS_TYPE = "status_type";
    public static final String STATUS_TYPE_REPLACED = "type_replaced";


    public DownloadSsidJsonService(){
        super("DownloadSsidJsonService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        long lastCheck = sharedPref.getLong("timestamp_last_ssid_download", 0);
        long currentTime = System.currentTimeMillis() / 1000L;

        Log.d(TAG, "Current timestamp: " + currentTime +  " last check timestamp: " + lastCheck);
        if(currentTime - lastCheck > 24*60*60){


            StringBuilder builder = new StringBuilder();
            JSONObject downloaded_ssids = null;
            JSONObject existing_ssids = null;
            try {
                // Download json File.
                Log.d(TAG, "Start downloading ssid file");
                URL url = new URL(SSID_URL);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                int statusCode = urlConnection.getResponseCode();
                if(statusCode == HttpURLConnection.HTTP_OK) {
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    downloaded_ssids = new JSONObject(builder.toString());
                } else{
                    Log.w(TAG, url.toExternalForm() + " : Failed to download file. Response Code " + statusCode);
                    return;
                }
                urlConnection.disconnect();
                Log.d(TAG, "ssids.json was downloaded");

                // Read ssids.json from internal storage.
                String jsonString = "";
                InputStreamReader is = new InputStreamReader(openFileInput("ssids.json"));
                BufferedReader reader = new BufferedReader(is);
                String line;
                while ((line = reader.readLine()) != null) {
                    //networks.add(new Network(line));
                    jsonString += line;
                }
                reader.close();
                existing_ssids = new JSONObject(jsonString);

                // Compare version of downloaded and existing ssid files.
                int version_existing_ssids = existing_ssids.getInt("version");
                int version_downloaded_ssids = downloaded_ssids.getInt("version");

                // If version of downloaded json file is bigger, replace existing json file.
                Log.d(TAG, "Version of existing ssids.json: " + version_existing_ssids +  " Version of downloaded ssids.json: " + version_downloaded_ssids);
                if(version_downloaded_ssids > version_existing_ssids){
                    FileOutputStream outputStream = openFileOutput("ssids.json", Context.MODE_PRIVATE);
                    outputStream.write(builder.toString().getBytes());
                    outputStream.close();
                    responseFileReplaced();
                }

                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putLong("timestamp_last_ssid_download", currentTime);
                editor.commit();

            } catch (MalformedURLException e) {
                e.printStackTrace();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
        }

    }

    private void responseFileReplaced(){
        Intent localIntent = new Intent(BROADCAST_ACTION);
        localIntent.putExtra(STATUS_TYPE, STATUS_TYPE_REPLACED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(localIntent);
    }
}
