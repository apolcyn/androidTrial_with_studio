package com.example.alex.sometrial;

import android.app.Application;
import android.content.Intent;

/**
 * Created by Alex on 11/8/2015.
 */
public class UpdateStarter extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Intent intent = new Intent(this, LocationUpdater.class);
        startService(intent);
    }
}
