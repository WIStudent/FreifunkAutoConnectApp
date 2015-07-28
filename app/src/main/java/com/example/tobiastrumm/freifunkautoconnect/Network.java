package com.example.tobiastrumm.freifunkautoconnect;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Tobias on 06.05.2015.
 */
public class Network implements Comparable<Network>, Parcelable{

    String ssid = "";
    boolean active = false;

    public Network(String ssid){
        this.ssid = ssid;
    }

    @Override
    public int compareTo(Network o) {
        // Cut of the quotation marks before compairing the SSIDs.
        String ssid1 = ssid.substring(1,ssid.length()-1);
        String ssid2 = o.ssid.substring(1, o.ssid.length()-1);
        return ssid1.compareToIgnoreCase(ssid2);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(ssid);
        boolean[] booleanArray = {active};
        parcel.writeBooleanArray(booleanArray);
    }

    public final static Parcelable.Creator<Network>CREATOR =
            new Parcelable.Creator<Network>(){

                @Override
                public Network createFromParcel(Parcel parcel) {
                    Network n = new Network(parcel.readString());
                    boolean[] booleanArray = new boolean[1];
                    parcel.readBooleanArray(booleanArray);
                    n.active = booleanArray[0];
                    return n;
                }

                @Override
                public Network[] newArray(int i) {
                    return new Network[0];
                }
            };
}
