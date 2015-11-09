package com.example.alex.sometrial;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import junit.framework.Assert;

import java.util.LinkedList;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    // TODO: figure out what this error code means.
    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";
    // TODO: figure out if should save this as in instance variable instead, and load and save it in onCreate and onSaveInstanceState
    // , see https://developers.google.com/android/guides/api-client#handle_connection_failure
    private static GoogleMap myMap;
    private final int ZOOM_LEVEL = 17;
    private boolean mBound;
    private LocationUpdater mService;
    private MyLinkedHashSet<Location> mDrawnLocationUpdates = new MyLinkedHashSet<Location>();
    private LocationUpdateReceiver mLocationUpdateReceiver;
    private boolean mapReadyForUpdates = false;
    private Location mostRecentLocation;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocationUpdater.LocalBinder binder = (LocationUpdater.LocalBinder) service;
            mService = binder.getService();
            String msg = mService.getHello();

            ((TextView)findViewById(R.id.myLocationText)).setText(msg);
            mBound = true;
            registerLocationUpdateReceiver();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            unregisterLocationUpdateReceiver();
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupSearchBox();
        myMap = null;
        mapReadyForUpdates = false;
        mDrawnLocationUpdates.clear();
        mostRecentLocation = null;
        setupMap();
        registerLocationUpdateReceiver();
    }

    private void setupSearchBox() {
        /*final EditText editText = (EditText) findViewById(R.id.map_search);
        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    getDirectionsDriver(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude())
                            , editText.getText().toString());
                    handled = true;
                    ((TextView) findViewById(R.id.destinationInfo)).setText("Searching for: " + editText.getText().toString());
                }
                return handled;
            }
        });*/
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = new Intent(this, LocationUpdater.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterLocationUpdateReceiver();
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
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupMap() {
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap map) {
        myMap = map;

        if(mDrawnLocationUpdates.size() > 0) {
            for(Location temp : mDrawnLocationUpdates.getLinkedList()) {
                addLocationToLine(temp);
            }
        }

        mapReadyForUpdates = true;
        /*getDirectionsDriver(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()),
                new LatLng(35.300063, -120.658606));*/
    }

   /* private void getDirectionsDriver(LatLng start, String buildingNumber) {
        String url = BASE_SERVER + "/directions?start_latitude=" + start.latitude
                + "&start_longitude=" + start.longitude
                + "&building_number=" + buildingNumber;
        getDirections(url);
    }*/

   /* private void updateLocationTracker() {
        String url = BASE_SERVER + "/location_update?latitude=" + mLastLocation.getLatitude()
                + "&longitude=" + mLastLocation.getLongitude()
                + "&millis_time_update=" + System.currentTimeMillis();
        if(locationTrackerId != null) {
            url += "&source_id=" + locationTrackerId;
        }
        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(url,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            locationTrackerId = Long.parseLong(response.get("source_id").toString());
                        }
                        catch(JSONException e) {
                            throw new RuntimeException("error parsing JSON");
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ((TextView)findViewById(R.id.myLocationText)).setText("an error occurred posting a location update");
                    }
                });
        queue.add(jsonObjectRequest);
    }*/

   /* private void getDirectionsDriver(LatLng start, LatLng dest) {
        String url = BASE_SERVER + "/directions?start_latitude=" + start.latitude
                + "&start_longitude=" + start.longitude
                + "&dest_latitude=" + dest.latitude
                + "&dest_longitude=" + dest.longitude;
        getDirections(url);
    }*/

  /*  private void getDirections(String url) {
        RequestQueue queue = Volley.newRequestQueue(this);

        // Request a string response from the provided URL.
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(url,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        // Display the first 500 characters of the response string.
                        for(int i = 0; i < response.length() - 1; i++) {
                            try {
                                JSONObject first = (JSONObject) response.get(i);
                                JSONObject second = (JSONObject) response.get(i + 1);
                                connectLatLng(new LatLng(new Double(first.get("lat").toString())
                                        , new Double(first.get("lng").toString()))
                                        , new LatLng(new Double(second.get("lat").toString())
                                        , new Double(second.get("lng").toString())));
                            }
                            catch (JSONException e) {
                                throw new RuntimeException("there was an error paring json: " + e.getMessage());
                            }
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                ((TextView)findViewById(R.id.myLocationText)).setText("An error occurred in connecting. message: " + error.getMessage());
            }
        });
// Add the request to the RequestQueue.
        queue.add(jsonArrayRequest);
    }*/

    private void addDestinationMarker(LatLng other) {
        myMap.addMarker(new MarkerOptions().position(other).title("destination"));
    }

    private void connectLatLng(LatLng start, LatLng end) {
        PolylineOptions polylineOptions = new PolylineOptions();
        polylineOptions.add(start);
        polylineOptions.add(end);
        myMap.addPolyline(polylineOptions);
    }

    private void addLocationToLine(Location newLocation) {
        if(newLocation == null) {
            throw new IllegalArgumentException("new location shouldn't be null");
        }
        if(mostRecentLocation == null) {
            addDestinationMarker(new LatLng(newLocation.getLatitude(), newLocation.getLongitude()));
        }
        else {
            Location prev = mostRecentLocation;
            mostRecentLocation = newLocation;

            connectLatLng(new LatLng(prev.getLatitude(), prev.getLongitude())
                    , new LatLng(newLocation.getLatitude(), newLocation.getLongitude()));
        }
    }

    public class LocationUpdateReceiver extends BroadcastReceiver {
        public static final String LOCATION_UPDATE = "com.example.alex.sometrial.LOCATION_UPDATE";

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction() == LOCATION_UPDATE) {
                if(mapReadyForUpdates) {
                    new UpdateLocationList().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, mService.getLocationUpdates());
                }
            }
        }
    }

    private void registerLocationUpdateReceiver() {
        IntentFilter intentFilter = new IntentFilter(LocationUpdateReceiver.LOCATION_UPDATE);
        intentFilter.addCategory(Intent.CATEGORY_DEFAULT);
        mLocationUpdateReceiver = new LocationUpdateReceiver();
        registerReceiver(mLocationUpdateReceiver, intentFilter);
    }

    private void unregisterLocationUpdateReceiver() {
        unregisterReceiver(mLocationUpdateReceiver);
    }

    public class UpdateLocationList extends AsyncTask<LinkedList<Location>, Void, LinkedList<Location>> {

        protected synchronized LinkedList<Location> doInBackground(LinkedList<Location>... locationUpdates) {
            Assert.assertEquals(1, locationUpdates.length);
            int startOfNewUpdates = 0;

            LinkedList<Location> list = locationUpdates[0];

            for(Location temp : list) {
                if(!mDrawnLocationUpdates.contains(temp))
                    break;
                startOfNewUpdates++;
            }

            LinkedList<Location> updates = new LinkedList<>(list.subList(startOfNewUpdates, list.size()));

            for(Location temp : updates) {
                mDrawnLocationUpdates.add(temp);
            }

            return updates;
        }

        protected void onPostExecute(LinkedList<Location> newLocations) {
            for(Location temp : newLocations) {
                addLocationToLine(temp);
            }
        }

    }
}
