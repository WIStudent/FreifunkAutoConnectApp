package com.example.tobiastrumm.freifunkautoconnect;

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
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
public class NearestNodesFragment extends Fragment implements AdapterView.OnItemClickListener, FragmentLifecycle{

    private final static String TAG = NearestNodesFragment.class.getSimpleName();

    private OnFragmentInteractionListener mListener;

    private ArrayList<Node> nodes;
    private NodeAdapter nodeAdapter;
    private TextView tv_last_update;

    private FindNearestNodesResponseReceiver findNearestNodesResponseReceiver;

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Node n = nodes.get(position);
        // geo:0,0?q=lat,lng(label)
        Uri geoLocation = Uri.parse("geo:0,0?q=" + n.lat + "," + n.lon + "(" + n.name + ")");
        Intent navIntent = new Intent(Intent.ACTION_VIEW);
        navIntent.setData(geoLocation);
        if(navIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            Intent chooser = Intent.createChooser(navIntent, "Select app");
            startActivity(chooser);
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
                    tv_last_update.setText("Last updated: " + last_update_string_date + " " + last_update_string_time);
                    break;
                case FindNearestNodesService.STATUS_TYPE_ERROR:
                    //TODO: Something went wrong
                    Log.w(TAG, "Broadcast Receiver: FindNearestNodesService responded: Something went wrong.");
                    break;
            }
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

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_nearest_nodes, container, false);
        nodeAdapter = new NodeAdapter(getActivity(), nodes);
        ListView lv_nearest_nodes = (ListView) view.findViewById(R.id.lv_nearest_nodes);
        lv_nearest_nodes.setAdapter(nodeAdapter);
        lv_nearest_nodes.setOnItemClickListener(this);

        tv_last_update = (TextView)view.findViewById(R.id.tv_last_update);
        tv_last_update.setText("Last updated: -");

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Start FindNearestNodesService
        //Intent intent = new Intent(getActivity(), FindNearestNodesService.class);
        //getActivity().startService(intent);
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
        // Start FindNearestNodesService
        Intent intent = new Intent(getActivity(), FindNearestNodesService.class);
        getActivity().startService(intent);

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
