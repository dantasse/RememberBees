package com.dantasse.rememberbees;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class RememberBeesActivity extends Activity implements
        SensorEventListener, OnClickListener {

    private static final int SAMPLING_SPEED = SensorManager.SENSOR_DELAY_FASTEST;
    private static final int WHACK_TIMEOUT = 500; // ms between whacks
    
    SensorManager sensorManager;
    Sensor accelerometer;
    Vibrator vibrator;
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
    private long calibrateStartTime;
    private int secondsForPreCalibrating;
    private List<Float> calibrationReadings = new ArrayList<Float>();
    static final int NUM_CALIBRATION_WHACKS = 6;
    WhackThresholdCalculator whackThresholdCalculator = new WhackThresholdCalculator();
    // for listening:
    private float xExpAvg = 0;
    private float yExpAvg = 0;
    private float zExpAvg = 0;
    private float whackThreshold = 5f;
    
    private enum State {
        TESTING, //  making sure your phone's accelerometer is fast enough 
        PRE_CALIBRATING, // countdown before calibration
        CALIBRATING, // get a good sense of how hard you hit your phone
        LISTENING, // listening for whacks
        HEARD_1, // heard one whack, waiting for the second for it to count
        REMINDING, // or between buzzes
        HEARD_1_ON, // heard one whack, waiting for the second, to stop the reminding
        NONE; // like when you open the app
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("RememberBees", "onCreate");
        setContentView(R.layout.activity_remember_bees);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager
                .getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(this, accelerometer, SAMPLING_SPEED);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

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
        for (int i = 0; i < buzzes.length - 1; i++) {
            buzzes[i] = new Runnable() {
                @Override
                public void run() {
                    vibrator.vibrate(1000);
                    // TODO also post some kind of update on the screen, like
                    // how many buzzes are left
                }
            };
        }
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
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        changeUiState(State.valueOf(savedInstanceState.getString("RememberBeesState")));
        Log.d("RememberBees", "RESTORING STATE: " + savedInstanceState.getString("RememberBeesState"));
        // TODO but all the dang timers have to get reset too
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("RememberBeesState", currentState.toString());
        Log.d("RememberBees", "SAVING STATE: " + outState.getString("RememberBeesState"));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { /* nothing */ }
    
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
                Log.d("RememberBees", displayText);
                if (sampleRate > 90) {
                    displayText += "RememberBees will work great.";
                } else if (sampleRate > 40 && sampleRate <= 90) {
                    displayText += "RememberBees might work.";
                } else {
                    displayText += "RememberBees probably won't work.";
                }
                changeUiState(State.NONE);
                textView.setText(displayText);
            }
            break;
        case PRE_CALIBRATING:
            // nothing
            break;
        case CALIBRATING:
            float outputCal = calculateValue(event.values);
            calibrationReadings.add(outputCal);
            long nowCal = System.currentTimeMillis();
            if (nowCal - calibrateStartTime > 5000) {
                float[] calibrationArray = new float[calibrationReadings.size()];
                for (int i = 0; i < calibrationArray.length; i++) {
                    calibrationArray[i] = calibrationReadings.get(i).floatValue();
                }
                whackThreshold = whackThresholdCalculator.determineWhackThreshold(calibrationArray);
                changeUiState(State.NONE);

                //TODO remove the below; it's just to output a lot of text for debugging
                List<Pair<Float, Integer>> lowests =
                        whackThresholdCalculator.findHighests(calibrationReadings);
                String lowestsStr = "";
                for (Pair<Float, Integer> lowest : lowests) {
                    lowestsStr += nf.format(lowest.first) + "--" + lowest.second + "\n";
                }
                lowestsStr = "whackThreshold: " + whackThreshold + "\n" + lowestsStr;
                textView.setText(lowestsStr);
                //TODO remove the above
            }
            break;
        case LISTENING: // fall through
        case HEARD_1: // fall through
        case REMINDING: // fall through
        case HEARD_1_ON: // detect whacks in all these cases
            float output = calculateValue(event.values); //z - zExpAvg;
            if (output > whackThreshold) {
                whackHappened();
            }
            break;
        case NONE:
            // nothing
            break;
        default:
            throw new IllegalStateException("Well, how did I get here?");
        }
    }

    private float calculateValue(float[] values) {
        float x = Math.abs(values[0]);
        xExpAvg = xExpAvg * .5f + x * .5f;
        float y = Math.abs(values[1]);
        yExpAvg = yExpAvg * .5f + y * .5f;
        float z = Math.abs(values[2]);
        zExpAvg = zExpAvg * .5f + z * .5f;
        return z - zExpAvg;
    }

    private void whackHappened() {
        switch(currentState) {
        case LISTENING:
            // got to post this (here and in all below cases) because otherwise it'll still be
            // reading a whack while it's in the next state, so it'll register twice.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    changeUiState(State.HEARD_1);
                }
            }, 100);
            break;
        case HEARD_1:
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    changeUiState(State.REMINDING);
                }
            }, 100);
            break;
        case REMINDING:
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    changeUiState(State.HEARD_1_ON);
                }
            }, 100);
            break;
        case HEARD_1_ON:
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    changeUiState(State.LISTENING);
                }
            }, 100);
            break;
        default:
            break;
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
    
    private Runnable[] buzzes = new Runnable[6];
    private Runnable stopper = new Runnable() {
        @Override
        public void run() {
            changeUiState(State.LISTENING);
        }
    };
    private Runnable whackTimeout = new Runnable() {
        @Override
        public void run() {
            changeUiState(State.LISTENING);
        }
    };
    
    private void changeUiState(State newState) {
        for (Runnable buzz : buzzes) {
            handler.removeCallbacks(buzz);
        }
        handler.removeCallbacks(stopper);
        handler.removeCallbacks(whackTimeout);
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
            disableInputs();
            vibrator.vibrate(new long[]{200, 400, 200}, -1);
            textView.setText("Listening...");
            break;
        case HEARD_1:
            disableInputs();
            textView.setText("Heard one whack, waiting for the second.");
            handler.postDelayed(whackTimeout, WHACK_TIMEOUT);
            break;
        case REMINDING:
            disableInputs();
            handler.post(buzzes[0]);
            handler.postDelayed(buzzes[1], 3 * 1000);
            handler.postDelayed(buzzes[2], 9 * 1000);
            handler.postDelayed(buzzes[3], 27 * 1000);
            handler.postDelayed(buzzes[4], 81 * 1000);
            handler.postDelayed(buzzes[5], 243 * 1000); // about 4 minutes
            handler.postDelayed(stopper, 244 * 1000);
            textView.setText("Reminding you sometimes now");
            break;
        case HEARD_1_ON:
            disableInputs();
            textView.setText("Heard one whack, waiting for the second.");
            handler.postDelayed(whackTimeout, WHACK_TIMEOUT);
            break;
        case NONE:
            allowInputs();
            textView.setText("");
            break;
        }
        currentState = newState;
    }
}
