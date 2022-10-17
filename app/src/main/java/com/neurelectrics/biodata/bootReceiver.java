package com.neurelectrics.biodata;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class bootReceiver extends BroadcastReceiver {
    boolean started=false;
    @Override
    public void onReceive(Context context, Intent intent) {
        //start the monitoring service
            if (!started) {
                Intent i = new Intent(context, MainActivity.class);
                i.addFlags(FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
                started=true;
            }

    }


}
