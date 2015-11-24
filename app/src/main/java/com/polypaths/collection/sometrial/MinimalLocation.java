package com.polypaths.collection.sometrial;

import android.location.Location;

import junit.framework.Assert;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Created by Alex on 11/10/2015.
 */
public class MinimalLocation {
    final double lat;
    final double lng;
    final long millisUpdateTime;
    final String millisUpdateTimeString;
    private long nextTime = -1;

    private MinimalLocation(double lat, double lng, long millisUpdateTime) {
        this.lat = lat;
        this.lng = lng;
        this.millisUpdateTime = millisUpdateTime;
        this.millisUpdateTimeString = String.valueOf(millisUpdateTime);
    }

    private MinimalLocation(double lat, double lng, long millisUpdateTime, long nextTime) {
        this(lat, lng, millisUpdateTime);
        setNextTime(nextTime);
    }

    public static MinimalLocation newMinimalLocation(double lat, double lng) {
        return new MinimalLocation(lat, lng, System.currentTimeMillis());
    }

    public static MinimalLocation newMinimalLocation(double lat, double lng, long millisUpdateTime) {
        return new MinimalLocation(lat, lng, millisUpdateTime);
    }

    public static MinimalLocation newMinimalLocation(double lat, double lng, long millisUpdateTime, long nextTime) {
        MinimalLocation temp = new MinimalLocation(lat, lng, millisUpdateTime);
        temp.setNextTime(nextTime);
        return temp;
    }

    public static MinimalLocation newMinimalLocation(Location location) {
        return new MinimalLocation(location.getLatitude(), location.getLongitude(), System.currentTimeMillis());
    }

    public static MinimalLocation createFromJson(String jsonString) throws JSONException {
        Object obj = new JSONTokener(jsonString).nextValue();
        Assert.assertEquals(obj.getClass(), JSONObject.class);
        return createFromJson((JSONObject)obj);
    }

    public static MinimalLocation createFromJson(JSONObject jsonObject) throws JSONException {
        return newMinimalLocation(jsonObject.getDouble("lat"), jsonObject.getDouble("lng")
                , jsonObject.getLong("time"), jsonObject.getLong("nextTime"));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("lat", lat);
        object.put("lng", lng);
        object.put("time", millisUpdateTime);
        object.put("nextTime", nextTime);
        return object;
    }

    public String toJsonString() throws JSONException {
        return toJson().toString();
    }

    public void setNextTime(long nextTime) {
        Assert.assertEquals(this.nextTime, -1);
        this.nextTime = nextTime;
    }

    public long getNextTime() {
        return nextTime;
    }

    public String getNextTimeString() {
        return String.valueOf(nextTime);
    }

    public boolean hasNextTime() {
        return nextTime != -1;
    }

    public double getLatitude() {
        return lat;
    }

    public double getLongitude() {
        return lng;
    }
}
