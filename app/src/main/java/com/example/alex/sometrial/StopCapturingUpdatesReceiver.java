package com.example.alex.sometrial;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StopCapturingUpdatesReceiver extends BroadcastReceiver {
    public StopCapturingUpdatesReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        context.startService(new Intent(context, LocationUpdateServerUpdater.class));
    }
}
