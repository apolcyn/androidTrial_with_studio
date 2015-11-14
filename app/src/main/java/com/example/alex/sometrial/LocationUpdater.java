package com.example.alex.sometrial;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.LinkedList;
import java.util.List;


/*
Storing location updates in a linked list in shared preferences.
Head of linked list: if it exists, it point to the first JSON object in shared preferences
if the list is empty, then there is no mapping for the head in shared preferences.

Tail of linked list: if the list is non-empty, it point to the key of the last item in the list.
if the list is empty, then it has no mapping.

Each JSON object contains a field that is the key of the next time in shared preferences.
If they are at the end of the list, then their next key is "-1".
 */

public class LocationUpdater extends Service
        implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener, Runnable {
    private boolean running;
    private boolean mResolvingError;
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    public static final String LOCATION_UPDATES_TABLE = "location_update_preferences_table";
    public static final String LOGS_TAG = "my_logs";
    private static final String BASE_SERVER = "http://darkroast-1085.appspot.com/";


    // key for the first item in the list. points to nothing if list is empty.
    public static final String LOCATION_DATA_HEAD = "location_data_shared_pref_head";
    // key for the last item in the list. points to nothing if list is empty.
    private static String LOCATION_DATA_TAIL = "location_data_shared_pref_tail";

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private GoogleApiClient mGoogleApiClient;

    public class LocalBinder extends Binder {
        public LocationUpdater getService() {
            return LocationUpdater.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOGS_TAG, "hello there");
        if(!running) {
            running = true;
            new Thread(this).start();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

   /* @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(running)
            return;

        running = true;

        run();
    }*/

    public void run() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mGoogleApiClient.connect();
    }

    private void startLocationUpdates() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Assert.fail("failed to connect to google api services");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        // Connected to Google Play services!
        // The good stuff goes here.
        try {
            addToLocationUpdates(MinimalLocation.newMinimalLocation(LocationServices.FusedLocationApi.getLastLocation(
                    mGoogleApiClient)));
        }
        catch(JSONException e) {
            Log.e(LOGS_TAG, "error adding last logation to udpates list", e);
        }

        startLocationUpdates();
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
    }

    public void clearLocationUpdates() {
        getSharedPreferences(LOCATION_UPDATES_TABLE, MODE_PRIVATE).edit().clear().commit();
    }

    @Override
    public void onLocationChanged(Location location) {
        try {
            addToLocationUpdates(MinimalLocation.newMinimalLocation(location));
        }
        catch(JSONException e) {
            clearLocationUpdates();
            Log.e(LOGS_TAG, "json error in adding new location. just cleared updates list.", e);
        }
        Intent locationUpdateBroadcast = new Intent();
        locationUpdateBroadcast.setAction(MainActivity.LocationUpdateReceiver.LOCATION_UPDATE);
        locationUpdateBroadcast.addCategory(Intent.CATEGORY_DEFAULT);
        sendBroadcast(locationUpdateBroadcast);
    }

    public List<MinimalLocation> getUpdateHistoryFromLocation(String start) throws JSONException {
        SharedPreferences store = getSharedPreferences(LOCATION_UPDATES_TABLE, MODE_PRIVATE);

        // if start is the head of the list, and its empty, or start is pointing at the end of the list
        // , then return an empty list.
        if(start == String.valueOf(-1) || !store.contains(start) && start == LOCATION_DATA_HEAD) {
            return new LinkedList<MinimalLocation>();
        }
        // if start isn't the head, or the end, but isn't contained in the list, then something's wrong.
        else if(!store.contains(start) && start != LOCATION_DATA_HEAD)
            Log.e(LOGS_TAG, "start not contained in updates list");

        List<MinimalLocation> locationList = new LinkedList<MinimalLocation>();
        String curKey = start;

        // add all locations in the list
        while (curKey != String.valueOf(-1)) {
            if(!store.contains(curKey)) Log.e(LOGS_TAG, "store doesn't contain a key");
            String jsonString = store.getString(curKey, null);
            if(jsonString == null) Log.e(LOGS_TAG, "corrupt updates list. missing json object");
            MinimalLocation temp = MinimalLocation.createFromJson(jsonString);
            locationList.add(temp);
            curKey = temp.getNextTimeString();
        }

        return locationList;
    }

    public List<MinimalLocation> getFullUpdateHistory() throws JSONException {
        return getUpdateHistoryFromLocation(LOCATION_DATA_HEAD);
    }

    private void addToLocationUpdates(MinimalLocation location) throws JSONException {
        // add to head of list is list is empty
        SharedPreferences store = getSharedPreferences(LOCATION_UPDATES_TABLE, MODE_PRIVATE);

        // if list is empty, add it to head. point head to item, point tail to head key.
        if (!store.contains(LOCATION_DATA_HEAD)) {
            location.setNextTime(-1);
            SharedPreferences.Editor editor = store.edit();
            editor.putString(LOCATION_DATA_HEAD, location.toJsonString());
            editor.putString(LOCATION_DATA_TAIL, LOCATION_DATA_HEAD);
            editor.commit();
        }
        // get last item, set its next key to new item. ground new item's next key. set tail key to key of new item.
        else {
            String keyOfLastUpdate = store.getString(LOCATION_DATA_TAIL, null);
            if (!store.contains(keyOfLastUpdate)) Log.e(LOGS_TAG, "corrupted updates list. last update not found.");
            MinimalLocation last = MinimalLocation.createFromJson(store.getString(keyOfLastUpdate, null));
            if (last.getNextTime() != -1) Log.e(LOGS_TAG, "corrupted updates list. last node isn't grounded.");

            last.setNextTime(location.millisUpdateTime);
            location.setNextTime(-1);

            SharedPreferences.Editor editor = store.edit();
            editor.putString(keyOfLastUpdate, last.toJsonString());
            editor.putString(last.getNextTimeString(), location.toJsonString());
            editor.putString(LOCATION_DATA_TAIL, location.millisUpdateTimeString);
            editor.commit();
        }
    }

    private JSONArray getLocationUpdatesAsJsonArray() throws JSONException {
        JSONArray arr = new JSONArray();
        List<MinimalLocation> updates = getFullUpdateHistory();
        for(MinimalLocation temp : updates) {
            arr.put(temp.toJsonString());
        }
        return arr;
    }

    public void sendLocationUpdates() {
        String url = BASE_SERVER + "location_update_big";
        RequestQueue queue = Volley.newRequestQueue(this);
        JSONArray requestBody;

        try {
            requestBody = getLocationUpdatesAsJsonArray();
        }
        catch(JSONException e) {
            clearLocationUpdates();
            Log.e(LOGS_TAG, "error in creating array of json updates. just cleared data", e);
            return;
        }

        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.POST, url, requestBody,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        stopSelf();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("updateError", "server responded with a bad response: " + error.toString());
                stopSelf();
            }
        });
        queue.add(jsonArrayRequest);
    }
}
