package com.example.tobiastrumm.freifunkautoconnect;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// TODO Maybe move the whole getSSID logic into the NodeRecyclerAdapter

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link AddRemoveNetworksFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link AddRemoveNetworksFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AddRemoveNetworksFragment extends Fragment implements NetworkRecyclerAdapter.OnItemClickListener, FragmentLifecycle{

    private static final String TAG = AddRemoveNetworksFragment.class.getSimpleName();

    private OnFragmentInteractionListener mListener;

    private ArrayList<Network> allNetworks;

    // Network
    private NetworkRecyclerAdapter networkRecyclerAdapter;
    private WifiManager wm;

    // SearchView
    private SearchView searchView;
    private String last_filter_term;

    // ProgressBar
    private LinearLayout linearLayout;
    private RelativeLayout relativeLayout;
    private ProgressBar progressBar;
    private TextView tv_progress;
    private boolean showProgress;
    private int progress_max_value;

    private class AddAllNetworksResponseReceiver extends BroadcastReceiver{
        private AddAllNetworksResponseReceiver(){}

        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getStringExtra(AddAllNetworksService.STATUS_TYPE)){
                case AddAllNetworksService.STATUS_TYPE_FINISHED:
                    checkActiveNetworks();
                    hideProgressBar();
                    break;
                case RemoveAllNetworksService.STATUS_TYPE_PROGRESS:
                    int progressBarProgress = intent.getIntExtra(AddAllNetworksService.STATUS_PROGRESS, 0);
                    updateProgressBar(progressBarProgress);
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
                    checkActiveNetworks();
                    hideProgressBar();
                    break;
                case RemoveAllNetworksService.STATUS_TYPE_PROGRESS:
                    int progressBarProgress = intent.getIntExtra(AddAllNetworksService.STATUS_PROGRESS, 0);
                    updateProgressBar(progressBarProgress);
                    break;
            }
        }
    }

    private class DownloadSsidJsonResponseReceiver extends BroadcastReceiver {
        private DownloadSsidJsonResponseReceiver(){}

        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getStringExtra(DownloadSsidJsonService.STATUS_TYPE)){
                case DownloadSsidJsonService.STATUS_TYPE_REPLACED:
                    try {
                        // Read ssid from file again.
                        getSSIDs();
                        checkActiveNetworks();
                        // caling na.notifyAllNetworksHasChangedReapplyFilter isn't necessary here because it was already called at the end of
                        // checkActiveNetworks
                        Log.d(TAG, "SSIDs were refreshed");

                        // Notify user that a new SSID list was downloaded.
                        Toast toast = Toast.makeText(getActivity(), getString(R.string.message_ssids_updated), Toast.LENGTH_LONG);
                        toast.show();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    }

    AddAllNetworksResponseReceiver addAllNetworksResponseReceiver;
    RemoveAllNetworksResponseReceiver removeAllNetworksResponseReceiver;
    DownloadSsidJsonResponseReceiver downloadSsidJsonResponseReceiver;

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.

     * @return A new instance of fragment AddRemoveNetworksFragment.
     */
    public static AddRemoveNetworksFragment newInstance() {
        return new AddRemoveNetworksFragment();
    }

    public AddRemoveNetworksFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);

        setupBroadcastReceivers();

        allNetworks = new ArrayList<>();
        try{
            getSSIDs();
        }
        catch(IOException e){
            // Could not read SSIDs from csv file.
        }
        wm = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);

        // Setup NodeRecyclerAdapter
        networkRecyclerAdapter = new NetworkRecyclerAdapter(new ArrayList<>(allNetworks));
        networkRecyclerAdapter.setOnItemClickListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_add_remove_networks, container, false);

        linearLayout = (LinearLayout) view.findViewById(R.id.ll_add_remove_networks);
        progressBar = (ProgressBar) view.findViewById(R.id.progressbar_add_remove_networks);
        tv_progress = (TextView) view.findViewById(R.id.tv_progresbar);
        relativeLayout = (RelativeLayout) view.findViewById(R.id.rl_add_remove_networks);

        // Setup RecyclerView
        RecyclerView rv = (RecyclerView) view.findViewById(R.id.rv_networks);
        rv.setAdapter(networkRecyclerAdapter);
        rv.setLayoutManager(new LinearLayoutManager(getActivity()));

        // Set OnClickListeners for the buttons
        view.findViewById(R.id.btn_add_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mListener.showDialogAddAllNetworks();
            }
        });
        view.findViewById(R.id.btn_remove_all).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mListener.showDialogRemoveAllNetworks();
            }
        });

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterBroadcastReceivers();
    }

    @Override
    public void onResume() {
        super.onResume();
        registerBroadcastReceivers();
        // Check which Networks are already saved in the network configuration
        checkActiveNetworks();

        // The service could have finished while no Broadcast Receiver was registered that could have received the signal to set showProgress to false;
        if(showProgress && (isAddAllNetworkServiceRunning() || isRemoveAllNetworkServiceRunning())){
            showProgressBar(progress_max_value);
        }
        else{
            hideProgressBar();
        }
    }


    @Override
    public void onPauseFragment() {
        // Save the last filter term. It is necessary to recreate the last search if the user swiped/paged to a different tab.
        last_filter_term = searchView.getQuery().toString();
    }

    @Override
    public void onResumeFragment() {
        // Check which Networks are already saved in the network configuration
        checkActiveNetworks();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_add_remove_fragment, menu);
        // Setup SearchView
        SearchManager searchManager = (SearchManager)getActivity().getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchItem = menu.findItem(R.id.search);
        searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                networkRecyclerAdapter.getFilter().filter(newText);
                return true;
            }
        });
        // Recreate the last search if the screen was rotated
        if(last_filter_term != null && last_filter_term.length() > 0){
            searchView.setQuery(last_filter_term, true);
            searchView.setIconified(false);
            searchView.clearFocus();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnFragmentInteractionListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
        // Save the last filter term. It is necessary to recreate the last search if the screen was rotated.
        if(searchView != null){
            last_filter_term = searchView.getQuery().toString();
        }

    }

    private void showProgressBar(int maxValue) {
        showProgress = true;
        progress_max_value = maxValue;
        progressBar.setMax(maxValue);
        tv_progress.setText("0/" + progress_max_value);
        relativeLayout.setVisibility(RelativeLayout.GONE);
        searchView.setVisibility(SearchView.GONE);
        linearLayout.setVisibility(LinearLayout.VISIBLE);
    }

    private void hideProgressBar(){
        showProgress = false;
        linearLayout.setVisibility(LinearLayout.GONE);
        if(searchView != null){
            searchView.setVisibility(SearchView.VISIBLE);
        }
        relativeLayout.setVisibility(RelativeLayout.VISIBLE);
    }

    private void updateProgressBar(int value){
        progressBar.setProgress(value);
        tv_progress.setText(value + "/" + progress_max_value);
    }

    private void getSSIDs() throws IOException {
        // Check if ssids.json exists in internal storage.
        File ssidsJson = getActivity().getFileStreamPath("ssids.json");
        if(!ssidsJson.exists()){
            Log.d(TAG, "Copy ssids.json to internal storage.");
            // If not, copy ssids.json from assets to internal storage.
            FileOutputStream outputStream = getActivity().openFileOutput("ssids.json", Context.MODE_PRIVATE);
            InputStream inputStream = getActivity().getAssets().open("ssids.json");
            byte[] buffer = new byte[1024];
            int bytesRead;
            while((bytesRead = inputStream.read(buffer)) != -1){
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            outputStream.close();
            Log.d(TAG, "Finished copying ssids.json to internal storage");
        }

        // Read ssids.json from internal storage.
        String jsonString = "";
        InputStreamReader is = new InputStreamReader(new FileInputStream(ssidsJson));
        BufferedReader reader = new BufferedReader(is);
        String line;
        while ((line = reader.readLine()) != null) {
            jsonString += line;
        }
        reader.close();

        // Read SSIDs from JSON file
        allNetworks.clear();
        try {
            JSONObject json = new JSONObject(jsonString);
            JSONArray ssidsJsonArray = json.getJSONArray("ssids");
            for(int i = 0; i<ssidsJsonArray.length(); i++){
                allNetworks.add(new Network('"' + ssidsJsonArray.getString(i) + '"'));
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }

        Collections.sort(allNetworks);
    }

    private void checkActiveNetworks(){
        // Check which Network is already added to the network configuration
        List<WifiConfiguration> wifiConf = wm.getConfiguredNetworks();
        if (wifiConf != null) {
            Collections.sort(wifiConf, new WifiConfigurationComparator());
            for (Network n : allNetworks) {
                // Search for the current network in the network configuration. If it is found (index will be >= 0), set active to true
                int index = Collections.binarySearch(wifiConf, n.ssid, new WifiConfigurationSSIDComparator());
                n.active = index >= 0;
            }
            // Update networkRecyclerAdapter
            networkRecyclerAdapter.clear();
            networkRecyclerAdapter.addAll(allNetworks);
        }

    }

    @Override
    public void onItemClick(View itemView, int layoutPosition, int adapterPosition) {
        if(adapterPosition >= 0) {
            // Get the Network object that was clicked.
            Network n = networkRecyclerAdapter.getNetwork(adapterPosition);
            // If the network is already saved in the network configuration, remove it
            if (n.active) {
                List<WifiConfiguration> wificonf = wm.getConfiguredNetworks();
                if (wificonf != null) {
                    for (WifiConfiguration wc : wificonf) {
                        if (wc.SSID.equals(n.ssid)) {
                            // Only set active to false if the removal was successful
                            n.active = !wm.removeNetwork(wc.networkId);
                        }
                    }
                }
            }
            // If the network is not in the network configuration, add it.
            else {
                n.active = true;
                // Add Network to network configuration
                WifiConfiguration wc = new WifiConfiguration();
                wc.SSID = n.ssid;
                wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                int networkId = wm.addNetwork(wc);
                wm.enableNetwork(networkId, false);
            }
            // Save configuration
            wm.saveConfiguration();
            // Notify the networkRecyclerAdapter that the item at adapterPosition was changed.
            networkRecyclerAdapter.notifyItemChanged(adapterPosition);
        }
    }

    private void setupBroadcastReceivers(){
        addAllNetworksResponseReceiver = new AddAllNetworksResponseReceiver();
        removeAllNetworksResponseReceiver = new RemoveAllNetworksResponseReceiver();
        downloadSsidJsonResponseReceiver = new DownloadSsidJsonResponseReceiver();

    }

    private void registerBroadcastReceivers(){
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getActivity());

        IntentFilter addAllIntentFilter = new IntentFilter(AddAllNetworksService.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(addAllNetworksResponseReceiver, addAllIntentFilter);

        IntentFilter removeAllIntentFilter = new IntentFilter(RemoveAllNetworksService.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(removeAllNetworksResponseReceiver, removeAllIntentFilter);

        IntentFilter downloadSsidJsonIntentFilter = new IntentFilter(DownloadSsidJsonService.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(downloadSsidJsonResponseReceiver, downloadSsidJsonIntentFilter);
    }

    private void unregisterBroadcastReceivers(){
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getActivity());
        lbm.unregisterReceiver(addAllNetworksResponseReceiver);
        lbm.unregisterReceiver(removeAllNetworksResponseReceiver);
        lbm.unregisterReceiver(downloadSsidJsonResponseReceiver);
    }



    public void addAllNetworks(){
        // Add only currently shown networks
        // Tell Activity to show a ProgressDialog
        int progressBarMax = networkRecyclerAdapter.getItemCount();
        showProgressBar(progressBarMax);
        updateProgressBar(0);

        //Start AddAllNetworksService
        Intent intent = new Intent(getActivity(), AddAllNetworksService.class);
        intent.putParcelableArrayListExtra(AddAllNetworksService.INPUT_NETWORKS, new ArrayList<>(networkRecyclerAdapter.getShownNetworks()));
        getActivity().startService(intent);
    }

    public void removeAllNetworks(){
        // Tell Activity to show a ProgressDialog
        int progressBarMax = networkRecyclerAdapter.getItemCount();
        showProgressBar(progressBarMax);
        updateProgressBar(0);

        //Start RemoveAllNetworksService
        Intent intent = new Intent(getActivity(), RemoveAllNetworksService.class);
        intent.putParcelableArrayListExtra(RemoveAllNetworksService.INPUT_NETWORKS, new ArrayList<>(networkRecyclerAdapter.getShownNetworks()));
        getActivity().startService(intent);
    }


    private boolean isAddAllNetworkServiceRunning(){
        ActivityManager manager = (ActivityManager) getActivity().getSystemService(Activity.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (AddAllNetworksService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isRemoveAllNetworkServiceRunning(){
        ActivityManager manager = (ActivityManager) getActivity().getSystemService(Activity.ACTIVITY_SERVICE);
        for( ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if(RemoveAllNetworksService.class.getName().equals((service.service.getClassName()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        /**
         * Should open a dialog to confirm that all shown networks should be added to the network configuration.
         */
        public void showDialogAddAllNetworks();

        /**
         * Should open a dialog to confirm that all shown networks should be removed from the network configuration.
         */
        public void showDialogRemoveAllNetworks();
    }

}
