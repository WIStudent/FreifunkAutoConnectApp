package com.example.tobiastrumm.freifunkautoconnect;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Tobias on 06.05.2015.
 */
public class NetworkAdapter extends ArrayAdapter<Network> {

    private static class ViewHolder{
        TextView tv_ssid;
    }

    public NetworkAdapter(Context context, ArrayList<Network> networks){
        super(context, 0, networks);
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
}
