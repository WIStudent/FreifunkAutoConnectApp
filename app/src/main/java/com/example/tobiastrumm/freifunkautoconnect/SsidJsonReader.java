package com.example.tobiastrumm.freifunkautoconnect;


import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

class SsidJsonReader {

    private final static String TAG = SsidJsonReader.class.getSimpleName();
    private final static String FILE_NAME = "ssids.json";

    static JSONObject readSsisJsonFromFile(Context context) throws IOException, JSONException {
        File ssidsJson = context.getFileStreamPath(FILE_NAME);
        if(!ssidsJson.exists()){
            Log.d(TAG, "Copy ssids.json to internal storage.");
            // If not, copy ssids.json from assets to internal storage.
            try(FileOutputStream outputStream = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
                InputStream inputStream = context.getAssets().open(FILE_NAME)) {

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            Log.d(TAG, "Finished copying ssids.json to internal storage");
        }

        // Read ssids.json from internal storage.
        StringBuilder jsonStringBuilder = new StringBuilder();
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(ssidsJson)))){
            String line;
            while ((line = reader.readLine()) != null) {
                jsonStringBuilder.append(line);
            }
        }

        return new JSONObject(jsonStringBuilder.toString());
    }
}
