package com.polypaths.collection.sometrial;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Alex on 11/8/2015.
 */
public class UpdateStarter extends Application {

    public static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        UpdateStarter.context = getApplicationContext();
        Intent intent = new Intent(this, LocationUpdater.class);
        startService(intent);
    }

    public static Context getContext() {
        return context;
    }
}
