package com.dantasse.rememberbees;

import java.text.NumberFormat;
import java.util.logging.Logger;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Menu;
import android.widget.TextView;

public class RememberBeesActivity extends Activity implements
        SensorEventListener {

    SensorManager mSensorManager;
    Sensor mAccelerometer;
    // MSP sampled 256Hz
    // NORMAL gives me about 7Hz (on Nexus S); UI, GAME, FASTEST all about 50Hz
    private static final int SAMPLING_SPEED = SensorManager.SENSOR_DELAY_FASTEST;
    
    /** List of the last 50 z-axis averages, with a decay factor of .5 */
    private float[] lastZs = new float[50];

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remember_bees);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mSensorManager.registerListener(this, mAccelerometer, SAMPLING_SPEED);

        for (int i = 0; i < lastZs.length; i++) {
            lastZs[i] = 0f;
        }
    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SAMPLING_SPEED);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_remember_bees, menu);
        return true;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private float expAvg = 0;
    private int readings = 0;
    private long lastPrint = System.currentTimeMillis();

    @Override
    public void onSensorChanged(SensorEvent event) {
//        readings ++;
//        long now = System.currentTimeMillis();
//        if (now - lastPrint > 1000) {
//            Logger.getAnonymousLogger().info("this many readings last second: " + readings);
//            readings = 0;
//            lastPrint = now;
//        }
        

        TextView tv = (TextView) findViewById(R.id.hello_world_text_view);
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        if (expAvg == 0) {
            expAvg = z;
        } else {
            expAvg = expAvg * .5f + z * .5f;
        }

        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(2);
        float mag = (z - expAvg);
        if (mag < -3) {
          Logger.getAnonymousLogger().info("Z:\t" + nf.format(z) + "\tmag:\t" + nf.format(mag));
        }
    }

}
