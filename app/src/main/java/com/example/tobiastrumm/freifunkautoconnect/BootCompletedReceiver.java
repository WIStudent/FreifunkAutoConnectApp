package com.example.tobiastrumm.freifunkautoconnect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Created by tobias on 01.08.2015.
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){
            // Check if NotificationService should run.
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
            if(sharedPref.getBoolean("pref_notification", false)){
                context.startService(new Intent(context, NotificationService.class));
            }
        }
    }
}
