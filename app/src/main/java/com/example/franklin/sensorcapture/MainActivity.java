package com.example.franklin.sensorcapture;


import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.os.Environment;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.opencv.android.*;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.w3c.dom.Text;

public class MainActivity extends Activity implements SensorEventListener{

    //gps
    private LocationManager locationManager=null;
    private LocationListener locationListener=null;

    //global variables
    public static final String file = ("data.txt");
    public static File myData = null;
    public static File myPhone = null;
    public static File myDataCollection = null;
    public static Date currentTime = Calendar.getInstance().getTime();
    public static final String DATA_COLLECTION_FILE = currentTime.toString() + ".csv";

    private CameraBridgeViewBase openCvCameraView;
    private CascadeClassifier cascadeClassifier;
    private Mat grayscaleImage;
    private int absoluteFaceSize;
    private Boolean flag = false;

    public double longitude, latitude;
    public String phonestate = "";
    public String personstate = "";
    public String stationname = "";
    public SensorEventListener mSensorListener ;
    public SensorManager sensorManager;
    public List<Sensor> listSensor;
    private  Sensor mAccelerometer,mProximity, mAmbientTemp, mGameRotationVect, mGeomagneticRotationVecor, mGravity, mGyroscope,
            mGyroUncal, mLight, mLinearAcceleration, mMagFld, mMagFldUncal, mOrientation, mPressure, mRelativeHumid, mRotVect,
            mSignificantMotion, mStepCntr, mStepDetect, mTemp;

    public float barometer = 0, accelerometer = 0, temperature_sensor = 0, light_sensor = 0, magnetometer = 0, linear_acc = 0, linear_gyro = 0, accx = 0, accy = 0, accz = 0, gyrox = 0, gyroy = 0, gyroz = 0, GRVx = 0, GRVy = 0, GRVz = 0, GeoRVx = 0, GeoRVy = 0, GeoRVz = 0, rvx = 0, rvy = 0, rvz = 0, stepcounter = 0, stepdetector = 0;
    Boolean switchState = false;
    public double audioamp = 0, audioamphistory = 30;
    SoundMeter soundmeter = new SoundMeter();
    TextView sensorvalue;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    initializeOpenCVDependencies();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    private void initializeOpenCVDependencies(){
        try{
            // Copy the resource into a temp file so OpenCV can load it
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("cascade", Context.MODE_APPEND);
            File mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            // Load the cascade classifier
            cascadeClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            cascadeClassifier.load(mCascadeFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e("OpenCVActivity", "Error loading cascade", e);
        }

        // And we are ready to go
        openCvCameraView.enableView();

    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //creating Folder on device
        setContentView(R.layout.activity_main);


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        openCvCameraView = (CameraBridgeViewBase) findViewById(R.id.javaCameraView);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        flag = displayGpsStatus();


            locationListener = new MyLocationListener();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);

        sensorvalue = (TextView) findViewById(R.id.textView2);
        final TextView facedetection = (TextView) findViewById(R.id.textView);

        requestAudioPermissions();
        //soundmeter.start();


        if (cascadeClassifier == null){
            Log.e("OpenCVActivity", "franklinnotload");
        }

        openCvCameraView.setCameraIndex(1);
        openCvCameraView.setCvCameraViewListener(new CvCameraViewListener() {
            @Override
            public void onCameraViewStarted(int width, int height) {
                grayscaleImage = new Mat(height, width, CvType.CV_8UC4);

                Log.d("Franklincheck", "Franklincheck111");
                // The faces will be a 20% of the height of the screen
                absoluteFaceSize = (int) (height * 0.2);
            }

            @Override
            public void onCameraViewStopped() {

            }

            @Override
            public Mat onCameraFrame(Mat inputFrame) {
                Imgproc.cvtColor(inputFrame, grayscaleImage, Imgproc.COLOR_RGBA2RGB);

                MatOfRect faces = new MatOfRect();

                // Use the classifier to detect faces
                if (cascadeClassifier != null) {
                    cascadeClassifier.detectMultiScale(grayscaleImage, faces, 1.1, 2, 2,
                            new Size(absoluteFaceSize, absoluteFaceSize), new Size());
                }

                // If there are any faces found, draw a rectangle around it
                Rect[] facesArray = faces.toArray();

                Log.d("Franklinface", faces.toString());

                if (faces.toArray().length > 0){
                    facedetection.setText("Visual Cue");
                }
                else if (faces.toArray().length == 0){
                    facedetection.setText("No visual cue");
                }
                    //Core.rectangle(inputFrame, facesArray[i].tl(), facesArray[i].br(), new Scalar(0, 255, 0, 255), 3);

                return inputFrame;
            }
        });

        Spinner spinner = (Spinner) findViewById(R.id.spinner3);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                stationname = parent.getItemAtPosition(position).toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        List<String> categories = new ArrayList<String>();
        categories.add("Finch");
        categories.add("North York Centre");
        categories.add("Sheppard-Yonge");
        categories.add("York Mills");
        categories.add("Lawrence");
        categories.add("Eglinton");
        categories.add("Davisville");
        categories.add("St.Clair");
        categories.add("Summerhill");
        categories.add("Rosedale");
        categories.add("Bloor-Yonge");
        categories.add("Wellesley");
        categories.add("College");
        categories.add("Dundas");
        categories.add("Queen");
        categories.add("King");
        categories.add("Union");
        categories.add("St.Andrew");
        categories.add("Osgoode");
        categories.add("St.Patrick");
        categories.add("Queen's Park");

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, categories);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(dataAdapter);
        String extStorageDirectory = Environment.getExternalStorageDirectory().getAbsolutePath();

