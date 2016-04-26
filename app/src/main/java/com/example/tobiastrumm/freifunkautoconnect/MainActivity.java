package com.example.tobiastrumm.freifunkautoconnect;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;


public class MainActivity extends AppCompatActivity implements AddRemoveNetworksFragment.OnFragmentInteractionListener, RemoveAllDialogFragment.OnRemoveAllListener, AddAllDialogFragment.OnAddAllListener, NearestNodesFragment.OnFragmentInteractionListener{

    private static String TAG = MainActivity.class.getSimpleName();

    private MyFragmentPagerAdapter myFragmentPagerAdapter;


    private void checkForNewSsidFile(){
        SharedPreferences sharedPref = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        long lastCheck = sharedPref.getLong(getString(R.string.preference_timestamp_last_ssid_download), 0);
        long currentTime = System.currentTimeMillis() / 1000L;

        Log.d(TAG, "Current timestamp: " + currentTime + " last check timestamp: " + lastCheck);

        if(currentTime - lastCheck > 24*60*60){
            // Start DownloadSsidJsonService to check if a newer ssids.json file is available.
            Intent intent = new Intent(this, DownloadSsidJsonService.class);
            startService(intent);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Use Toolbar instead of ActionBar. See:
        // http://blog.xamarin.com/android-tips-hello-toolbar-goodbye-action-bar/
        // https://stackoverflow.com/questions/29055491/android-toolbar-for-api-19-for-api-21-works-ok
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Setup Tabs and Fragments
        String titles[] =  {getString(R.string.nearest_freifunk), getString(R.string.ssids)};
        myFragmentPagerAdapter = new MyFragmentPagerAdapter(getFragmentManager(), titles);

        ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
        viewPager.setAdapter(myFragmentPagerAdapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            int currentPosition = 0;

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int newPosition) {
                FragmentLifecycle fragmentToShow;
                switch (newPosition) {
                    case 0:
                        fragmentToShow = (FragmentLifecycle) myFragmentPagerAdapter.nearestNodesFragment;
                        break;
                    case 1:
                        fragmentToShow = (FragmentLifecycle) myFragmentPagerAdapter.addRemoveNetworksFragment;
                        break;
                    default:
                        fragmentToShow = new FragmentLifecycle() {
                            @Override
                            public void onPauseFragment() {
                            }

                            @Override
                            public void onResumeFragment() {
                            }
                        };
                }

                FragmentLifecycle fragmentToHide;
                switch (currentPosition) {
                    case 0:
                        fragmentToHide = (FragmentLifecycle) myFragmentPagerAdapter.nearestNodesFragment;
                        break;
                    case 1:
                        fragmentToHide = (FragmentLifecycle) myFragmentPagerAdapter.addRemoveNetworksFragment;
                        break;
                    default:
                        fragmentToHide = new FragmentLifecycle() {
                            @Override
                            public void onPauseFragment() {
                            }

                            @Override
                            public void onResumeFragment() {
                            }
                        };
                }

                if (fragmentToShow != null) {
                    fragmentToShow.onResumeFragment();
                }
                if (fragmentToHide != null) {
                    fragmentToHide.onPauseFragment();
                }


                currentPosition = newPosition;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabLayout);
        tabLayout.setTabMode(TabLayout.MODE_FIXED);
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
        tabLayout.setupWithViewPager(viewPager);


        // Start NotificationService if it should running but isn't
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean notifications = sharedPref.getBoolean("pref_notification", false);
        if(notifications && !isNotificationServiceRunning()){
            startService(new Intent(this, NotificationService.class));
        }

        checkForNewSsidFile();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_info) {
            Intent intent = new Intent(MainActivity.this, InfoActivity.class);
            startActivity(intent);
            return true;
        }

        else if (id == R.id.action_settings){
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }


        return super.onOptionsItemSelected(item);
    }

    private boolean isNotificationServiceRunning(){
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for ( ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if(NotificationService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addAllNetworks() {
        myFragmentPagerAdapter.addRemoveNetworksFragment.addAllNetworks();
    }

    @Override
    public void removeAllNetworks() {
        myFragmentPagerAdapter.addRemoveNetworksFragment.removeAllNetworks();
    }

    @Override
    public void showDialogAddAllNetworks() {
        AddAllDialogFragment df = new AddAllDialogFragment();
        df.show(this.getFragmentManager(),"");
    }

    @Override
    public void showDialogRemoveAllNetworks() {
        RemoveAllDialogFragment df = new RemoveAllDialogFragment();
        df.show(this.getFragmentManager(), "");
    }

    @Override
    public void showDialogSSIDRemovalFailed() {
        RemovalFailedDialogFragment df = new RemovalFailedDialogFragment();
        df.show(this.getFragmentManager(), "");
    }
}