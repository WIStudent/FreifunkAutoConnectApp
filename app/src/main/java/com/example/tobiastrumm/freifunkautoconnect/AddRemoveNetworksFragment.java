package com.example.tobiastrumm.freifunkautoconnect;

import android.app.Activity;
import android.app.Fragment;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;
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


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link AddRemoveNetworksFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link AddRemoveNetworksFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class AddRemoveNetworksFragment extends Fragment implements AdapterView.OnItemClickListener, FragmentLifecycle{

    private static final String TAG = AddRemoveNetworksFragment.class.getSimpleName();

    public static String DIRECTORY = "freifunkautoconnect";
    public static String USER_SSIDS_FILE = "user_ssids.csv";

    private OnFragmentInteractionListener mListener;

    private ArrayList<Network> allNetworks;
    private ArrayList<Network> shownNetworks;

    private NetworkAdapter na;
    private WifiManager wm;

    private SearchView searchView;
    private String last_filter_term;

    private class AddAllNetworksResponseReceiver extends BroadcastReceiver{
        private AddAllNetworksResponseReceiver(){}

        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getStringExtra(AddAllNetworksService.STATUS_TYPE)){
                case AddAllNetworksService.STATUS_TYPE_FINISHED:
                    checkActiveNetworks();
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
        shownNetworks = new ArrayList<>(allNetworks);
        wm = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_add_remove_networks, container, false);
        ListView lv = (ListView) view.findViewById(R.id.lv_networks);
        lv.setOnItemClickListener(this);

        na = new NetworkAdapter(getActivity(), allNetworks, shownNetworks);
        lv.setAdapter(na);

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
    public void onStart() {
        super.onStart();
        // Check which Networks are already saved in the network configuration
        checkActiveNetworks();
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
    }


    @Override
    public void onPauseFragment() {

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
                na.getFilter().filter(newText);
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
        last_filter_term = searchView.getQuery().toString();
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


        // Read user defined ssids
        // Check if external storage is available
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            File user_ssids = new File(Environment.getExternalStorageDirectory() + File.separator + DIRECTORY + File.separator + USER_SSIDS_FILE );
            // Check if file exists
            if(!user_ssids.exists()){
                // If not, create the file
                Log.i(TAG, "Start creation of user_ssids.csv file");
                user_ssids = createUserSSIDFile();
            }
            else{
                Log.i(TAG, "user_ssids.csv already exists");
            }
            // If the file was found/created:
            if(user_ssids != null){
                is = new InputStreamReader(new FileInputStream(user_ssids));
                reader = new BufferedReader(is);
                while ((line = reader.readLine()) != null) {
                    allNetworks.add(new Network(line));
                }
            }
            else{
                Log.w(TAG, "Could not find or create user_ssids file.");
            }
        }
        Collections.sort(allNetworks);
    }

    private File createUserSSIDFile(){
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            File directory = new File(Environment.getExternalStorageDirectory() + File.separator + DIRECTORY);
            if(!directory.exists()){
                // Create directory
                Log.i(TAG, "Create freifunkautoconnect directory");
                directory.mkdir();
            }
            File user_ssids = new File(directory, USER_SSIDS_FILE);
            try {
                // Create empty file
                Log.i(TAG, "Create empty user_ssids.csv file");
                user_ssids.createNewFile();
            }
            catch (IOException e) {
                Log.w(TAG, "Could not create user_ssids.csv file.");
                return null;
            }

            // Make sure that the new file will be visible if the device is connected to a pc over USB cable.
            Log.i(TAG, "Scan for user_ssids.csv file");
            MediaScannerConnection.scanFile(
                    getActivity(),
                    new String[]{directory.getAbsolutePath(), user_ssids.getAbsolutePath(),},
                    null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i(TAG, "Scanned " + path + ":");
                            Log.i(TAG, "-> uri=" + uri);
                        }
                    }
            );
        }
        return null;
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
            na.notifyAllNetworksHasChangedReapplyFilter();
        }

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Get the Network object that was clicked. Must be looked up in shownNetworks because it contains
        // all Networks, that are currently shown in the ListView.
        Network n = shownNetworks.get(position);
        // If the network is already saved in the network configuration, remove it
        if(n.active){
            n.active = false;
            List<WifiConfiguration> wificonf = wm.getConfiguredNetworks();
            if(wificonf != null) {
                for (WifiConfiguration wc : wificonf) {
                    if (wc.SSID.equals(n.ssid)) {
                        wm.removeNetwork(wc.networkId);
                    }
                }
            }
        }
        // If the network is not in the network configuration, add it.
        else{
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
        // Notify the NetworkAdapter that the content of ArrayList allNetworks was changed.
        na.notifyDataSetChanged();
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
        int progressBarMax = shownNetworks.size();
        mListener.showProgressDialog(progressBarMax);

        //Start AddAllNetworksService
        Intent intent = new Intent(getActivity(), AddAllNetworksService.class);
        intent.putParcelableArrayListExtra(AddAllNetworksService.INPUT_NETWORKS, shownNetworks);
        getActivity().startService(intent);
    }

    public void removeAllNetworks(){
        // Tell Activity to show a ProgressDialog
        int progressBarMax = shownNetworks.size();
        mListener.showProgressDialog(progressBarMax);

        //Start RemoveAllNetworksService
        Intent intent = new Intent(getActivity(), RemoveAllNetworksService.class);
        intent.putParcelableArrayListExtra(RemoveAllNetworksService.INPUT_NETWORKS, shownNetworks);
        getActivity().startService(intent);
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

        /**
         * Should open a ProgressDialog with progressBarMax as max value. Get the current progress by listening to
         * the broadcast of AddAllNetworksService and RemoveAllNetworksService
         * @param progressBarMax
         */
        public void showProgressDialog(int progressBarMax);
    }

}
