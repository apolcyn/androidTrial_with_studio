package com.polypaths.collection.sometrial;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.LinkedList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    // TODO: figure out what this error code means.
    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";
    // TODO: figure out if should save this as in instance variable instead, and load and save it in onCreate and onSaveInstanceState
    // , see https://developers.google.com/android/guides/api-client#handle_connection_failure
    private static GoogleMap myMap;
    private MinimalLocation lastPrintedLocation = null;
    private final int ZOOM_LEVEL = 18;
    private boolean mBound;
    private LocationUpdater mService;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocationUpdater.LocalBinder binder = (LocationUpdater.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            new LocationDisplayer().execute();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
            mService = null;
            Intent intent = new Intent(MainActivity.this, LocationUpdater.class);
            stopService(intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        myMap = null;
        lastPrintedLocation = null;
        setupMap();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if(id == R.id.send_location_data) {
            if(mBound) {
                new LocationSender().execute();
            }
            return true;
        }
        else if(id == R.id.erase_location_data) {
            if(mBound) {
                new LocationDataEraser().execute();
            }
            return true;
        }
        else if(id == R.id.action_refresh_location_updates) {
            if(mBound) {
                new LocationDisplayer().execute();
            }
        }
        else if(id == R.id.launch_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        }
        else if(id == R.id.action_shutdown) {
            if(mBound) {
                unbindService(mConnection);
                mBound = false;
            }
            Intent intent = new Intent(this, LocationUpdater.class);
            stopService(intent);
        }
        else if(id == R.id.get_directions_between_places) {
            new DirectionsFinder().execute();
        }

        return super.onOptionsItemSelected(item);
    }

    private class LocationDataEraser extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if(!mBound) {
            }
            else {
                mService.clearLocationUpdates();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            cleanMap();
        }
    }

    private class LocationSender extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if(!mBound) {
            }
            else {
                mService.sendAndStartFresh();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void param) {
            cleanMap();
        }
    }

    private class DirectionsFinder extends AsyncTask<Void, Void, List<LatLng>> {
        @Override
        protected List<LatLng> doInBackground(Void... params) {
            LatLng start = new LatLng(35.303047, -120.663501);
            LatLng dest = new LatLng(35.301528, -120.659370);
            List<LatLng> directions = null;
            try {
                directions = new LocationUpdater().getDirectionsBetweenCoordinates(start, dest);
            }catch(Exception e) {
                throw new RuntimeException(e);
            }
            return directions;
        }

        @Override
        protected void onPostExecute(List<LatLng> updates) {
            displayDirections(updates);
        }
    }

    private class LocationDisplayer extends AsyncTask<Void, Void, List<MinimalLocation>> {
        @Override
        protected List<MinimalLocation> doInBackground(Void... params) {
            List<MinimalLocation> output;

            if(!mBound) {
                return new LinkedList<>();
            }
            else {
                return mService.getFullUpdateHistory();
            }
        }

        @Override
        protected void onPostExecute(List<MinimalLocation> updates) {
            cleanMap();
            for(MinimalLocation temp: updates) {
                addLocationToLine(temp);
            }
        }
    }

    private void setupMap() {
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    public void displayDirections(List<LatLng> directions) {
        if(directions == null) {
            new AlertDialog.Builder(getApplicationContext())
                    .setTitle("Path not found")
                    .show();
        }
        else {
            if(directions.size() < 2) {
                new AlertDialog.Builder(getApplicationContext())
                        .setTitle("Path is too short. it has length: " + directions.size())
                        .show();
            }
            LatLng prev = directions.get(0);
            for(int i = 1; i < directions.size(); i++) {
                LatLng cur = directions.get(i);
                connectLatLng(prev, cur);
                prev = cur;
            }
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                    new LatLng(prev.latitude, prev.longitude), ZOOM_LEVEL);
            myMap.moveCamera(cameraUpdate);
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        myMap = map;
        Intent intent = new Intent(this, LocationUpdater.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private void addDestinationMarker(LatLng other) {
        myMap.addMarker(new MarkerOptions().position(other).title("start"));
    }

    private void connectLatLng(LatLng start, LatLng end) {
        PolylineOptions polylineOptions = new PolylineOptions();
        polylineOptions.add(start);
        polylineOptions.add(end);
        myMap.addPolyline(polylineOptions);
    }

    private void cleanMap() {
        if(myMap != null) {
            lastPrintedLocation = null;
            myMap.clear();
        }
    }

    private void addLocationToLine(MinimalLocation newLocation) {
        if(newLocation == null) {
            throw new IllegalArgumentException("new location shouldn't be null");
        }
        if(lastPrintedLocation == null) {
            addDestinationMarker(new LatLng(newLocation.getLatitude(), newLocation.getLongitude()));
            lastPrintedLocation = newLocation;
        }
        else {
            connectLatLng(new LatLng(lastPrintedLocation.getLatitude(), lastPrintedLocation.getLongitude())
                    , new LatLng(newLocation.getLatitude(), newLocation.getLongitude()));
            lastPrintedLocation = newLocation;
        }
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                new LatLng(lastPrintedLocation.getLatitude(), lastPrintedLocation.getLongitude()), ZOOM_LEVEL);
        myMap.moveCamera(cameraUpdate);
    }
}
