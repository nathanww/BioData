package com.neurelectrics.biodata;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.StrictMode;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.jakewharton.processphoenix.ProcessPhoenix;
import com.neurelectrics.biodata.databinding.ActivityMainBinding;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

public class MainActivity extends Activity implements SensorEventListener {

    private TextView mTextView;
    private ActivityMainBinding binding;
    private SensorManager sm;
    private float accX=-1;
    private float accY=-1;
    private float accZ=-1;
    private float gX=-1;
    private float gY=-1;
    private float gZ=-1;
    private float mX=-1;
    private float mY=-1;
    private float mZ=-1;

    private float ambientTemp=-1;
    private float pressure=-1;
    private float lightlevel=-1;
    private float heartRate=-1;
    private int ACC_SAMPLE_RATE=1000;  //default is to sample the accelerometer every second
    private int GLOBAL_UPDATE_RATE=1000;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //initialize sensor manager
        //sm = ((SensorManager)getSystemService(SENSOR_SERVICE));
        //allow internet accsess on UI thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        //PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        //PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
        //        "BioData:dataAcquistion");
        //wakeLock.acquire();
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        //check to see if we have a device ID, if not then make one
        if (sharedPref.getInt("pid",-1) == -1) {
            Random rand = new Random();
            editor.putInt("pid",rand.nextInt(2147483647));
            editor.commit();
        }
        int pid=sharedPref.getInt("pid",-1);


        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        long[] mVibratePattern = new long[]{50,10,100,30,150,50,200,20,200,100};

        // -1 : Do not repeat this pattern
        // pass 0 if you want to repeat this pattern from 0th index

        ToggleButton toggleAlarm = (ToggleButton) findViewById(R.id.toggleButton);
        toggleAlarm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked)
                {
                    vibrator.vibrate(mVibratePattern, 0);
                }
                else
                {
                    vibrator.cancel();
                }
            }
        });

        final Handler netCheck = new Handler();

        netCheck.postDelayed(new Runnable() {
            public void run() {
                TextView partid=(TextView) findViewById(R.id.partid);
                partid.setText("Participant:"+pid);
                TextView warnings=(TextView) findViewById(R.id.warnings);
                if (!checkInternet()) {
                    warnings.setText("No internet connection");
                    netCheck.postDelayed(this, 2000);
                }
                else {
                    warnings.setText("");
                    netCheck.postDelayed(this, 120000);
                }

            }
        }, 1);

        /*//start the sensors
        initializeSensors();
        final Handler dataUpdate = new Handler();
        dataUpdate.postDelayed(new Runnable() {
            public void run() {
                    JSONObject data=new JSONObject();
                    try {
                        data.put("aX", accX);
                        data.put("aY", accY);
                        data.put("aZ", accZ);
                        data.put("gX", gX);
                        data.put("gY", gY);
                        data.put("gZ", gZ);
                        data.put("hr", heartRate);
                        data.put("light", lightlevel);
                        data.put("temp", ambientTemp);
                        data.put("pr", pressure);
                        data.put("aq",System.currentTimeMillis());
                    }
                    catch (Exception e) {

                    }
                    sendData(data.toString(),""+pid);
                    dataUpdate.postDelayed(this, GLOBAL_UPDATE_RATE);


            }
        }, 1);*/






    //request to use the body sensors
        String[] perms = {"android.permission.BODY_SENSORS"};
        if (checkSelfPermission("android.permission.BODY_SENSORS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(perms, 200);
        };
        //request permission to start stuff from the background
        if (!Settings.canDrawOverlays(getApplicationContext())) {
            Intent myIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            Uri uri = Uri.fromParts("package", getPackageName(), null);

            myIntent.setData(uri);
            startActivityForResult(myIntent, 300);
            return;
        }
        //start the monitoring service
        if (!isServiceRunning(dataservice.class) ) {
            Intent i = new Intent(getApplicationContext(), dataservice.class);
            getApplicationContext().startService(i);
        }
    }
    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType()==Sensor.TYPE_ACCELEROMETER) {
            accX=event.values[0];
            accY=event.values[1];
            accZ=event.values[2];
        }
        if (event.sensor.getType()==Sensor.TYPE_GYROSCOPE) {
            gX=event.values[0];
            gY=event.values[1];
            gZ=event.values[2];
        }
        if (event.sensor.getType()==Sensor.TYPE_HEART_RATE) {
            heartRate=event.values[0];
        }
        if (event.sensor.getType()==Sensor.TYPE_LIGHT) {
            lightlevel=event.values[0];
        }
        if (event.sensor.getType()==Sensor.TYPE_AMBIENT_TEMPERATURE) {
            ambientTemp=event.values[0];
        }
        if (event.sensor.getType()==Sensor.TYPE_PRESSURE) {
            pressure=event.values[0];
        }
    }
/*
    @Override
    protected void onResume() {
        super.onResume();
        initializeSensors();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
    }*/
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        /*
        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager mgr = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000, pendingIntent);*/
    }



    private boolean checkInternet() { //returns true if internet is connected

        try {
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress("8.8.8.8", 53), 1500);
            sock.close();


            return true;
        } catch (IOException e) { return false; }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}