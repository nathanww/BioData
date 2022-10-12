package com.neurelectrics.biodata;

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
                Intent i = new Intent(context, dataservice.class);
                context.startService(i);
                started=true;
            }

    }


}
