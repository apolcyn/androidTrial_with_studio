package com.example.alex.sometrial;

import android.app.Service;
import android.content.Intent;
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
        implements ConnectionCallbacks, OnConnectionFailedListener, LocationListener, Runnable {
    private boolean running;
    private boolean mResolvingError;
    private static final int REQUEST_RESOLVE_ERROR = 1001;
    public static final String LOCATION_UPDATES_TABLE = "location_update_preferences_table";
    public static final String LOGS_TAG = "my_logs";
    private static final String BASE_SERVER = "http://darkroast-1085.appspot.com/";
    private static final String LOCATION_UPDATES_ENDPOINT = BASE_SERVER + "location_update_big";
    private FileOutputStream updatesWriter;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private GoogleApiClient mGoogleApiClient;

    public class LocalBinder extends Binder {
        public LocationUpdater getService() {
            return LocationUpdater.this;
        }
    }

    private void ensureUpdateClientsReady() {
        try {
            if (updatesWriter == null)
                updatesWriter = openFileOutput(LOCATION_UPDATES_TABLE, MODE_PRIVATE);
        } catch (IOException e) {
            Log.e(LOGS_TAG, "couldn't open file writer to write to location updates file", e);
        }
    }

    private void handleCorruptFile() {
        clearLocationUpdates();
        ensureUpdateClientsReady();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(LOGS_TAG, "hello there");
        ensureUpdateClientsReady();

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
        getFileStreamPath(LOCATION_UPDATES_TABLE).delete();
        try {
            if(updatesWriter != null) {
                updatesWriter.close();
            }
        }
        catch(IOException e) {
            Log.e(LOGS_TAG, "error closing updates writer", e);
            updatesWriter = null;
        }
        try {
            updatesWriter = openFileOutput(LOCATION_UPDATES_TABLE, MODE_PRIVATE);
        }
        catch(FileNotFoundException e) {
            Log.e(LOGS_TAG, "couldn't open updates file", e);
        }
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
    }

    public List<MinimalLocation> getFullUpdateHistory() {
        List<MinimalLocation> output = new LinkedList<MinimalLocation>();
        Scanner sc;

        try {
            BufferedInputStream bufferedInputStream = new BufferedInputStream(openFileInput(LOCATION_UPDATES_TABLE));
            sc = new Scanner(bufferedInputStream);
        }
        catch(IOException e) {
            Log.e(LOGS_TAG, "couldn't read upates because couldn't get a reader");
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
                Log.e(LOGS_TAG, "input mismatch reading updates", e);
                handleCorruptFile();
                return output;
            }
            catch(NoSuchElementException e) {
                Log.e(LOGS_TAG, "item not found when reading updates", e);
                handleCorruptFile();
                return output;
            }

            output.add(MinimalLocation.newMinimalLocation(lat, lng, time));
        }
        sc.close();

        return output;
    }

    private void addToLocationUpdates(MinimalLocation location) throws JSONException {
        if(updatesWriter == null) {
            Log.e(LOGS_TAG, "couldn't add a location update because couldn't get a writer");
            return;
        }
        try {
            StringBuilder builder = new StringBuilder();
            builder.append(location.getLatitude() + " ");
            builder.append(location.getLongitude() + " ");
            builder.append(location.millisUpdateTimeString + "\r\n");
            updatesWriter.write(builder.toString().getBytes());
            updatesWriter.flush();
        }
        catch(IOException e) {
            Log.e(LOGS_TAG, "failed to write to updates file", e);
        }
    }

    public void sendLocationUpdates() {
        URL url;
        try {
            url = new URL(LOCATION_UPDATES_ENDPOINT);
        }
        catch(MalformedURLException e) {
            Log.e(LOGS_TAG, "couldn't upload file because endpoint url is malformed", e);
            return;

        }
        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        }
        catch (IOException e) {
            Log.e(LOGS_TAG, "coulnd't update locations because couldn't open connection to server", e);
            return;
        }

        long fileLengthLong = getFileStreamPath(LOCATION_UPDATES_TABLE).length();
        if(fileLengthLong > Integer.MAX_VALUE) {
            Log.e(LOGS_TAG, "file is too big. only going to be able to upload first " + Integer.MAX_VALUE + " bytes");
        }
        int fileLength = (int)fileLengthLong;

        try{
            urlConnection.setDoOutput(true);
            urlConnection.setFixedLengthStreamingMode(fileLength);
            OutputStream fileUploadStream = new BufferedOutputStream(urlConnection.getOutputStream());
            InputStream fileReadingStream = new BufferedInputStream(openFileInput(LOCATION_UPDATES_TABLE));

            int cur, numWritten = 0; // not sure if possible, but if file grows while reading, don't include the new stuff.
            while((cur = fileReadingStream.read()) != -1 && numWritten++ < fileLength) {
                fileUploadStream.write((char)cur);
            }
            fileReadingStream.close();
            fileUploadStream.close();
        }
        catch(IOException e) {
            Log.e(LOGS_TAG, "error occured in uploading location updates", e);
        }
        finally {
            urlConnection.disconnect();
        }
    }
}
