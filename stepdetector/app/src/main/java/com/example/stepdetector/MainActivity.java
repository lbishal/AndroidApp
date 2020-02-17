/*
This application implements a real-time step detection based on android phone's accelerometer signal.
Based on the accelerometer signal magnitude, speed estimate (surrogate) is computed. This is filtered
with moving average filter. Then a template matching is done, with checks that the detected step
template, have a certain threshold of signal magnitude (to avoid spurious detections from noise).
Template is updated as signature of given person's steps are identified.

 */

package com.example.stepdetector;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private TextView mXAccelerationTextView;
    private FileOutputStream fOut;
    private double detectorThreshold;
    private int countSensorData;
    private int latestDecision =0;//variable to keep track of step detection
    private int countStep = 0;
    private double templateCorrelationThreshold = 0.7;

    private Double[] templateSignal = { -0.124511,-0.14722985, -0.14286704, -0.06707362,
            -0.05615815,-0.03482528, -0.00217015,  0.04454678,  0.0686863 ,  0.13771749,
            0.16672203,  0.18137357,  0.05211326, -0.06255626, -0.12578879,-0.077148};

    //store information about incoming sensor data
    int numElements       = 3;
    int templatePoints    = 14;
    ArrayDeque accDeque   = new ArrayDeque<Double>(numElements);
    ArrayDeque timeDeque  = new ArrayDeque<Long>(numElements);
    ArrayDeque speedDeque = new ArrayDeque<Double>(numElements);
    ArrayDeque speedTemplateDeque = new ArrayDeque<Double>(templatePoints);
    double currSpeed;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Obtain reference to accelerometer sensors
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //obtain a reference to file
        File savePath = getExternalFilesDir(null);
        Log.d("Sys Out",savePath.toString());
        File saveFile = new File(savePath,"data_step.csv");

        try {
            fOut = new FileOutputStream(saveFile);
        }
        catch (Exception e) {
            Log.d("Error","File not found");
        }

        //initialize array to save sensor data
        for(int i=0; i<numElements; i++){
            accDeque.add(0.0);
            timeDeque.add(0L);
            speedDeque.add(0.0);
        }

        //initialize array to save speed for template matching
        for(int i=0; i<templatePoints; i++) {
            speedTemplateDeque.add(0.0);
        }

        //initialize sensor data count
        countSensorData = 0;
    }

    //@Override
    protected void onResume() {
        super.onResume();
    }

    protected void onDestroy() {
        //close the output stream for file write
        try {
            fOut.close();
        }
        catch (Exception e) {
            Log.d("Error","Could not close the file where data was written");
        }
        super.onDestroy();
    }

    //when start button is clicked
    public void startStepDetection(View view) {
        Log.d("message", "Step Detector started");
        //start obtaining the accelerometer data
        mSensorManager.registerListener(this, mAccelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);

        //read the threshold provided by the user
        EditText et         = findViewById(R.id.thresholdInput);
        String thresholdVal = et.getText().toString();
        detectorThreshold   = Double.parseDouble(thresholdVal);
    }

    //when stop button is clicked
    public void stopStepDetection(View view) {
        Log.d("message", "Step Detector stopped");
        //de-register listener
        mSensorManager.unregisterListener(this,mAccelerometer);

        //reset accelerometer and count view
        mXAccelerationTextView = findViewById(R.id.accOutput);
        float[] eventZero = {0f,0f,0f};
        mXAccelerationTextView.setText(Arrays.toString(eventZero));

        TextView countResults = (TextView)findViewById(R.id.countResults);
        countStep = 0;
        countResults.setText(String.valueOf(countStep));

        //reset counter of sensor data
        countSensorData = 0;
    }

    //adapted from https://www.geeksforgeeks.org/program-find-correlation-coefficient/
    double correlationCoefficient(Double X[], Double Y[])
    {
        double sum_X = 0, sum_Y = 0, sum_XY = 0;
        double squareSum_X = 0, squareSum_Y = 0;
        int n = X.length;

        for (int i = 0; i < n; i++)
        {
            // sum of elements of array X.
            sum_X = sum_X + X[i];

            // sum of elements of array Y.
            sum_Y = sum_Y + Y[i];

            // sum of X[i] * Y[i].
            sum_XY = sum_XY + X[i] * Y[i];

            // sum of square of array elements.
            squareSum_X = squareSum_X + X[i] * X[i];
            squareSum_Y = squareSum_Y + Y[i] * Y[i];
        }

        // use formula for calculating correlation coefficient.
        double corr = (double)(n * sum_XY - sum_X * sum_Y)
                / Math.sqrt((n * squareSum_X - sum_X * sum_X)
                * (n * squareSum_Y - sum_Y * sum_Y));

        return corr;
    }

    //function to compute correlation
    public double computeCorrelation( ) {
        Object[] speedTemplateObj = speedTemplateDeque.toArray();
        Double[] speedTemplate     = new Double[speedTemplateDeque.size()];
        for(int i=0;i<speedTemplateDeque.size();i++){
            speedTemplate[i] = (Double) (speedTemplateObj[i]);
        }
        Log.d("Debug",Arrays.toString(speedTemplate));

        //compute correlation between speedTemplate and templateSignal
        double currCorrelation = correlationCoefficient(speedTemplate,
                                            templateSignal);
        return currCorrelation;
    }

    public double rangeSpeed() {
        Object[] speedTemplateObj = speedTemplateDeque.toArray();
        Double[] speedTemplate     = new Double[speedTemplateDeque.size()];
        for(int i=0;i<speedTemplateDeque.size();i++){
            speedTemplate[i] = (Double) (speedTemplateObj[i]);
        }
        double minSpeed = Collections.min(Arrays.asList(speedTemplate));
        double maxSpeed = Collections.max(Arrays.asList(speedTemplate));
        double speedRange = maxSpeed - minSpeed;
        return speedRange;
    }

    //Function called when sensor values change
    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER)
        {
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                countSensorData = countSensorData + 1;
                float acc_x = event.values[0];
                float acc_y = event.values[1];
                float acc_z = event.values[2];
                float acc_mag = (float)(java.lang.Math.sqrt(acc_x*acc_x + acc_y*acc_y + acc_z*acc_z));
                float acc_mag_abs = acc_mag - (float)(9.8);
                long event_time = event.timestamp;
                String event_time_str = String.valueOf(event_time);
                //Log.d("acc mag:", Float.toString(acc_mag));
                mXAccelerationTextView = findViewById(R.id.accOutput);
                mXAccelerationTextView.setText(Arrays.toString(event.values));

                //logic for step detection
                if(countSensorData==1){
                    currSpeed = 0.0;
                }
                else {
                    double pastAcc = (double) accDeque.getLast();
                    long pastTime = (long) timeDeque.getLast();
                    double timeFactor = ((event_time - pastTime) / (Math.pow(10, 9)));
                    currSpeed = (acc_mag_abs - pastAcc) * timeFactor;
                }

                //add new data to deques
                accDeque.add((double)(acc_mag_abs));
                timeDeque.add(event_time);
                speedDeque.add(currSpeed);

                //remove the first elements
                accDeque.removeFirst();
                timeDeque.removeFirst();
                speedDeque.removeFirst();

                Object[] speedObj = speedDeque.toArray();
                double speedSum = 0;
                for(int i=0;i<numElements;i++){
                    double speed = (double) speedObj[i];
                    speedSum = speedSum + speed;
                }
                double speedSmooth = speedSum/numElements;
                //Log.d("Algo","Speed smooth is "+String.valueOf(speedSmooth)+" Threshold is "+String.valueOf(detectorThreshold));
                int currDecision = 0;

                //add speedSmooth to the deque
                speedTemplateDeque.add(speedSmooth);
                speedTemplateDeque.removeFirst();

                double currCorrelation = computeCorrelation();
                Log.d("Debug","Correlation is "+String.valueOf(currCorrelation));
                float currentDecision = 0;

                if(currCorrelation > templateCorrelationThreshold){
                    double currentSpeedRange = rangeSpeed();
                    if(currentSpeedRange < detectorThreshold) {
                        currentDecision = 0;
                    }
                    else {
                        currDecision = 1;
                        countStep = countStep + 1;

                        //update template signal
                        Object[] speedTemplateObj = speedTemplateDeque.toArray();
                        Double[] speedTemplate     = new Double[speedTemplateDeque.size()];
                        for(int i=0;i<speedTemplateDeque.size();i++){
                            speedTemplate[i] = (Double) (speedTemplateObj[i]);
                        }
                        for (int i=0;i<templatePoints;i++) {
                            templateSignal[i] = templateSignal[i] + 0.2*speedTemplate[i];
                        }

                        for(int i=0;i<templatePoints;i++){
                            speedTemplateDeque.add(0.0);
                            speedTemplateDeque.removeFirst();
                        }
                    }
                }
                else {
                    currentDecision = 0;
                }


                //update the view elements with the step detection results
                TextView countResults = (TextView)findViewById(R.id.countResults);
                countResults.setText(String.valueOf(countStep));

                String stepDetectionString;
                if(currentDecision == 1){
                    stepDetectionString = "Step";
                }
                else {
                    stepDetectionString = "No Step";
                }
                TextView stepResult = (TextView)findViewById(R.id.stepDecision);
                stepResult.setText(stepDetectionString);

                //write to file
                try {
                    fOut.write((String.valueOf(acc_mag_abs)+ ','+ event_time_str+'\n').getBytes());
                    fOut.flush();
                    //Log.d("Success", "Files written");
                }
                catch (Exception e){
                    e.printStackTrace();
                    //Log.d("Error","Could not write to file");
                }

            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

}
