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
import java.util.List;

/**
 * Created by Tobias on 06.05.2015.
 */
public class NetworkAdapter extends ArrayAdapter<Network> implements Filterable{

    private List<Network> allNetworks;
    private List<Network> shownNetworks;
    private CharSequence constraint;

    private Filter mNetworkFilter;

    private static class ViewHolder{
        TextView tv_ssid;
    }

    /**
     * allNetworks and shownNetworks must be two separate Lists, even if they contain the same elements.
     * @param context
     * @param allNetworks contains all Networks that should be included in an unfiltered ListView.
     * @param shownNetworks is used by the underlying ArrayAdapter and contains the Networks that are currently contained in the ListView.
     */
    public NetworkAdapter(Context context, List<Network> allNetworks, List<Network> shownNetworks){
        super(context, 0, shownNetworks);
        this.allNetworks = allNetworks;
        this.shownNetworks = shownNetworks;
    }

    /**
     * Notifies the NetworkAdapter that the allNetworks List was changed. The last filter constraint will be used with the updated
     * allNetwork List to update the shownNetworks.
     */
    public void notifyAllNetworksHasChangedReapplyFilter(){
        // Filter again with the last constraint.
        getFilter().filter(constraint);
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
    // http://codetheory.in/android-filters/
    // https://coderwall.com/p/zpwrsg/add-search-function-to-list-view-in-android
    // https://stackoverflow.com/questions/11840344/android-custom-arrayadapter-doesnt-refresh-after-filter
    // https://stackoverflow.com/questions/27903361/searchmenuitem-getactionview-returning-null
    public Filter getFilter(){
        if(mNetworkFilter == null){
            mNetworkFilter = new NetworkFilter();
        }
        return mNetworkFilter;
    }

    private class NetworkFilter extends Filter{

        @Override
        protected FilterResults performFiltering(CharSequence charSequence) {
            FilterResults results = new FilterResults();
            constraint = charSequence;

            if(charSequence == null || charSequence.length() == 0){
                results.values = allNetworks;
                results.count = allNetworks.size();
            }
            else{
                ArrayList<Network> filteredSSIDs = new ArrayList<>();

                charSequence = charSequence.toString().toLowerCase();
                for (Network n : allNetworks) {
                    String ssid = n.ssid;
                    if (ssid.toLowerCase().contains(charSequence)) {
                        filteredSSIDs.add(n);
                    }
                    results.values = filteredSSIDs;
                    results.count = filteredSSIDs.size();
                }

            }
            return results;
        }

        @SuppressWarnings("unchecked")
        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            shownNetworks.clear();
            shownNetworks.addAll((ArrayList<Network>) filterResults.values);
            notifyDataSetChanged();
        }
    }
}
