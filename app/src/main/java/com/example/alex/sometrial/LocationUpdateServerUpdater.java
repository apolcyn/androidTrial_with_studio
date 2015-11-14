package com.example.alex.sometrial;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;

import java.util.Map;

public class LocationUpdateServerUpdater extends Service {
    private static final String BASE_SERVER = "http://darkroast-1085.appspot.com/";

    public LocationUpdateServerUpdater() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public int onStartCommand(Intent intent, int flag, int startId) {
        Map<String, String> locationUpdateMap
                = (Map<String, String>)getSharedPreferences(LocationUpdater.LOCATION_UPDATES_TABLE, MODE_PRIVATE).getAll();
        String curKey = LocationUpdater.LOCATION_DATA_HEAD;

        while(curKey != String.valueOf(-1)) {
            String jsonString = locationUpdateMap.
        }
        JSONArray updateList = new JSONArray();
        if(locationUpdateMap.size() > 0) {
            for(String jsonUpdate : locationUpdateMap.values()) {
                updateList.put(jsonUpdate);
            }
        }
        updateLocationBig();

        return START_STICKY_COMPATIBILITY;
    }

    private void clearLocationUpdates() {
        SharedPreferences.Editor editor = getSharedPreferences(LocationUpdater.LOCATION_UPDATES_TABLE, MODE_PRIVATE).edit();
        editor.clear();
        editor.commit();
    }

    private void updateLocationBig(JSONArray requestBody) {
        String url = BASE_SERVER + "location_update_big";

        RequestQueue queue = Volley.newRequestQueue(this);

        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(Request.Method.POST, url, requestBody,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        clearLocationUpdates();
                        stopSelf();
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                clearLocationUpdates();
                Log.e("updateError", "server responded with a bad response: " + error.toString());
                stopSelf();
            }
        });
        queue.add(jsonArrayRequest);
    }
}
