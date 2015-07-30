package com.example.tobiastrumm.freifunkautoconnect;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;


public class SettingsActivity extends ActionBarActivity {

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            SwitchPreference switchPref = (SwitchPreference)getPreferenceManager().findPreference("pref_notification");
            switchPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

                public boolean onPreferenceChange(Preference preference, Object newValue) {

                    boolean myValue = (Boolean) newValue;

                    if (myValue)
                        getActivity().startService(new Intent(getActivity(), NotificationService.class));
                    else
                        getActivity().stopService(new Intent(getActivity(), NotificationService.class));

                    return true;
                }
            });

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .add(R.id.fragment_container, new SettingsFragment())
                .commit();



    }
}
