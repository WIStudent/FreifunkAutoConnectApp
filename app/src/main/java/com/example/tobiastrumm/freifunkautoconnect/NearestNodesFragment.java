package com.example.tobiastrumm.freifunkautoconnect;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.percent.PercentRelativeLayout;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link NearestNodesFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link NearestNodesFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class NearestNodesFragment extends Fragment implements AdapterView.OnItemClickListener, FragmentLifecycle, FragmentCompat.OnRequestPermissionsResultCallback{

    private final static String TAG = NearestNodesFragment.class.getSimpleName();

    private final static int PERMISSION_REQUEST_FINE_LOCATION = 1;

    private OnFragmentInteractionListener mListener;

    // ArrayList
    private ArrayList<Node> nodes;
    private NodeAdapter nodeAdapter;

    // TextView last_update
    private TextView tv_last_update;
    private String last_updated_text;

    // ProgressBar
    private ProgressBar progressBar;
    private RelativeLayout relativeLayout;
    private boolean showProgress;

    // GPS warning
    private PercentRelativeLayout rl_location_warning;
    private boolean showLocationWarning = false;

    private int last_orientation;

    private SwipeRefreshLayout swipeContainer;

    private FindNearestNodesResponseReceiver findNearestNodesResponseReceiver;

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Dont let the user click on a network if the list is updated.
        if(!swipeContainer.isRefreshing()) {
            Node n = nodes.get(position);
            // geo:0,0?q=lat,lng(label)
            Uri geoLocation = Uri.parse("geo:0,0?q=" + n.lat + "," + n.lon + "(" + n.name + ")");
            Intent navIntent = new Intent(Intent.ACTION_VIEW);
            navIntent.setData(geoLocation);
            if (navIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                Intent chooser = Intent.createChooser(navIntent, getString(R.string.open_coordinates_with));
                startActivity(chooser);
            }
        }
    }

    private class FindNearestNodesResponseReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getStringExtra(FindNearestNodesService.STATUS_TYPE)){
                case FindNearestNodesService.STATUS_TYPE_FINISHED:
                    Node[] received_nodes = (Node[])intent.getParcelableArrayExtra(FindNearestNodesService.RETURN_NODES);
                    List<Node> nodelist = Arrays.asList(received_nodes);
                    nodes.clear();
                    nodes.addAll(nodelist);
                    nodeAdapter.notifyDataSetChanged();

                    long timestamp = intent.getLongExtra(FindNearestNodesService.RETURN_LAST_UPDATE, 0);
                    String last_update_string_date = DateFormat.getDateFormat(getActivity()).format(new Date(timestamp * 1000));
                    String last_update_string_time = DateFormat.getTimeFormat(getActivity()).format(new Date(timestamp * 1000));
                    last_updated_text = getString(R.string.last_data_update) + last_update_string_date + " " + last_update_string_time;
                    tv_last_update.setText(last_updated_text);
                    // show found nodes.
                    showNodeList();
                    break;
                case FindNearestNodesService.STATUS_TYPE_ERROR:
                    //TODO: Something went wrong
                    Log.w(TAG, "Broadcast Receiver: FindNearestNodesService responded: Something went wrong.");
                    // Show error.
                    showLocationWarning();
                    break;
            }
            swipeContainer.setRefreshing(false);
        }
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.

     * @return A new instance of fragment NearestNodesFragment.
     */
    public static NearestNodesFragment newInstance() {
        return new NearestNodesFragment();
    }

    public NearestNodesFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setupBroadcastReceivers();
        nodes = new ArrayList<>();
        last_updated_text = getString(R.string.last_data_update) + "-";
        last_orientation = getResources().getConfiguration().orientation;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_nearest_nodes, container, false);

        relativeLayout = (RelativeLayout)view.findViewById(R.id.rl_nearest_nodes);

        nodeAdapter = new NodeAdapter(getActivity(), nodes);
        ListView lv_nearest_nodes = (ListView) view.findViewById(R.id.lv_nearest_nodes);
        lv_nearest_nodes.setAdapter(nodeAdapter);
        lv_nearest_nodes.setOnItemClickListener(this);

        tv_last_update = (TextView)view.findViewById(R.id.tv_last_update);
        tv_last_update.setText(last_updated_text);

        progressBar = (ProgressBar)view.findViewById(R.id.progressbar_loading);

        rl_location_warning = (PercentRelativeLayout)view.findViewById(R.id.rl_location_warning);
        Button btn_location_warning_retry = (Button) view.findViewById(R.id.btn_location_warning_retry);
        btn_location_warning_retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NearestNodesFragment.this.startFindNearestNodesServiceWithProgressBar();
            }
        });
        Button btn_location_warning_permission = (Button) view.findViewById(R.id.btn_location_warning_permission);
        if(android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
        {
            // If the running Android OS is older than Android M (SDK 23), do not show this button
            btn_location_warning_permission.setVisibility(View.GONE);
        }
        {
            btn_location_warning_permission.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NearestNodesFragment.this.openApplicationSettingsPage();
                }
            });
        }

        swipeContainer = (SwipeRefreshLayout) view.findViewById(R.id.sc_nearest_nodes);
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Start FindNearestNodeService
                startFindNearestNodesService();
            }
        });
        // Configure the refreshing colors
        swipeContainer.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        return view;
    }


    private void showProgressBar(){
        showLocationWarning = false;
        rl_location_warning.setVisibility(RelativeLayout.GONE);
        if(progressBar != null){
            showProgress = true;
            relativeLayout.setVisibility(RelativeLayout.GONE);
            progressBar.setVisibility(ProgressBar.VISIBLE);
        }
    }

    private void showNodeList(){
        if(progressBar != null){
            showProgress = false;
            progressBar.setVisibility(ProgressBar.GONE);

        }
        rl_location_warning.setVisibility(RelativeLayout.GONE);
        showLocationWarning = false;
        relativeLayout.setVisibility(RelativeLayout.VISIBLE);
    }

    private void showLocationWarning(){
        showLocationWarning = true;
        if(progressBar != null){
            showProgress = false;
            progressBar.setVisibility(ProgressBar.GONE);
        }
        relativeLayout.setVisibility(RelativeLayout.GONE);
        rl_location_warning.setVisibility(RelativeLayout.VISIBLE);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Call startFineNearestNodesService if the execution of onStart was not caused by a change in orientation.
        int new_orientation = getResources().getConfiguration().orientation;
        if(new_orientation == last_orientation){
            startFindNearestNodesServiceWithProgressBar();
        }
        last_orientation = new_orientation;
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

        if(showProgress && isFindNearestNodesServiceRunning()){
            showProgressBar();
        }
        else{
            showProgress = false;
        }

        if(showLocationWarning){
            showLocationWarning();
        }
        else if(!showProgress){
            showNodeList();
        }
    }


    @Override
    public void onPauseFragment() {

    }

    @Override
    public void onResumeFragment() {
        // Dont start it if it is already running.
        if(!isFindNearestNodesServiceRunning()){
            startFindNearestNodesServiceWithProgressBar();
        }

    }

    /**
     * Starts FindNearestNodesService
     */
    private void startFindNearestNodesService(){

        // Since API level 23 permissions must be requested at run time. FindNearestNodeService requires ACCESS_FINE_LOCATION
        int permissionCheck = ContextCompat.checkSelfPermission(this.getActivity(), Manifest.permission.ACCESS_FINE_LOCATION);
        if(permissionCheck == PackageManager.PERMISSION_GRANTED){
            // Start FindNearestNodesService
            Intent intent = new Intent(getActivity(), FindNearestNodesService.class);
            getActivity().startService(intent);
        }
        else{
            // Request permission
            FragmentCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    PERMISSION_REQUEST_FINE_LOCATION);
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch(requestCode){
            case PERMISSION_REQUEST_FINE_LOCATION:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    // The permission was granted. Start FineNearestNodesService
                    Intent intent = new Intent(getActivity(), FindNearestNodesService.class);
                    getActivity().startService(intent);
                }
                else{
                    // The permission was denied. Inform the user about this.
                    showLocationWarning();
                }
        }
    }

    /*
        Open the Application's setting page
     */
    private void openApplicationSettingsPage(){
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", this.getActivity().getPackageName(), null);
        intent.setData(uri);
        this.getActivity().startActivity(intent);
    }

    /**
     * Shows ProgressBar and starts the FindNearestNodesService
     */
    private void startFindNearestNodesServiceWithProgressBar(){
        // Show ProgressBar
        showProgressBar();
        startFindNearestNodesService();
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
    }

    private void setupBroadcastReceivers(){
        findNearestNodesResponseReceiver = new FindNearestNodesResponseReceiver();

    }

    private void registerBroadcastReceivers(){
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getActivity());
        IntentFilter findNearestNodesIntentFilter = new IntentFilter(FindNearestNodesService.BROADCAST_ACTION);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(findNearestNodesResponseReceiver, findNearestNodesIntentFilter);
    }

    private void unregisterBroadcastReceivers(){
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(getActivity());
        lbm.unregisterReceiver(findNearestNodesResponseReceiver);
    }

    private boolean isFindNearestNodesServiceRunning(){
        ActivityManager manager = (ActivityManager) getActivity().getSystemService(Activity.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (FindNearestNodesService.class.getName().equals(service.service.getClassName())) {
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
    }

}
