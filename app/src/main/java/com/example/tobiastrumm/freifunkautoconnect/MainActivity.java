package com.example.tobiastrumm.freifunkautoconnect;

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.TabLayout;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;


public class MainActivity extends AppCompatActivity implements AddRemoveNetworksFragment.OnFragmentInteractionListener, RemoveAllDialogFragment.OnRemoveAllListener, AddAllDialogFragment.OnAddAllListener, NearestNodesFragment.OnFragmentInteractionListener{

    private ProgressDialog progress;
    private int progressBarMax;
    private int progressBarProgress;
    private boolean progressBarRunning = false;

    private static final String STATE_PROGRESSBAR_RUNNING = "state_progressbar_running";
    private static final String STATE_PROGRESSBAR_MAX = "state_progressbar_max";
    private static final String STATE_PROGRESSBAR_PROGRESS = "state_progressbar_progress";

    private static String TAG = MainActivity.class.getSimpleName();

    private MyFragmentPagerAdapter myFragmentPagerAdapter;

    private class AddAllNetworksResponseReceiver extends BroadcastReceiver {
        private AddAllNetworksResponseReceiver(){}

        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getStringExtra(AddAllNetworksService.STATUS_TYPE)){
                case AddAllNetworksService.STATUS_TYPE_FINISHED:
                    if(progress != null){
                        progress.cancel();
                    }
                    progressBarRunning = false;
                    // Unregister this receiver
                    LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                    break;

                case AddAllNetworksService.STATUS_TYPE_PROGRESS:
                    progressBarProgress = intent.getIntExtra(AddAllNetworksService.STATUS_PROGRESS, 0);
                    if(progress != null) {
                        progress.setProgress(progressBarProgress);
                    }
                    break;
            }
        }
    }

    private class RemoveAllNetworksResponseReceiver extends BroadcastReceiver{
        private RemoveAllNetworksResponseReceiver(){}

        @Override
        public void onReceive(Context context, Intent intent){
            switch(intent.getStringExtra(RemoveAllNetworksService.STATUS_TYPE)){
                case RemoveAllNetworksService.STATUS_TYPE_FINISHED:
                    if(progress != null){
                        progress.cancel();
                    }
                    progressBarRunning = false;
                    // Unregister this receiver
                    LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
                    break;

                case RemoveAllNetworksService.STATUS_TYPE_PROGRESS:
                    progressBarProgress = intent.getIntExtra(RemoveAllNetworksService.STATUS_PROGRESS, 0);
                    if(progress != null){
                        progress.setProgress(progressBarProgress);
                    }
                    break;
            }
        }
    }

    public void showProgressDialog(int maxValue){
        // Register Broadcast Receivers
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);

        AddAllNetworksResponseReceiver addAllNetworksResponseReceiver = new AddAllNetworksResponseReceiver();
        IntentFilter addAllIntentFilter = new IntentFilter(AddAllNetworksService.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(addAllNetworksResponseReceiver, addAllIntentFilter);

        RemoveAllNetworksResponseReceiver removeAllNetworksResponseReceiver = new RemoveAllNetworksResponseReceiver();
        IntentFilter removeAllIntentFilter = new IntentFilter(RemoveAllNetworksService.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(this).registerReceiver(removeAllNetworksResponseReceiver, removeAllIntentFilter);

        progress = new ProgressDialog(this);
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setIndeterminate(false);
        progress.setMax(maxValue);
        progress.setCancelable(false);
        progress.show();
        progressBarRunning = true;
    }


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

        // Recreate ProgressBar if screen was rotated.
        if(savedInstanceState != null){
            progressBarRunning = savedInstanceState.getBoolean(STATE_PROGRESSBAR_RUNNING);
            // Only recreate the progress dialog if the service hasn't stopped running while the activity was recreated.
            if(progressBarRunning && (isAddAllNetworkServiceRunning() || isRemoveAllNetworkServiceRunning())){
                progressBarMax = savedInstanceState.getInt(STATE_PROGRESSBAR_MAX);
                progressBarProgress = savedInstanceState.getInt(STATE_PROGRESSBAR_PROGRESS);
                showProgressDialog(progressBarMax);
            }
        }


        // Use Toolbar instead of ActionBar. See:
        // http://blog.xamarin.com/android-tips-hello-toolbar-goodbye-action-bar/
        // https://stackoverflow.com/questions/29055491/android-toolbar-for-api-19-for-api-21-works-ok
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Setup Tabs and Fragments
        myFragmentPagerAdapter = new MyFragmentPagerAdapter(getFragmentManager());

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
                switch(newPosition){
                    case 0:
                        fragmentToShow = (FragmentLifecycle)myFragmentPagerAdapter.addRemoveNetworksFragment;
                        break;
                    case 1:
                        fragmentToShow = (FragmentLifecycle)myFragmentPagerAdapter.nearestNodesFragment;
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
                switch(currentPosition){
                    case 0:
                        fragmentToHide = (FragmentLifecycle)myFragmentPagerAdapter.addRemoveNetworksFragment;
                        break;
                    case 1:
                        fragmentToHide = (FragmentLifecycle)myFragmentPagerAdapter.nearestNodesFragment;
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

                if(fragmentToShow != null){
                    fragmentToShow.onResumeFragment();
                }
                if(fragmentToHide != null){
                    fragmentToHide.onPauseFragment();
                }


                currentPosition = newPosition;
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabLayout);
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
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save state of the ProgressDialog
        savedInstanceState.putBoolean(STATE_PROGRESSBAR_RUNNING, progressBarRunning);
        savedInstanceState.putInt(STATE_PROGRESSBAR_MAX, progressBarMax);
        savedInstanceState.putInt(STATE_PROGRESSBAR_PROGRESS, progressBarProgress);

        super.onSaveInstanceState(savedInstanceState);
    }



    @Override
    protected void onDestroy() {
        // Destroy the ProgressDialog if it is still running
        if(progress != null){
            progress.dismiss();
        }
        super.onDestroy();
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


    private boolean isAddAllNetworkServiceRunning(){
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (AddAllNetworksService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isRemoveAllNetworkServiceRunning(){
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        for( ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if(RemoveAllNetworksService.class.getName().equals((service.service.getClassName()))) {
                return true;
            }
        }
        return false;
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
}