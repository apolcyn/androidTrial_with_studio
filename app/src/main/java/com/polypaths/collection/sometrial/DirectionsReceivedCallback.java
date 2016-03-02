package com.polypaths.collection.sometrial;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

/**
 * Created by Alex on 3/2/2016.
 */
public interface DirectionsReceivedCallback {
    void directionsReceived(List<LatLng> directions);
}
