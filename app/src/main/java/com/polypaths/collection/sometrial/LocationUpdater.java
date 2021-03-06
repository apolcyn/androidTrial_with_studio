package com.polypaths.collection.sometrial;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;


/*
Storing location updates in a linked list in shared preferences.
Head of linked list: if it exists, it point to the first JSON object in shared preferences
if the list is empty, then there is no mapping for the head in shared preferences.

Tail of linked list: if the list is non-empty, it point to the key of the last item in the list.
if the list is empty, then it has no mapping.

Each JSON object contains a field that is the key of the next time in shared preferences.
If they are at the end of the list, then their next key is "-1".
 */

/* Location updates structured in: (space delimited attributes, newline delimited objects)
latitude longitude update-time
*/
public class LocationUpdater extends Service
        implements ConnectionCallbacks
        , OnConnectionFailedListener
        , LocationListener
        , Runnable {
    private boolean running;
    private boolean mResolvingError;
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    public static final String LOCATION_UPDATES_TABLE = "location_update_preferences_table";
    private static final String BASE_SERVER = "http://pubsub-walkthrough-dot-darkroast-1085.appspot.com/";
    private static final String LOCATION_UPDATES_ENDPOINT = BASE_SERVER + "location_update_big";
    private static final String GET_DIRECTIONS_ENPOINT = BASE_SERVER + "books/get_directions";
    private FileOutputStream updatesWriter;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private GoogleApiClient mGoogleApiClient;
    private int mMaxUpdatesFileLength;
    public static List<MinimalLocation> campusCoords = new ArrayList<MinimalLocation>();
    private GoogleApiConnectionStatus mGoogleApiConnectionStatus = GoogleApiConnectionStatus.NOT_CONNECTED_YET;

    /* Used to keep track of the boundaries of Cal Poly campus. */
    static {
        campusCoords.add(MinimalLocation.newMinimalLocation(35.297184, -120.665141, 0));
        campusCoords.add(MinimalLocation.newMinimalLocation(35.302638, -120.666626, 0));
        campusCoords.add(MinimalLocation.newMinimalLocation(35.305020, -120.662978, 0));
        campusCoords.add(MinimalLocation.newMinimalLocation(35.303759, -120.658558, 0));
        campusCoords.add(MinimalLocation.newMinimalLocation(35.297780, -120.654906, 0));
    }

    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceChangeListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if(key.equals(getResources().getString(R.string.max_update_file_length_pref_key))) {
                clearLocationUpdates();
                mMaxUpdatesFileLength
                        = Integer.valueOf(sharedPreferences.getString(getResources().getString(R.string.max_update_file_length_pref_key), "0"));
            }
        }
    };

    public enum GoogleApiConnectionStatus {
        NOT_CONNECTED_YET, CONNECTED, ERROR_WITH_CONNECTION;
    }

    public class LocalBinder extends Binder {
        public LocationUpdater getService() {
            return LocationUpdater.this;
        }
    }

    public GoogleApiConnectionStatus getGoogleApiConnectionStatus() {
        return mGoogleApiConnectionStatus;
    }

    private void ensureUpdateClientsReady() {
        try {
            if (updatesWriter == null)
                updatesWriter = openFileOutput(LOCATION_UPDATES_TABLE, MODE_PRIVATE);
        } catch (IOException e) {
        }
    }

    private void handleCorruptFile() {
        clearLocationUpdates();
        ensureUpdateClientsReady();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureUpdateClientsReady();
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
        mMaxUpdatesFileLength = Integer.valueOf(PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getString(getResources().getString(R.string.max_update_file_length_pref_key), "0"));

        if(!running) {
            running = true;
            new Thread(this).start();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        ensureUpdateClientsReady();
        return mBinder;
    }

    /* Start polling for location updates" */
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
        mGoogleApiConnectionStatus = GoogleApiConnectionStatus.ERROR_WITH_CONNECTION;
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
        mGoogleApiConnectionStatus = GoogleApiConnectionStatus.ERROR_WITH_CONNECTION;
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        // Connected to Google Play services!
        // The good stuff goes here.
        mGoogleApiConnectionStatus = GoogleApiConnectionStatus.CONNECTED;

        try {
            Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if(lastLocation != null && insidePolyCampus(MinimalLocation.newMinimalLocation(lastLocation))) {
                addToLocationUpdates(MinimalLocation.newMinimalLocation(lastLocation));
            }
        }
        catch(JSONException e) {
        }

        startLocationUpdates();
    }

    @Override
    public void onDestroy() {
        mGoogleApiClient.disconnect();
        mGoogleApiConnectionStatus = GoogleApiConnectionStatus.NOT_CONNECTED_YET;
        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
    }

    public void clearLocationUpdates() {
        getFileStreamPath(LOCATION_UPDATES_TABLE).delete();
        try {
            if(updatesWriter != null) {
                updatesWriter.close();
            }
        }
        catch(IOException e) {
            updatesWriter = null;
        }
        try {
            updatesWriter = openFileOutput(LOCATION_UPDATES_TABLE, MODE_PRIVATE);
        }
        catch(FileNotFoundException e) {
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null || !insidePolyCampus(MinimalLocation.newMinimalLocation(location))) {
            return;
        }

        try {
            addToLocationUpdates(MinimalLocation.newMinimalLocation(location));
        } catch (JSONException e) {
            clearLocationUpdates();
        }
    }

    public List<MinimalLocation> getFullUpdateHistory() {
        List<MinimalLocation> output = new LinkedList<MinimalLocation>();
        Scanner sc;

        try {
            BufferedInputStream bufferedInputStream = new BufferedInputStream(openFileInput(LOCATION_UPDATES_TABLE));
            sc = new Scanner(bufferedInputStream);
        }
        catch(IOException e) {
            return output;
        }

        while(sc.hasNext()) {
            double lat, lng;
            long time;
            try {
                lat = sc.nextDouble();
                lng = sc.nextDouble();
                time = sc.nextLong();
            }
            catch(InputMismatchException e) {
                sc.close();
                handleCorruptFile();
                return output;
            }
            catch(NoSuchElementException e) {
                sc.close();
                handleCorruptFile();
                return output;
            }

            output.add(MinimalLocation.newMinimalLocation(lat, lng, time));
        }
        sc.close();

        return output;
    }

    private void addToLocationUpdates(MinimalLocation location) throws JSONException {
        if(location == null || updatesWriter == null) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(location.getLatitude() + " ");
        builder.append(location.getLongitude() + " ");
        builder.append(location.millisUpdateTimeString + "\r\n");
        byte[] newUpdate = builder.toString().getBytes();

        if(getFileStreamPath(LOCATION_UPDATES_TABLE).length() + newUpdate.length <= mMaxUpdatesFileLength) {
            try {
                updatesWriter.write(newUpdate);
                updatesWriter.flush();
            } catch (IOException e) {
            }
        }
    }

    public void sendAndStartFresh() {
        sendLocationUpdates();
        clearLocationUpdates();
    }

    public List<LatLng> getDirectionsBetweenCoordinates(LatLng start, LatLng dest) throws Exception {
        URL url;
        String queryString = "?start_lat=" + start.latitude
                + "&start_lng=" + start.longitude
                + "&end_lat=" + dest.latitude
                + "&end_lng=" + dest.longitude;
        try {
            url = new URL(GET_DIRECTIONS_ENPOINT + queryString);
        } catch (MalformedURLException e) {
            return null;
        }

        HttpURLConnection urlConnection;
        urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setInstanceFollowRedirects(true);
        List<LatLng> directions = null;
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            directions = parseDirectionsResponse(in);
        }
        catch(IOException e) {
            new AlertDialog.Builder(getApplicationContext())
                    .setTitle("request for directions failed")
                    .setMessage("error message: " + urlConnection.getErrorStream().toString());
        }
        catch(Exception e) {
            new AlertDialog.Builder(getApplicationContext())
                    .setTitle("request for directions failed")
                    .setMessage("error message: " +e.getMessage());
        }
        finally {
            urlConnection.disconnect();
        }
        return directions;
    }

    /* Converts a server response for a directions query to directions.
     * @return A list of coordinates to follow in order, or null, of no path was found.
     */
    public List<LatLng> parseDirectionsResponse(InputStream jsonLatLngsIn) throws Exception {
        List<LatLng> response = new ArrayList<LatLng>();

        java.util.Scanner s = new java.util.Scanner(jsonLatLngsIn).useDelimiter("\\A");
        String jsonLatLngString = s.hasNext() ? s.next() : "";

        JSONObject jsonObject = new JSONObject(jsonLatLngString);
        boolean pathFound = jsonObject.getBoolean("path_found");
        if(!pathFound) {
            return null;
        }
        else {
            JSONArray pointList = jsonObject.getJSONArray("shortest_path");

            for(int i = 0; i < pointList.length(); i++) {
                JSONObject pointStruct = (JSONObject)pointList.get(i);
                response.add(new LatLng(pointStruct.getDouble("lat"), pointStruct.getDouble("lng")));
            }
            return response;
        }
    }

    public void sendLocationUpdates() {
        URL url;
        try {
            url = new URL(LOCATION_UPDATES_ENDPOINT);
        } catch (MalformedURLException e) {
            return;
        }

        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            return;
        }

        long fileLengthLong = getFileStreamPath(LOCATION_UPDATES_TABLE).length();

        int fileLength = (int) fileLengthLong;

        try {
            urlConnection.setDoOutput(true);
            urlConnection.setFixedLengthStreamingMode(fileLength);
            OutputStream fileUploadStream = new BufferedOutputStream(urlConnection.getOutputStream());
            InputStream fileReadingStream = new BufferedInputStream(openFileInput(LOCATION_UPDATES_TABLE));

            int cur, numWritten = 0; // not sure if possible, but if file grows while reading, don't include the new stuff.
            while ((cur = fileReadingStream.read()) != -1 && numWritten++ < fileLength) {
                fileUploadStream.write((char) cur);
            }
            fileReadingStream.close();
            fileUploadStream.close();
        } catch (IOException e) {
        } finally {
            urlConnection.disconnect();
        }
    }

    // checks if a new location is within the bounds of cal poly's campus.
    private boolean insidePolyCampus(MinimalLocation location) {
        for(int start = 0; start < campusCoords.size(); start++) {
            if(zCompCrossProd(campusCoords.get(start), campusCoords.get((start + 1) % campusCoords.size()), location) < 0) {
                return false;
            }
        }

        return true;
    }

    private double zCompCrossProd(MinimalLocation start, MinimalLocation end, MinimalLocation other) {
        double deltaX_1 = end.getLatitude() - start.getLatitude();
        double deltaX_2 = other.getLatitude() - start.getLatitude();
        double deltaY_1 = end.getLongitude() - start.getLongitude();
        double deltaY_2 = other.getLongitude() - start.getLongitude();

        return deltaX_1 * deltaY_2 - deltaY_1 * deltaX_2;
    }
}
