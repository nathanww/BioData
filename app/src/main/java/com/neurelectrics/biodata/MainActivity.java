package com.neurelectrics.biodata;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.StrictMode;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;

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

        //check to see if we have a device ID, if not then make one
        if (sharedPref.getInt("pid",-1) == -1) {
            Random rand = new Random();
            editor.putInt("pid",rand.nextInt(2147483647));
            editor.commit();
        }
        int pid=sharedPref.getInt("pid",-1);






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

        //start the sensors
        initializeSensors();
        final Handler dataUpdate = new Handler();
        dataUpdate.postDelayed(new Runnable() {
            public void run() {
                    JSONObject data=new JSONObject();
                    try {
                        data.put("accX", accX);
                        data.put("accY", accY);
                        data.put("accZ", accZ);
                        data.put("gX", gX);
                        data.put("gY", gY);
                        data.put("gZ", gZ);
                        data.put("hr", heartRate);
                    }
                    catch (Exception e) {

                    }
                    sendData(data.toString(),""+pid);
                    dataUpdate.postDelayed(this, GLOBAL_UPDATE_RATE);


            }
        }, 1);







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
            heartRate=event.data[0];
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        initializeSensors();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
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
        hr = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (hr != null) {
            sm.registerListener(this, hr, ACC_SAMPLE_RATE*1000);

        }
    }
    void sendData(String data, String userID) {

        try {
            RequestQueue requestQueue = Volley.newRequestQueue(this);
            String URL = "https://biostream-1024.appspot.com/sendps?user="+userID+"&data="+data;


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

    private boolean checkInternet() { //returns true if internet is connected

        try {
            Socket sock = new Socket();
            sock.connect(new InetSocketAddress("8.8.8.8", 53), 1500);
            sock.close();


            return true;
        } catch (IOException e) { return false; }
    }
}