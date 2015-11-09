package com.example.alex.sometrial;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import junit.framework.Assert;

import java.util.LinkedList;

public class LocationUpdater extends Service
        implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener, Runnable {
    private boolean running;
    private boolean mResolvingError;
    private Location mLastLocation;
    private LinkedList<Location> mLocationUpdates = new LinkedList<Location>();
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    private static final String BASE_SERVER = "http://darkroast-1085.appspot.com";

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private GoogleApiClient mGoogleApiClient;

    public class LocalBinder extends Binder {
        public LocationUpdater getService() {
            return LocationUpdater.this;
        }
    }

    public String getHello() {
        return "hello there from loc updater";
    }

    public LinkedList<Location> getLocationUpdates() {
        return new LinkedList<>(mLocationUpdates);
    }

    @Override
    public IBinder onBind(Intent intent) {

        if(running) {
            return mBinder;
        }
        else {
            running = true;
            run();
            return mBinder;
        }
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
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                mGoogleApiClient);

        mLocationUpdates.add(mLastLocation);

        Assert.assertNotNull("location updates shouldn't be null here", mLastLocation);

        startLocationUpdates();
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onLocationChanged(Location location) {
        mLocationUpdates.add(location);

        Intent locationUpdateBroadcast = new Intent();
        locationUpdateBroadcast.setAction(MainActivity.LocationUpdateReceiver.LOCATION_UPDATE);
        locationUpdateBroadcast.addCategory(Intent.CATEGORY_DEFAULT);
        sendBroadcast(locationUpdateBroadcast);
    }
}
