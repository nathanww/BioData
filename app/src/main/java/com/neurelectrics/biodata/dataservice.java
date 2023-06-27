package com.neurelectrics.biodata;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StrictMode;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.util.ArrayList;

public class dataservice extends Service implements SensorEventListener {
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
    private int GLOBAL_UPDATE_RATE=10000;


    ArrayList<Float> buffer_accX=new ArrayList<Float>();
    ArrayList<Float> buffer_accY=new ArrayList<Float>();
    ArrayList<Float> buffer_accZ=new ArrayList<Float>();

    ArrayList<Float> buffer_gX=new ArrayList<Float>();
    ArrayList<Float> buffer_gY=new ArrayList<Float>();
    ArrayList<Float> buffer_gZ=new ArrayList<Float>();

    ArrayList<Float> buffer_hr=new ArrayList<Float>();

    ArrayList<Float> buffer_temp=new ArrayList<Float>();
    ArrayList<Float> buffer_pressure=new ArrayList<Float>();
    ArrayList<Float> buffer_light=new ArrayList<Float>();
    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;

    public dataservice() {

    }


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "42",
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startid) {
        //creat the notification
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, "42")
                .setContentTitle("BioData")
                .setContentText("BioData is collecting data")
                .setSmallIcon(com.google.android.gms.base.R.drawable.common_google_signin_btn_icon_dark)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);
       sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
       editor = sharedPref.edit();

        //initialize sensor manager
        sm = ((SensorManager)getSystemService(SENSOR_SERVICE));
        //allow internet accsess on UI thread
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "BioData:dataAcquistion");

        wakeLock.acquire();
        SharedPreferences sharedPref = getApplicationContext().getSharedPreferences("prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        int pid=sharedPref.getInt("pid",-1);
        //start the sensors
        initializeSensors();

        //this samples the motion senosrs at the target update rate and writes to the buffer
        final Handler motionUpdate = new Handler();
        motionUpdate.postDelayed(new Runnable() {
            public void run() {
                buffer_accX.add(accX);
                buffer_accY.add(accY);
                buffer_accZ.add(accZ);
                buffer_gX.add(gX);
                buffer_gY.add(gY);
                buffer_gZ.add(gZ);
                //Log.i("sampling","done");
                motionUpdate.postDelayed(this, ACC_SAMPLE_RATE);


            }
        }, 1000);
        
        final Handler dataUpdate = new Handler();
        dataUpdate.postDelayed(new Runnable() {
            public void run() {

                JSONObject data=new JSONObject();
                try {
                    data.put("aX", buffer_accX);
                    data.put("aY", buffer_accY);
                    data.put("aZ", buffer_accZ);
                    data.put("gX", buffer_gX);
                    data.put("gY", buffer_gY);
                    data.put("gZ", buffer_gZ);
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
        }, 1);


        final Handler restart = new Handler();
        restart.postDelayed(new Runnable() {
            public void run() {
                if (!sharedPref.getBoolean("running",false)) {
                    /*Intent i = new Intent(getApplicationContext(), MainActivity.class);
                    i.addFlags(FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);*/
                }
                dataUpdate.postDelayed(this, 10000);


            }
        }, 10000);
        return Service.START_REDELIVER_INTENT;
    }



    void initializeSensors() {
        //initialize accelerometer
        Sensor acc;
        acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (acc != null) {
            sm.registerListener(this, acc, ACC_SAMPLE_RATE*1000);

        }
        //initialize gyro
        Sensor gyro;
        gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyro != null) {
            sm.registerListener(this, gyro, ACC_SAMPLE_RATE*1000);

        }
        //initialize heart rate sensor
        Sensor hr;
        hr = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (hr != null) {
            sm.registerListener(this, hr, ACC_SAMPLE_RATE*1000);

        }
        //initialize light sensor
        Sensor light;
        light = sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (light != null) {
            sm.registerListener(this, light, ACC_SAMPLE_RATE*1000);

        }
        //initialize temperature sensor
        Sensor temp;
        temp = sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        if (temp != null) {
            sm.registerListener(this, temp, ACC_SAMPLE_RATE*1000);
        }
        else { //if we have no ambient temeprature sensor, try for an unspecified one?
            temp = sm.getDefaultSensor(Sensor.TYPE_TEMPERATURE);
            if (temp != null) {
                sm.registerListener(this, temp, ACC_SAMPLE_RATE*1000);
            }
        }

        //initialize pressure sensor
        Sensor airp;
        airp = sm.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (airp != null) {
            sm.registerListener(this, airp, ACC_SAMPLE_RATE*1000);

        }
    }
    void sendData(String data, String userID) {
        try {

            RequestQueue requestQueue = Volley.newRequestQueue(this);
            String URL = "https://biostream-1024.appspot.com/sendps?user="+userID+"&data="+data;
            boolean worked=false;

            StringRequest stringRequest = new StringRequest(Request.Method.GET, URL, new Response.Listener<String>() {

                @Override
                public void onResponse(String response) {
                    Log.i("VOLLEY", response);
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.e("VOLLEY", error.toString());
                }
            }) {


                @Override
                protected Response<String> parseNetworkResponse(NetworkResponse response) {
                    String responseString = "";
                    if (response != null) {
                        responseString = String.valueOf(response.statusCode);
                        // can get more details such as response.headers
                        Log.i("requestStat",""+response.statusCode);
                        if (response.statusCode == 200) {
                            buffer_accX.clear();
                            buffer_accY.clear();
                            buffer_accZ.clear();
                            buffer_gX.clear();
                            buffer_gY.clear();
                            buffer_gZ.clear();
                        }
                    }
                    return Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response));
                }
            };
            stringRequest.setRetryPolicy(new DefaultRetryPolicy(50 * 1000, 5, 1.0f));
            requestQueue.add(stringRequest);
        } catch (Exception e) {
            e.printStackTrace();
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
}