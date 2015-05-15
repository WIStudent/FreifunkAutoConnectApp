package com.example.tobiastrumm.freifunkautoconnect;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Tobias on 06.05.2015.
 */
public class NetworkAdapter extends ArrayAdapter<Network> implements Filterable{

    private ArrayList<Network> networks;
    private ArrayList<Network> filteredNetworks;

    private static class ViewHolder{
        TextView tv_ssid;
    }

    public NetworkAdapter(Context context, ArrayList<Network> networks){
        super(context, 0, networks);
        this.networks = new ArrayList<Network>(networks);
        this.filteredNetworks = new ArrayList<>(networks);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Network network = getItem(position);

        ViewHolder viewHolder;
        if(convertView == null){
            viewHolder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(R.layout.network_item, parent, false);
            viewHolder.tv_ssid = (TextView) convertView.findViewById(R.id.tv_network_item_ssid);
            convertView.setTag(viewHolder);
        }
        else{
            viewHolder = (ViewHolder) convertView.getTag();
        }
        //Populate the data into the template view
        viewHolder.tv_ssid.setText(network.ssid);
        if(network.active){
            convertView.setBackgroundColor(Color.parseColor("#8bc34a"));
        }
        else{
            convertView.setBackgroundResource(R.drawable.abc_item_background_holo_light);
        }
        return convertView;
    }


    // For SearchView and Filter, see:
    // https://coderwall.com/p/zpwrsg/add-search-function-to-list-view-in-android
    // https://stackoverflow.com/questions/11840344/android-custom-arrayadapter-doesnt-refresh-after-filter
    // https://stackoverflow.com/questions/27903361/searchmenuitem-getactionview-returning-null
    @Override
    public Filter getFilter() {
        Filter filter = new Filter() {

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {

                filteredNetworks = (ArrayList<Network>)results.values;
                notifyDataSetChanged();
                clear();
                int count = filteredNetworks.size();
                for(int i = 0; i<count; i++){
                    add(filteredNetworks.get(i));
                    notifyDataSetInvalidated();
                }
            }

            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                ArrayList<Network> filteredSSIDs = new ArrayList<Network>();

                constraint = constraint.toString().toLowerCase();
                for (Network n : networks) {
                    String ssid = n.ssid;
                    if (ssid.toLowerCase().contains(constraint)) {
                        filteredSSIDs.add(n);
                    }
                    results.values = filteredSSIDs;
                    results.count = filteredSSIDs.size();
                }
                return results;
            }
        };
        return filter;
    }
}
