package com.example.testingapp;

public class Temp2 {
    // GNSS + GPS 기능 합친거
    /*
package com.example.testingapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;


public class MainActivity extends AppCompatActivity implements LocationListener {
    private LocationManager mLocationManager;
    private long Screen_initTime;
    private boolean status;
    private int positiveGpsCount;
    private int Data_Length = 3 + 4 + 2 + 2 + 4;
    private double lat;
    private double lng;
    private double Speed;
    // 00 ~ 02 : A_X, A_Y, A_Z
    // 03 ~ 06 : A_EX, A_EY, A_EZ, ACC
    // 07 ~ 08 : RV_Azimuth, GRV_Azimuth
    // 09 ~ 10 : Lat, Lng
    // 11 ~ 14 : Step_Count, Step_Detect, SatNum, Speed
    private double[] SensorData = new double[Data_Length];
    private double[] G = new double[4];
    private float[] RotationMatrix = new float[9];
    private float[] GameRotationMatrix = new float[9];
    private float mCnrThreshold = 32;
    private TextView TextView1, TextView2, TextView3;
    private ImageView img;
    private Vector<KalmanFilter> K_Vector = new Vector<KalmanFilter>(Data_Length);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 클래스 및 변수 초기화
        {
            status = false;
            Speed = 0;
            Screen_initTime = System.currentTimeMillis();
            for (int i = 0; i < Data_Length; i++) {
                SensorData[i] = 0;
                K_Vector.add(i, new KalmanFilter(0.0f));
            }
        }
        // 센서 리스너 등록
        {
            SensorManager SensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            assert SensorManager != null;
            SensorManager.registerListener(sensorListener, SensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), android.hardware.SensorManager.SENSOR_DELAY_FASTEST);
            SensorManager.registerListener(sensorListener, SensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), android.hardware.SensorManager.SENSOR_DELAY_FASTEST);
            SensorManager.registerListener(sensorListener, SensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), android.hardware.SensorManager.SENSOR_DELAY_FASTEST);
            SensorManager.registerListener(sensorListener, SensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), android.hardware.SensorManager.SENSOR_DELAY_FASTEST);
            SensorManager.registerListener(sensorListener, SensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), android.hardware.SensorManager.SENSOR_DELAY_FASTEST);
            SensorManager.registerListener(sensorListener, SensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), android.hardware.SensorManager.SENSOR_DELAY_FASTEST);
            SensorManager.registerListener(sensorListener, SensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR), android.hardware.SensorManager.SENSOR_DELAY_FASTEST);
        }
        // 버튼 & 텍스트 & 스위치
        {
            img = findViewById(R.id.Pointer);
            TextView1 = findViewById(R.id.textView1);
            TextView2 = findViewById(R.id.textView2);
            TextView3 = findViewById(R.id.textView3);
        }
        // GPS
        {
            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            Location lastKnownLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnownLocation != null) {
                lat = lastKnownLocation.getLatitude();
                lng = lastKnownLocation.getLongitude();
            } else {
                lat = lng = 0;
            }
            registerGNSSCallback();
        }
        // 화면 업데이트 주기 10 = 10ms
        {
            Timer updateTimer = new Timer("update GUI timer");
            updateTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    updateGUI();
                }
            }, 0, 10);
        }
        // Speed
        {
            Timer SpeedTimer = new Timer("Speed timer");
            SpeedTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    Speed = getSpeed();
                }
            }, 1000, 100);
        }
    }

    // 센서
    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        @Override public void onSensorChanged(SensorEvent event) {
            double[] E_Acc = new double[3];
            float[] RM_Orientation = new float[3];
            float[] GRM_Orientation = new float[3];

            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(RotationMatrix, event.values);
            } else if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(GameRotationMatrix, event.values);
            } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                SensorData[0] = event.values[0];
                SensorData[1] = event.values[1];
                SensorData[2] = event.values[2];
            } else if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                SensorData[11] = event.values[0];
            } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                if (event.values[0] == 1.0f) {
                    SensorData[12] += event.values[0];
                }
            }

            E_Acc = toEarth(RotationMatrix, SensorData[0], SensorData[1], SensorData[2]);
            android.hardware.SensorManager.getOrientation(RotationMatrix, RM_Orientation);
            android.hardware.SensorManager.getOrientation(GameRotationMatrix, GRM_Orientation);

            SensorData[6] = Math.sqrt(SensorData[0] * SensorData[0] + SensorData[1] * SensorData[1] + SensorData[2] * SensorData[2]);
            SensorData[3] = E_Acc[0];
            SensorData[4] = E_Acc[1];
            SensorData[5] = E_Acc[2];
            SensorData[7] = Math.toDegrees(RM_Orientation[0]);
            Heading_Direction = Math.toDegrees(GRM_Orientation[0]);
        }
    };

    // GUI
    private void updateGUI() {
        runOnUiThread(new Runnable() {
            @SuppressLint({"DefaultLocale", "SetTextI18n"})
            @Override
            public void run() {
                double ScreenTime = (System.currentTimeMillis() - Screen_initTime) / 1000.0;

                SensorData[9]  = lat;
                SensorData[10] = lng;
                SensorData[13] = positiveGpsCount;
                SensorData[14] = Speed;

                if (SensorData[7] < 0) {
                    SensorData[7] += 360.0;
                }
                if (SensorData[8] < 0) {
                    SensorData[8] += 360.0;
                }

                // 칼만 필터
                for (int i = 0; i < 3 + 4 + 2; i++) {
                    SensorData[i] = K_Vector.get(i).update(SensorData[i]);
                }

                TextView1.setText("Time : "             + String.format("%.0f", ScreenTime));
                TextView2.setText("Moving Speed : "     + String.format("%.2f", SensorData[14]) +
                                "\nSatellite Number : " + String.format("%.0f", SensorData[13]) +
                                "\nStep Detect : "      + String.format("%.0f", SensorData[12]));
                TextView3.setText("RM_AZ : "            + String.format("%.2f", SensorData[7]) +
                                "\nGM_AZ : "            + String.format("%.2f", SensorData[8]) +
                                "\nlat : "              + String.format("%.6f", SensorData[9]) +
                                "\nlng : "              + String.format("%.6f", SensorData[10]));

                img.setRotation((float) SensorData[8]);
            }
        });
    }

    // 전역 함수
    private double[] toEarth(float[] RotationMatrix, double sensor0, double sensor1, double sensor2) {
        double[] Temp = new double[3];
        Temp[0] = RotationMatrix[0] * sensor0 + RotationMatrix[1] * sensor1 + RotationMatrix[2] * sensor2;  // x 축
        Temp[1] = RotationMatrix[3] * sensor0 + RotationMatrix[4] * sensor1 + RotationMatrix[5] * sensor2;  // y 축
        Temp[2] = RotationMatrix[6] * sensor0 + RotationMatrix[7] * sensor1 + RotationMatrix[8] * sensor2;  // z 축
        return Temp;
    }
    private double getDistance(double P1_latitude, double P1_longitude, double P2_latitude, double P2_longitude) {
        if ((P1_latitude == P2_latitude) && (P1_longitude == P2_longitude)) {
            return 0;
        }
        double e10 = P1_latitude * Math.PI / 180;
        double e11 = P1_longitude * Math.PI / 180;
        double e12 = P2_latitude * Math.PI / 180;
        double e13 = P2_longitude * Math.PI / 180;

        // 타원체 GRS80
        double c16 = 6356752.314140910;
        double c15 = 6378137.000000000;
        double c17 = 0.0033528107;
        double f15 = c17 + c17 * c17;
        double f16 = f15 / 2;
        double f17 = c17 * c17 / 2;
        double f18 = c17 * c17 / 8;
        double f19 = c17 * c17 / 16;
        double c18 = e13 - e11;
        double c20 = (1 - c17) * Math.tan(e10);
        double c21 = Math.atan(c20);
        double c22 = Math.sin(c21);
        double c23 = Math.cos(c21);
        double c24 = (1 - c17) * Math.tan(e12);
        double c25 = Math.atan(c24);
        double c26 = Math.sin(c25);
        double c27 = Math.cos(c25);
        double c29 = c18;
        double c31 = (c27 * Math.sin(c29) * c27 * Math.sin(c29))
                + (c23 * c26 - c22 * c27 * Math.cos(c29))
                * (c23 * c26 - c22 * c27 * Math.cos(c29));
        double c33 = (c22 * c26) + (c23 * c27 * Math.cos(c29));
        double c35 = Math.sqrt(c31) / c33;
        double c36 = Math.atan(c35);
        double c38 = 0;
        if (c31 == 0) {
            c38 = 0;
        } else {
            c38 = c23 * c27 * Math.sin(c29) / Math.sqrt(c31);
        }
        double c40 = 0;
        if ((Math.cos(Math.asin(c38)) * Math.cos(Math.asin(c38))) == 0) {
            c40 = 0;
        } else {
            c40 = c33 - 2 * c22 * c26
                    / (Math.cos(Math.asin(c38)) * Math.cos(Math.asin(c38)));
        }
        double c41 = Math.cos(Math.asin(c38)) * Math.cos(Math.asin(c38))
                * (c15 * c15 - c16 * c16) / (c16 * c16);
        double c43 = 1 + c41 / 16384
                * (4096 + c41 * (-768 + c41 * (320 - 175 * c41)));
        double c45 = c41 / 1024 * (256 + c41 * (-128 + c41 * (74 - 47 * c41)));
        double c47 = c45
                * Math.sqrt(c31)
                * (c40 + c45
                / 4
                * (c33 * (-1 + 2 * c40 * c40) - c45 / 6 * c40
                * (-3 + 4 * c31) * (-3 + 4 * c40 * c40)));
        double c50 = c17
                / 16
                * Math.cos(Math.asin(c38))
                * Math.cos(Math.asin(c38))
                * (4 + c17
                * (4 - 3 * Math.cos(Math.asin(c38))
                * Math.cos(Math.asin(c38))));
        double c52 = c18
                + (1 - c50)
                * c17
                * c38
                * (Math.acos(c33) + c50 * Math.sin(Math.acos(c33))
                * (c40 + c50 * c33 * (-1 + 2 * c40 * c40)));
        double c54 = c16 * c43 * (Math.atan(c35) - c47);
        // return distance in meter
        return c54;
    }
    private double getSpeed() {
        double result = 0.0f;
        if (!status) {
            G[0] = lat;
            G[1] = lng;
            status = true;
        } else {
            G[2] = lat;
            G[3] = lng;
            if ((G[0] == G[2]) && (G[1] == G[3])) {
                result = 0;
            } else {
                result = getDistance(G[0], G[1], G[2], G[3]);
            }
            status = false;
        }
        return result/100;
    }
    private void registerGNSSCallback() {
        GnssStatus.Callback gnssStatusCallback = new GnssStatus.Callback() {
            @Override public void onStopped() { }
            @Override public void onStarted() { }
            @SuppressLint("DefaultLocale")
            public void onSatelliteStatusChanged(GnssStatus status) {
                int totalSatellite = status.getSatelliteCount();
                StringBuilder strength = new StringBuilder("GPS strength [" + totalSatellite + "] threshold:" + mCnrThreshold + "\n");
                float sum = 0;
                int count = 0;
                positiveGpsCount = 0;
                for (int i = 0; i < totalSatellite; i++) {
                    if (status.getConstellationType(i) == GnssStatus.CONSTELLATION_GPS) {
                        if (status.getCn0DbHz(i) > 25) {       //GPS 정확도
                            positiveGpsCount++;
                            strength.append(String.format("%.2f", status.getCn0DbHz(i))).append(" ");
                        }
                    }
                    sum += status.getCn0DbHz(i);
                    count++;
                }
                //CnrThreshold 최초 한 번만 결정
                mCnrThreshold = sum / count;
            }
        };

        GnssMeasurementsEvent.Callback gnssMeasurementCallback = new GnssMeasurementsEvent.Callback() {
            @SuppressLint("DefaultLocale")
            @Override
            public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
                super.onGnssMeasurementsReceived(eventArgs);
                StringBuilder strength = new StringBuilder();
                if (eventArgs.getMeasurements().size() > 0) {
                    for (GnssMeasurement gnssMeasurement : eventArgs.getMeasurements()) {
                        if (gnssMeasurement.getCn0DbHz() > 25)
                            strength.append(String.format("%.2f", gnssMeasurement.getCn0DbHz())).append(" ");
                    }
                }
            }
        };

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1002);
        } else {
//                mLocationManager.registerGnssMeasurementsCallback(gnssMeasurementCallback, null);
            mLocationManager.registerGnssStatusCallback(gnssStatusCallback, null);
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
        }
    }


    @Override public void onLocationChanged(Location location) {
        if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
            lat = location.getLatitude();
            lng = location.getLongitude();
        }
    }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {

    }
    @Override public void onProviderEnabled(String provider) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
    }
    @Override public void onProviderDisabled(String provider) {

    }
}
    */
}