        final RadioGroup radiophonepositiongroup = (RadioGroup) findViewById(R.id.radioGroup);
        final RadioButton inbag = (RadioButton)findViewById(R.id.inbag);
        final RadioButton inhand = (RadioButton)findViewById(R.id.inhand);
        final RadioButton inpocket = (RadioButton)findViewById(R.id.inpocket);

        inbag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                personstate = "inbag";
            }
        });

        inhand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                personstate = "inhand";
            }
        });

        inpocket.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                personstate = "inpocket";
            }
        });

        final RadioGroup radiopositiongroup = (RadioGroup) findViewById(R.id.radioGroup2);
        final RadioButton instation = (RadioButton)findViewById(R.id.instation);
        final RadioButton instationexiting = (RadioButton)findViewById(R.id.instationexiting);
        final RadioButton walkingdowntosubway = (RadioButton)findViewById(R.id.walkingdowntosubway);
        final RadioButton stopbetweenstation = (RadioButton)findViewById(R.id.stopbetweenstation);
        final RadioButton runninginhalfway = (RadioButton)findViewById(R.id.runninginhalfway);

        instation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                phonestate = "instation";
            }
        });

        instationexiting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                phonestate = "instationexiting";
            }
        });

        walkingdowntosubway.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                phonestate = "walkingdowntosubway";
            }
        });

        stopbetweenstation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                phonestate = "stopbetweenstation";
            }
        });

        runninginhalfway.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                phonestate = "runninginhalfway";
            }
        });

        final Switch startrecording = (Switch) findViewById(R.id.startrecording);
        //Boolean switchState = startrecording.isChecked();

        startrecording.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                switchState = isChecked;
            }
        });

        myData = new File(extStorageDirectory + "/"+ file);
        try{
            if(!myData.exists()){
                myData.createNewFile();
            }
        }catch(IOException ioExp){
            Log.d("AndroidSensorList::", "error in file creation");
        }

        myDataCollection = new File(extStorageDirectory + "/"+ DATA_COLLECTION_FILE);
        try{
            if(!myDataCollection.exists()){
                myDataCollection.createNewFile();
            }
        }catch(IOException ioExp){
            Log.d("AndroidSensorList::", "error in file creation");
        }

        try{
            OutputStream fo = new FileOutputStream(myDataCollection,true);
            String title = "time,barometer,magnetometer,linear_acc,linear_gyro,accx,accy,accz,gyrox,gyroy,gyroz,GRVx,GRVy,GRVz,GeoRVx,GeoRVy,GeoRVz,rvx,rvy,rvz,light_sensor,stepcounter,soundlevel,latitude,longitude,phone,person,station" + "\n";

            fo.write(title.getBytes());
            fo.close();
        }catch(IOException e){
            Log.e("AndroidSensorList::","File write failed: " + e.toString());
        }



        //retrieving the list of sensors available on a device
        sensorManager
                = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        listSensor
                = sensorManager.getSensorList(Sensor.TYPE_ALL);
        mAccelerometer=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAmbientTemp=sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        mGameRotationVect=sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        mGeomagneticRotationVecor=sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        mGravity=sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        mGyroscope=sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mGyroUncal=sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
        mLight=sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mLinearAcceleration=sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mMagFld=sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mMagFldUncal=sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);
        mOrientation=sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        mPressure=sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        mProximity=sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mRelativeHumid=sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        mRotVect=sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mSignificantMotion=sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
        mStepCntr=sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        mStepDetect=sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        mTemp=sensorManager.getDefaultSensor(Sensor.TYPE_TEMPERATURE);

        //Creating List view and writing data to a file

        List<String> listSensorType = new ArrayList<String>();
        for(int i=0; i<listSensor.size(); i++){
            System.out.println("Inside list sensors:::::::");
            listSensorType.add((i+1)+" "+listSensor.get(i).getName());
            String sensorNames = listSensor.get(i).getName();
            System.out.println(listSensor.get(i).getType());
            //mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(listSensor.get(i).getType()), SensorManager.SENSOR_DELAY_NORMAL);
            writeToFile(listSensor.get(i).getName().getBytes(),sensorNames );
        }

    }


    private Boolean displayGpsStatus() {
        ContentResolver contentResolver = getBaseContext()
                .getContentResolver();
        boolean gpsStatus = Settings.Secure
                .isLocationProviderEnabled(contentResolver,
                        LocationManager.GPS_PROVIDER);
        if (gpsStatus) {
            return true;

        } else {
            return false;
        }
    }

    private class MyLocationListener implements LocationListener {
        @Override
        public void onLocationChanged(Location loc) {
            longitude = loc.getLongitude();
            latitude = loc.getLatitude();
        }

        @Override
        public void onProviderDisabled(String provider) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onProviderEnabled(String provider) {
            // TODO Auto-generated method stub
        }

        @Override
        public void onStatusChanged(String provider,
                                    int status, Bundle extras) {
            // TODO Auto-generated method stub
        }
    }


    private void writeToFile(byte[] data, String sensorNames) {
        System.out.println("----------------Inside writeToFile-----------------");

        try {
            String comma = "\n";
            byte[] bComma = comma.getBytes();
            OutputStream fo = new FileOutputStream(myData,true);
            //fo.write(bComma);
            fo.write(data);
            fo.close();

        }
        catch (IOException e) {
            Log.e("AndroidSensorList::","File write failed: " + e.toString());
        }

    }


    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this,mAccelerometer,  SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,mAmbientTemp,  SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mGameRotationVect, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,mGeomagneticRotationVecor,  SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mGravity,  SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mGyroUncal, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mLight,  SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mLinearAcceleration, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,mMagFld,  SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,mMagFldUncal,  SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,mOrientation,   SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,mPressure,   SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mRelativeHumid, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,mRotVect,  SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mSignificantMotion, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this,mStepCntr,   SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mStepDetect, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, mTemp, SensorManager.SENSOR_DELAY_NORMAL);
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
    }

    @Override
    protected void onPause() {
        sensorManager.unregisterListener(mSensorListener);
        super.onPause();

    }

    protected void onStop() {
        soundmeter.stop();
        super.onStop();
    }

    private final int MY_PERMISSIONS_RECORD_AUDIO = 1;

    private void requestAudioPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            //When permission is not granted by user, show them message why this permission is needed.
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Please grant permissions to record audio", Toast.LENGTH_LONG).show();

                //Give user option to still opt-in the permissions
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);

            } else {
                // Show user dialog to grant permission to record audio
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        MY_PERMISSIONS_RECORD_AUDIO);
            }
        }
        //If permission is granted, then go ahead recording audio
        else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {

            //Go ahead with recording audio now
            soundmeter.start();
        }
    }

    //Handling callback
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    soundmeter.start();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Permissions Denied to record audio", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub

    }





    @Override
    public void onSensorChanged(SensorEvent event) {
        // TODO Auto-generated method stub
        System.out.println("++++++++++++++++INSIDE onSensorChanged() ++++++++++++++++++++++");
        //System.out.println("sensorName:"+sensorName);
        System.out.println("event.sensor.getName():"+event.sensor.getName());
        //float x,y,z;

        String sensorlist = "";



        if (event.sensor.getName().equals("airpress-bmp380")){
            barometer = event.values[0];
            String sensordata = "LinearAcc: " +  String.valueOf(Math.sqrt(accx*accx + accy * accy + accz * accz) - 9.8) + " Barometer: " + String.valueOf(barometer) + " Magnetometer: " + String.valueOf(magnetometer);
            sensorvalue.setText(sensordata);
        }
        else if (event.sensor.getName().equals("uncalibrated Magnetic Field")){
            magnetometer = event.values[0];
            String sensordata = "LinearAcc: " +  String.valueOf(Math.sqrt(accx*accx + accy * accy + accz * accz) - 9.8) + " Barometer: " + String.valueOf(barometer) + " Magnetometer: " + String.valueOf(magnetometer);
            sensorvalue.setText(sensordata);
        }
        else if (event.sensor.getName().equals("linear Acceleration")){
            linear_acc = event.values[0];
        }
        else if (event.sensor.getName().equals("uncalibrated Gyroscope")){
            linear_gyro = event.values[0];
        }
        else if (event.sensor.getName().equals("accelerometer-lsm6dsm")){
            accx = event.values[0];
            accy = event.values[1];
            accz = event.values[2];
            
        }
        else if (event.sensor.getName().equals("gyroscope-lsm6dsm")){
            gyrox = event.values[0];
            gyroy = event.values[1];
            gyroz = event.values[2];
        }
        else if (event.sensor.getName().equals("game Rotation Vector")){
            GRVx = event.values[0];
            GRVy = event.values[1];
            GRVz = event.values[2];
        }
        else if (event.sensor.getName().equals("geomagnetic Rotation Vector")){
            GeoRVx = event.values[0];
            GeoRVy = event.values[1];
            GeoRVz = event.values[2];
        }
        else if (event.sensor.getName().equals("rotation Vector")){
            rvx = event.values[0];
            rvy = event.values[1];
            rvz = event.values[2];
        }
        else if (event.sensor.getName().equals("als-apds9253")){
            light_sensor = event.values[0];
        }
        else if (event.sensor.getName().equals("step counter")){
            stepcounter = event.values[0];
        }
        else if (event.sensor.getName().equals("step Detector")){
            stepdetector = event.values[0];
        }

        audioamp = soundmeter.getAmplitude();
        double amplitudeDb = 20 * Math.log10((double)Math.abs(audioamp));

        if (amplitudeDb > 0) {
            audioamphistory = amplitudeDb;
        }

        long time = System.currentTimeMillis();
        Date currentTime = Calendar.getInstance().getTime();

        if (phonestate.equals("instation")) {
            sensorlist = String.valueOf(time) + "," + String.valueOf(barometer) + "," + String.valueOf(magnetometer) + "," + String.valueOf(linear_acc) + "," + String.valueOf(linear_gyro) + "," + String.valueOf(accx) + "," + String.valueOf(accy) + "," + String.valueOf(accz) + "," + String.valueOf(gyrox) + "," + String.valueOf(gyroy) + "," + String.valueOf(gyroz) + "," + String.valueOf(GRVx) + "," + String.valueOf(GRVy) + "," + String.valueOf(GRVz) + "," + String.valueOf(GeoRVx) + "," + String.valueOf(GeoRVy) + "," + String.valueOf(GeoRVz) + "," + String.valueOf(rvx) + "," + String.valueOf(rvy) + "," + String.valueOf(rvz) + "," + String.valueOf(light_sensor) + "," + String.valueOf(stepcounter) + "," + String.valueOf(audioamphistory) + "," + String.valueOf(latitude) +"," + String.valueOf(longitude) + "," + personstate + "," + phonestate + "," + stationname + "\n";
        }
        else{
            sensorlist = String.valueOf(time) + "," + String.valueOf(barometer) + "," + String.valueOf(magnetometer) + "," + String.valueOf(linear_acc) + "," + String.valueOf(linear_gyro) + "," + String.valueOf(accx) + "," + String.valueOf(accy) + "," + String.valueOf(accz) + "," + String.valueOf(gyrox) + "," + String.valueOf(gyroy) + "," + String.valueOf(gyroz) + "," + String.valueOf(GRVx) + "," + String.valueOf(GRVy) + "," + String.valueOf(GRVz) + "," + String.valueOf(GeoRVx) + "," + String.valueOf(GeoRVy) + "," + String.valueOf(GeoRVz) + "," + String.valueOf(rvx) + "," + String.valueOf(rvy) + "," + String.valueOf(rvz) + "," + String.valueOf(light_sensor) + "," + String.valueOf(stepcounter) + "," + String.valueOf(audioamphistory) + "," + String.valueOf(latitude) +"," + String.valueOf(longitude) + "," + personstate + "," + phonestate + "," + "N/A" + "\n";

        }




        byte[] bsensorlist = String.valueOf(sensorlist).getBytes();

        if (switchState == true){
        try{
            //soundmeter.start();
            OutputStream fo = new FileOutputStream(myDataCollection,true);
            fo.write(bsensorlist);
            fo.close();
        }catch(IOException e){
            Log.e("AndroidSensorList::","File write failed: " + e.toString());
        }}

        //x=event.values[0];
        //y=event.values[1];
        //z=event.values[2];

        //writeDataTofile(event.sensor.getName(),x,0,0);

    }

}