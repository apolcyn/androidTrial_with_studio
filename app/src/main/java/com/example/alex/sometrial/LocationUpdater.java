package com.example.alex.sometrial;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import junit.framework.Assert;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.LinkedList;
import java.util.Map;
import java.util.TreeSet;

public class LocationUpdater extends Service
        implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener, Runnable {
    private boolean running;
    private boolean mResolvingError;
    private static Location sLastLocation;
    private static LinkedList<Location> sLocationUpdates = new LinkedList<Location>();
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    public static final String LOCATION_UPDATES_TABLE = "location_update_preferences_table";

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private GoogleApiClient mGoogleApiClient;

    public class LocalBinder extends Binder {
        public LocationUpdater getService() {
            return LocationUpdater.this;
        }
    }

    public String getHello() {
        SharedPreferences preferences = getSharedPreferences("com.example.alex.stuff", Context.MODE_PRIVATE);
        SharedPreferences.Editor writer = preferences.edit();
        int temp = preferences.getInt("counter", 0);
        writer.putInt("counter", ++temp);
        writer.commit();

        return "hello from updater for again. value from shared prefs was " + temp;
    }

    public LinkedList<Location> getLocationUpdates() {
        return new LinkedList<>(sLocationUpdates);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(!running) {
            running = true;
            getFullUpdateHistory();
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

    private void updateSharedPreferences(Location location) {
        JSONObject locationUpdate = new JSONObject();
        String currentTimeMillis = String.valueOf(System.currentTimeMillis());
        sLastLocation = location;
        sLocationUpdates.add(location);
        try {
            locationUpdate.put("update_time", currentTimeMillis);
            locationUpdate.put("latitude", location.getLatitude());
            locationUpdate.put("longitude", location.getLongitude());
        }
        catch(JSONException e) {
            Log.e("locationUpdating", "caught a json exception while adding a location update", e);
        }
        SharedPreferences.Editor editor = getSharedPreferences(LOCATION_UPDATES_TABLE, MODE_PRIVATE).edit();
        editor.putString(currentTimeMillis, locationUpdate.toString());
        editor.commit();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        // Connected to Google Play services!
        // The good stuff goes here.
        sLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);

        updateSharedPreferences(sLastLocation);

        Assert.assertNotNull("location updates shouldn't be null here", sLastLocation);
        startLocationUpdates();
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onLocationChanged(Location location) {
        updateSharedPreferences(location);
        sLastLocation = location;

        Intent locationUpdateBroadcast = new Intent();
        locationUpdateBroadcast.setAction(MainActivity.LocationUpdateReceiver.LOCATION_UPDATE);
        locationUpdateBroadcast.addCategory(Intent.CATEGORY_DEFAULT);
        sendBroadcast(locationUpdateBroadcast);
    }

    public class LocationUpdatesErasedReceiver extends BroadcastReceiver {
        public static final String LOCATION_UPDATES_ERASED = "com.example.alex.sometrial.LOCATION_UPDATES_ERASED";

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction() == LOCATION_UPDATES_ERASED) {
                SharedPreferences.Editor editor = getSharedPreferences(LOCATION_UPDATES_TABLE, MODE_PRIVATE).edit();
                editor.clear();
                editor.commit();
                sLocationUpdates.clear();
            }
        }
    }

    private void getFullUpdateHistory() {
        sLocationUpdates.clear();
        Map<String, String> updateSet
                = (Map<String, String>)getSharedPreferences(LOCATION_UPDATES_TABLE, MODE_PRIVATE).getAll();
        TreeSet<String> updateTimes = new TreeSet(updateSet.keySet());
        try {
            for (String singleUpdateTime : updateTimes) {
                JSONObject update = (JSONObject) new JSONTokener(updateSet.get(singleUpdateTime)).nextValue();
                double lat = Double.parseDouble(updateSet.get("latitude"));
                double lng = Double.parseDouble(updateSet.get("longitude"));
                Location temp = new Location();
                temp.setLatitude(lat);
                temp.setLongitude(lng);
                sLocationUpdates.add(temp);
            }
        }
        catch (JSONException e) {
            Log.e("JSONerror", "error in parsing shared preferences json: " + e.toString());
        }
        catch(NumberFormatException e) {
            Log.e("doubleParsing", "error in parsing shared preferences double: " + e.toString());
        }
    }
}
