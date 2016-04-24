package com.example.tobiastrumm.freifunkautoconnect;

import android.content.Context;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class NetworkRecyclerAdapter extends RecyclerView.Adapter<NetworkRecyclerAdapter.ViewHolder> {

    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tv_ssid;

        public ViewHolder(final View itemView) {
            super(itemView);
            tv_ssid = (TextView) itemView.findViewById(R.id.tv_network_item_ssid);

            // Setup the click listener
            itemView.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    // Triggers click upwards to the adapter on click
                    if(listener != null){
                        listener.onItemClick(itemView, getLayoutPosition(), getAdapterPosition());
                    }
                }
            });
        }
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
            filteredNetworks.clear();
            filteredNetworks.addAll((ArrayList<Network>) filterResults.values);
            notifyDataSetChanged();
        }
    }

    // Define the listener interface
    public interface OnItemClickListener{
        void onItemClick(View itemView, int layoutPosition, int adapterPosition);
    }

    // List that holds all network elements
    private List<Network> allNetworks;
    // List that holds all network elements that should be shown in the RecycleView
    private List<Network> filteredNetworks;

    // Filter
    private CharSequence constraint;
    private Filter mNetworkFilter;

    // Listener member variable
    private static OnItemClickListener listener;

    // Define the method that allows the parent fragment to define the listener
    public void setOnItemClickListener(OnItemClickListener listener){
        this.listener = listener;
    }

    public NetworkRecyclerAdapter(List<Network> networks) {
        allNetworks = networks;
        filteredNetworks = new ArrayList<>(networks);
    }

    @Override
    public NetworkRecyclerAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // inflate the layout
        View nodeView = inflater.inflate(R.layout.network_item, parent, false);

        // return a new holder instance
        ViewHolder viewHolder = new ViewHolder(nodeView);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(NetworkRecyclerAdapter.ViewHolder holder, int position) {
        // Get the network based on the position
        Network network = filteredNetworks.get(position);

        //Populate the data into the template view
        holder.tv_ssid.setText(network.ssid);
        if(network.active){
            holder.itemView.setBackgroundColor(Color.parseColor("#8bc34a"));
        }
        else{
            holder.itemView.setBackgroundResource(R.drawable.abc_item_background_holo_light);
        }
    }

    @Override
    public int getItemCount() {
        return filteredNetworks.size();
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

    /**
     *  Clean all elements of the recycler
     */
    public void clear() {
        allNetworks.clear();
        filteredNetworks.clear();
        notifyDataSetChanged();
    }

    /**
     * Add a list of items to the recycler
     * @param list
     */
    public void addAll(List<Network> list){
        allNetworks.addAll(list);
        // Filter again with the last constraint.
        getFilter().filter(constraint);
        notifyDataSetChanged();
    }

    /**
     * Returns pointer to the Network object at the passed position in the NodeRecyclerAdapter
     * @param adapterPosition Position in the NodeRecyclerAdapter
     * @return Pointer to the Node object in the NodeRecyclerAdapter
     */
    public Network getNetwork(int adapterPosition) {
        return filteredNetworks.get(adapterPosition);
    }

    /**
     * Returns pointer to the list of the shown networks in the RecyclerView
     * @return Pointer to the list of the shown networks
     */
    public List<Network> getShownNetworks() {
        return filteredNetworks;
    }
}
