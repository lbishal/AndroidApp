/*
This application implements a real-time gesture detection based on android phone's accelerometer signal.
Following mathematical figures are detected:
    Circle, Square, Triangle.


Based on the accelerometer signal magnitude:
    speed estimate (surrogate) is computed.
    This is filtered with moving average filter.
    Then a template matching is done, with checks that the detected template, have a certain
      threshold of signal magnitude (to avoid spurious detections from noise).
    Template is updated as signature of given shape are identified on the go.

 */

package com.example.gesturedetector;

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
import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
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
    private double templateCorrelationThreshold = 0.75;
    private Double[] circleTemplate = { 0.00969141,  0.00784496,  0.03088336,  0.04756067,  0.07052619,
            0.0401853 , -0.00025768, -0.09020558, -0.09975169, -0.10242839,
            -0.07698634, -0.06279815, -0.04915227, -0.00810394, -0.00379611,
            0.03359246,  0.03856481,  0.1054514 ,  0.08210032,  0.10108479,
            0.04927021,  0.06896833,  0.07461435,  0.07900467,  0.05565398,
            0.01039143, -0.03061315, -0.06472117, -0.10499691, -0.11827443,
            -0.10608554, -0.08973891, -0.06748583, -0.04535976, -0.00152234};

    private Double[] squareTemplate = {0.00419627,  0.02140595,  0.04115705,  0.04133982, -0.01444644,
            -0.03988721, -0.03227397, -0.00207788, -0.02129005, -0.04885056,
            -0.0631281 , -0.08983067, -0.08167325, -0.0465717 ,  0.07003027,
            0.13783668,  0.22794434,  0.16446736,  0.1433825 ,  0.01017048,
            -0.05854553, -0.1328194 , -0.09377081, -0.01036964,  0.06931413,
            0.04195654,  0.02418591, -0.01900421,  0.00120961, -0.02903992,
            -0.08193207, -0.12744195, -0.13876107, -0.08331881, -0.04673043};

    private Double[] triangleTemplate = {-0.04006087, -0.0724415 , -0.00600627,  0.04002379,  0.07363573,
            0.0448149 ,  0.11911902,  0.12953733,  0.11576079, -0.00614756,
            -0.0247129 , -0.10136406, -0.09530652, -0.10644339, -0.04243002,
            -0.00642079,  0.03926032,  0.04981631,  0.07426349,  0.03971873,
            0.03252332, -0.02726553, -0.02045063, -0.00534629,  0.02029409,
            0.02553932,  0.03244879,  0.01680453,  0.00226621, -0.05226111,
            -0.12199557, -0.12398746, -0.08488322, -0.01382969, -0.00752589};

    //store information about incoming sensor data
    int numElements       = 3;
    int templatePoints    = 35;
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
        File saveFile = new File(savePath,"data_gesture.csv");

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
    public void startGestureDetection(View view) {
        Log.d("message", "Gesture Detector started");
        //start obtaining the accelerometer data
        mSensorManager.registerListener(this, mAccelerometer,
                SensorManager.SENSOR_DELAY_NORMAL);

        //read the threshold provided by the user
        EditText et         = findViewById(R.id.thresholdInput);
        String thresholdVal = et.getText().toString();
        detectorThreshold   = Double.parseDouble(thresholdVal);
    }

    //when stop button is clicked
    public void stopGestureDetection(View view) {
        Log.d("message", "Gesture Detector stopped");
        //de-register listener
        mSensorManager.unregisterListener(this,mAccelerometer);

        //reset accelerometer and count view
        mXAccelerationTextView = findViewById(R.id.accOutput);
        float[] eventZero = {0f,0f,0f};
        mXAccelerationTextView.setText(Arrays.toString(eventZero));

        countStep = 0;

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
    public double computeCorrelationCircle( ) {
        Object[] speedTemplateObj = speedTemplateDeque.toArray();
        Double[] speedTemplate     = new Double[speedTemplateDeque.size()];
        for(int i=0;i<speedTemplateDeque.size();i++){
            speedTemplate[i] = (Double) (speedTemplateObj[i]);
        }
        Log.d("Debug",Arrays.toString(speedTemplate));

        //compute correlation between speedTemplate and templateSignal
        double currCorrelation = correlationCoefficient(speedTemplate, circleTemplate);
        return currCorrelation;
    }


    public double computeCorrelationSquare( ) {
        Object[] speedTemplateObj = speedTemplateDeque.toArray();
        Double[] speedTemplate     = new Double[speedTemplateDeque.size()];
        for(int i=0;i<speedTemplateDeque.size();i++){
            speedTemplate[i] = (Double) (speedTemplateObj[i]);
        }
        Log.d("Debug",Arrays.toString(speedTemplate));

        //compute correlation between speedTemplate and templateSignal
        double currCorrelation = correlationCoefficient(speedTemplate, squareTemplate);
        return currCorrelation;
    }


    public double computeCorrelationTriangle( ) {
        Object[] speedTemplateObj = speedTemplateDeque.toArray();
        Double[] speedTemplate     = new Double[speedTemplateDeque.size()];
        for(int i=0;i<speedTemplateDeque.size();i++){
            speedTemplate[i] = (Double) (speedTemplateObj[i]);
        }
        Log.d("Debug",Arrays.toString(speedTemplate));

        //compute correlation between speedTemplate and templateSignal
        double currCorrelation = correlationCoefficient(speedTemplate, triangleTemplate);
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

                double currCorrelationCircle   = computeCorrelationCircle();
                double currCorrelationSquare   = computeCorrelationSquare();
                double currCorrelationTriangle = computeCorrelationTriangle();
                Double[] allCorrelation = {currCorrelationCircle,currCorrelationSquare,currCorrelationTriangle};
                double maxCorrelation = Collections.max(Arrays.asList(allCorrelation));

                String shapeDetected = "No Shape";

                if(maxCorrelation > templateCorrelationThreshold){
                    double currentSpeedRange = rangeSpeed();
                    if(currentSpeedRange < detectorThreshold) {
                        shapeDetected = "No Shape";
                    }
                    else {

                        //to update template signal
                        Object[] speedTemplateObj = speedTemplateDeque.toArray();
                        Double[] speedTemplate     = new Double[speedTemplateDeque.size()];
                        for(int i=0;i<speedTemplateDeque.size();i++){
                            speedTemplate[i] = (Double) (speedTemplateObj[i]);
                        }

                        if(currCorrelationCircle > currCorrelationSquare && currCorrelationCircle > currCorrelationTriangle) {
                            shapeDetected = "Circle";
                            for (int i=0;i<templatePoints;i++) {
                                circleTemplate[i] = circleTemplate[i] + 0.2*speedTemplate[i];
                            }
                        }
                        else if(currCorrelationSquare > currCorrelationCircle && currCorrelationSquare > currCorrelationTriangle) {
                            shapeDetected = "Square";
                            for (int i=0;i<templatePoints;i++) {
                                squareTemplate[i] = squareTemplate[i] + 0.2*speedTemplate[i];
                            }
                        }
                        else {
                            shapeDetected = "Triangle";
                            for (int i=0;i<templatePoints;i++) {
                                triangleTemplate[i] = triangleTemplate[i] + 0.2*speedTemplate[i];
                            }
                        }
                        TextView lastShape = (TextView) findViewById(R.id.gestureDecisionLast);
                        lastShape.setText(shapeDetected);

                        //re-initialize array
                        //initialize array to save speed for template matching
                        for(int i=0; i<templatePoints; i++) {
                            speedTemplateDeque.add(0.0);
                            speedTemplateDeque.removeFirst();
                        }

                    }
                }
                else {
                    shapeDetected = "No Shape";
                }

                TextView currentShape = (TextView) findViewById(R.id.gestureDecision);
                currentShape.setText(shapeDetected);

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
