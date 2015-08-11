package com.example.tobiastrumm.freifunkautoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;


public class SettingsActivity extends ActionBarActivity {

    public static class SettingsFragment extends PreferenceFragment {
        SwitchPreference switchfNoNotificationConnected;
        SwitchPreference switchVibrate;
        SwitchPreference switchPlaySound;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            PreferenceManager prefMan = getPreferenceManager();

            SwitchPreference switchPref = (SwitchPreference)prefMan.findPreference("pref_notification");
            switchPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

                public boolean onPreferenceChange(Preference preference, Object newValue) {

                    boolean myValue = (Boolean) newValue;

                    if (myValue)
                        getActivity().startService(new Intent(getActivity(), NotificationService.class));
                    else
                        getActivity().stopService(new Intent(getActivity(), NotificationService.class));

                    switchfNoNotificationConnected.setEnabled(myValue);
                    switchVibrate.setEnabled(myValue);
                    switchPlaySound.setEnabled(myValue);

                    return true;
                }
            });

            switchfNoNotificationConnected = (SwitchPreference)prefMan.findPreference("pref_no_notification_connected");
            switchfNoNotificationConnected.setEnabled(switchPref.isChecked());

            switchVibrate = (SwitchPreference) prefMan.findPreference("pref_notification_vibrate");
            switchVibrate.setEnabled(switchPref.isChecked());

            switchPlaySound = (SwitchPreference) prefMan.findPreference("pref_notification_sound");
            switchPlaySound.setEnabled(switchPref.isChecked());

            Preference.OnPreferenceChangeListener restartServiceListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    // Restart NotificationService
                    getActivity().stopService(new Intent(getActivity(), NotificationService.class));
                    getActivity().startService(new Intent(getActivity(), NotificationService.class));
                    return true;
                }
            };
            switchfNoNotificationConnected.setOnPreferenceChangeListener(restartServiceListener);
            switchVibrate.setOnPreferenceChangeListener(restartServiceListener);
            switchPlaySound.setOnPreferenceChangeListener(restartServiceListener);

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Neccessary or a new SettingsFragment will be created every time the screen is rotated.
        if(savedInstanceState == null) {
            // Display the fragment as the main content.
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, new SettingsFragment())
                    .commit();
        }


    }
}
