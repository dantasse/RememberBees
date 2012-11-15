package com.dantasse.rememberbees;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class RememberBeesActivity extends Activity implements
        SensorEventListener, OnClickListener {

    SensorManager sensorManager;
    Sensor accelerometer;
    private static final int SAMPLING_SPEED = SensorManager.SENSOR_DELAY_FASTEST;
    Button calibrateButton;
    Button testButton;
    Button goButton;
    TextView textView;
    private State currentState = State.NONE;
    NumberFormat nf = NumberFormat.getNumberInstance();
    Handler handler;

    // for testing:
    private int numSamples;
    private long testStartTime;
    // for calibrating:
    private float expAvgCal = 0;
    private long calibrateStartTime;
    private int secondsForPreCalibrating;
    private List<Float> calibrationReadings = new ArrayList<Float>();
    static final int NUM_CALIBRATION_WHACKS = 6;
    WhackThresholdCalculator whackThresholdCalculator = new WhackThresholdCalculator();
    // for listening:
    private float expAvg = 0;
    private float whackThreshold = 0; // will be negative; TODO should we absolute-value this?

    private enum State {
        TESTING, //  making sure your phone's accelerometer is fast enough 
        PRE_CALIBRATING, // countdown before calibration
        CALIBRATING, // get a good sense of how hard you hit your phone
        LISTENING, // listening for whacks
        BUZZING, // or between buzzes
        NONE; // like when you open the app
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remember_bees);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager
                .getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, accelerometer, SAMPLING_SPEED);

        calibrateButton = (Button) findViewById(R.id.calibrate_button);
        testButton = (Button) findViewById(R.id.test_button);
        goButton = (Button) findViewById(R.id.go_button);
        textView = (TextView) findViewById(R.id.hello_world_text_view);

        calibrateButton.setOnClickListener(this);
        testButton.setOnClickListener(this);
        goButton.setOnClickListener(this);

        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(2);

        handler = new Handler();
    }

    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SAMPLING_SPEED);
    }

    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_remember_bees, menu);
        return true;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { /* nothing */
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch(currentState) {
        case TESTING:
            numSamples++;
            long now = System.currentTimeMillis();
            if (now - testStartTime > 5000) {
                long numMs = now - testStartTime;
                double sampleRate = numSamples * 1000.0 / numMs;
                String displayText = "Sample Rate: " + nf.format(sampleRate) + "\n";
                if (sampleRate > 90) {
                    displayText += "RememberBees will work great.";
                } else if (sampleRate > 40 && sampleRate <= 90) {
                    displayText += "RememberBees might work.";
                } else {
                    displayText += "RememberBees probably won't work.";
                }
                textView.setText(displayText);
                changeUiState(State.NONE);
            }
            break;
        case PRE_CALIBRATING:
            // nothing
            break;
        case CALIBRATING:
            float zCal = event.values[2]; // 0 is x, 1 is y
            expAvgCal = expAvgCal * .5f + zCal * .5f;
            float outputCal = zCal - expAvgCal;
            calibrationReadings.add(outputCal);
            long nowCal = System.currentTimeMillis();
            if (nowCal - calibrateStartTime > 5000) {
                float[] calibrationArray = new float[calibrationReadings.size()];
                for (int i = 0; i < calibrationArray.length; i++) {
                    calibrationArray[i] = calibrationReadings.get(i).floatValue();
                }
                whackThreshold = whackThresholdCalculator.determineWhackThreshold(calibrationArray);

                //TODO remove the below; it's just to output a lot of text for debugging
                List<Pair<Float, Integer>> lowests = findLowests(calibrationReadings);
                changeUiState(State.NONE);
                String lowestsStr = "";
                for (Pair<Float, Integer> lowest : lowests) {
                    lowestsStr += nf.format(lowest.first) + "--" + lowest.second + "\n";
                }
                lowestsStr = "whackThreshold: " + whackThreshold + "\n" + lowestsStr;
                textView.setText(lowestsStr);
                //TODO remove the above
            }
            break;
        case LISTENING:
            float z = event.values[2]; // 0 is x, 1 is y
            expAvg = expAvg * .5f + z * .5f;
            float output = z - expAvg;
            String listenOutputText = "Output: " + nf.format(output);
            if (output < whackThreshold) {
                listenOutputText = "WHACK" + listenOutputText;                
            }
            textView.setText(listenOutputText);
            //TODO
            break;
        case BUZZING:
            // TODO
            break;
        case NONE:
            // nothing
            break;
        default:
            throw new IllegalStateException("Well, how did I get here?");
        }
    }

    @Override
    public void onClick(View v) {
        if (v.equals(testButton)) {
            changeUiState(State.TESTING);
        } else if (v.equals(calibrateButton)) {
            changeUiState(State.PRE_CALIBRATING);
        } else if (v.equals(goButton)) {
            changeUiState(State.LISTENING);
        }
    }

    private void disableInputs() {
        testButton.setEnabled(false);
        calibrateButton.setEnabled(false);
        goButton.setEnabled(false);
    }

    private void allowInputs() {
        testButton.setEnabled(true);
        calibrateButton.setEnabled(true);
        goButton.setEnabled(true);
    }
    
    private Runnable preCalibrate = new Runnable() {
        @Override
        public void run() {
            textView.setText("Put your phone in your pocket and hit it 6 " +
            		"times. Starting in: " + secondsForPreCalibrating +
            		" seconds.");
            if (secondsForPreCalibrating > 0) {
                secondsForPreCalibrating--;
                handler.postDelayed(this, 1000);
            } else {
                changeUiState(State.CALIBRATING);
                calibrateStartTime = System.currentTimeMillis();
            }
        }
    };
    
    private List<Pair<Float, Integer>> findLowests(List<Float> readings) {

        List<Pair<Float, Integer>> lowests = new ArrayList<Pair<Float, Integer>>();
        for(int i = 0; i < 10; i++)
            lowests.add(new Pair<Float, Integer>(0.0f, 0));
        
        for(int i = 0; i < readings.size(); i++) {
            Float f = readings.get(i);
        
            int numLowerThanF = 0;
            for (Pair<Float, Integer> pair : lowests) {
                if (pair.first < f) {
                    numLowerThanF++;
                }
            }
            if (numLowerThanF < 10) {
                lowests.remove(lowests.size() - 1);
                lowests.add(new Pair<Float, Integer>(f, i));
            }
            Collections.sort(lowests, new Comparator<Pair<Float, Integer>>() {
                @Override
                public int compare(Pair<Float, Integer> lhs,
                        Pair<Float, Integer> rhs) {
                    return lhs.first.compareTo(rhs.first);
                }
            });
        }
        return lowests;
    }
    
   
    
    private void changeUiState(State newState) {
        switch (newState) {
        case TESTING:
            disableInputs();
            numSamples = 0;
            testStartTime = System.currentTimeMillis();
            textView.setText("Testing...");
            break;
        case PRE_CALIBRATING:
            disableInputs();
            secondsForPreCalibrating = 5;
            handler.post(preCalibrate);
            break;
        case CALIBRATING:
            calibrationReadings.clear();
            textView.setText("Put your phone in your pocket and hit it 6 times.");
            break;
        case LISTENING:
//            disableInputs();
            textView.setText("Listening...");
            break;
        case BUZZING:
            disableInputs();
            break;
        case NONE:
            allowInputs();
            break;
        }
        currentState = newState;
    }
}
