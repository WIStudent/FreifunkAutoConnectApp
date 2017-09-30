package com.example.tobiastrumm.freifunkautoconnect;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.WakefulBroadcastReceiver;

public class BootCompletedReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){
            // Check if NotificationService should run.
            SharedPreferences sharedPref = context.getSharedPreferences(context.getString(R.string.shared_preference_key_settings), Context.MODE_PRIVATE);
            if(sharedPref.getBoolean("pref_notification", false)){
                Intent startServiceIntent = new Intent(context, NotificationService.class);
                startWakefulService(context, startServiceIntent);
            }
        }
    }
}
