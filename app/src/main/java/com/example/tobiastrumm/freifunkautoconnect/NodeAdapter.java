package com.example.tobiastrumm.freifunkautoconnect;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.List;

public class NodeAdapter extends ArrayAdapter<Node>{

    private static class ViewHolder{
        TextView tv_name;
        TextView tv_status;
        TextView tv_distance;
    }

    public NodeAdapter(Context context, List<Node> nodes){
        super(context, 0, nodes);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Node node = getItem(position);

        ViewHolder viewHolder;
        if(convertView == null){
            viewHolder = new ViewHolder();
            LayoutInflater inflater = LayoutInflater.from(getContext());
            convertView = inflater.inflate(R.layout.node_item, parent, false);
            viewHolder.tv_name = (TextView) convertView.findViewById(R.id.tv_node_item_name);
            viewHolder.tv_status = (TextView) convertView.findViewById(R.id.tv_node_item_status);
            viewHolder.tv_distance = (TextView) convertView.findViewById(R.id.tv_node_item_distance);
            convertView.setTag(viewHolder);
        }
        else{
            viewHolder = (ViewHolder) convertView.getTag();
        }
        //Populate the data into the template view
        viewHolder.tv_name.setText(node.name);

        if(node.online){
            viewHolder.tv_status.setText(getContext().getString(R.string.online));
            viewHolder.tv_status.setTextColor(getContext().getResources().getColor(R.color.abc_secondary_text_material_light));
        }
        else{
            viewHolder.tv_status.setText(getContext().getString(R.string.offline));
            viewHolder.tv_status.setTextColor(Color.RED);
        }

        if(node.distance < 1000){
            viewHolder.tv_distance.setText((int)node.distance +  " m");
        }
        else{
            double distance_km = node.distance / 1000.0d;
            String distance_string = new DecimalFormat("###.000").format(distance_km);
            viewHolder.tv_distance.setText(distance_string + " km");
        }
        return convertView;
    }
}
