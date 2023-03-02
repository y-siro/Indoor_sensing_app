package com.example.testingapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

public class MainActivity extends AppCompatActivity {
    private tempView mView_Draw;
    private long InitTime;
    private int Data_Length = 3 + 4 + 2;
    private boolean Step_Detect;
    private boolean BT_Sensor;
    private boolean BT_Method;
    private double Init_Heading_Direction;
    private double Max_X, Min_X;
    // 00 ~ 02 : A_X, A_Y, A_Z
    // 03 ~ 06 : A_EX, A_EY, A_EZ, ACC
    // 07 ~ 08 : RV_Azimuth, GRV_Azimuth
    private double[] SensorData = new double[Data_Length];
    private float[] RotationMatrix = new float[9];
    private float[] GameRotationMatrix = new float[9];
    private float InitX, InitY;
    private float MaxLeft, MaxRight, MaxTop, MaxBottom;
    private float Initial_Pointer_X, Initial_Pointer_Y;
    private float CoordinatesX, CoordinatesY;
    private float Traveled_Distance1, Traveled_Distance2, Traveled_Distance3;
    private TextView TextView1;
    private ImageView Img_Pointer, Img_BackGround;
    private Vector<KalmanFilter> K_Vector = new Vector<KalmanFilter>(Data_Length);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 변수 초기화
        {
            InitTime = System.currentTimeMillis();
            mView_Draw = new tempView(MainActivity.this, null);
            MaxLeft = 0; MaxRight = 1050; MaxTop = 200; MaxBottom = 1950;
            Traveled_Distance1 = 16f; Traveled_Distance2 = 2f; Traveled_Distance3 = 4f;
            Init_Heading_Direction = Max_X = Min_X = 0;
            Step_Detect = BT_Sensor = BT_Method = false;
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
            SensorManager.registerListener(sensorListener, SensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),      android.hardware.SensorManager.SENSOR_DELAY_FASTEST);
            SensorManager.registerListener(sensorListener, SensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),        android.hardware.SensorManager.SENSOR_DELAY_FASTEST);
            SensorManager.registerListener(sensorListener, SensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),            android.hardware.SensorManager.SENSOR_DELAY_FASTEST);
            SensorManager.registerListener(sensorListener, SensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),       android.hardware.SensorManager.SENSOR_DELAY_FASTEST);
        }
        // 버튼 & 텍스트 & 스위치
        {
            mView_Draw = findViewById(R.id.view);

            Img_BackGround = findViewById(R.id.BackGround);
            Img_Pointer = findViewById(R.id.Pointer);
            Img_Pointer.bringToFront();

            TextView1 = findViewById(R.id.textView1);

            findViewById(R.id.button1).setOnClickListener(new Button.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BT_Sensor = !BT_Sensor;
                }
            });
            findViewById(R.id.button2).setOnClickListener(new Button.OnClickListener() {
                @Override
                public void onClick(View v) {
                    BT_Method = !BT_Method;
                }
            });
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
        // 스텝 카운팅
        {
            Timer StepCountingTimer = new Timer("Step Counting Timer");
            StepCountingTimer.scheduleAtFixedRate(new TimerTask() {
                public void run() { StepDetecting();
                }
            },100, 160);
        }
    }

    // 센서
    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        @Override public void onSensorChanged(SensorEvent event) {
            double[] E_Acc;
            float[] RM_Orientation  = new float[3];
            float[] GRM_Orientation = new float[3];

            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(RotationMatrix, event.values);
            } else if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(GameRotationMatrix, event.values);
            } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                SensorData[0] = event.values[0];
                SensorData[1] = event.values[1];
                SensorData[2] = event.values[2];
            }

            E_Acc = toEarth(RotationMatrix, SensorData[0], SensorData[1], SensorData[2]);
            android.hardware.SensorManager.getOrientation(RotationMatrix, RM_Orientation);
            android.hardware.SensorManager.getOrientation(GameRotationMatrix, GRM_Orientation);

            SensorData[6] = Math.sqrt(SensorData[0] * SensorData[0] + SensorData[1] * SensorData[1] + SensorData[2] * SensorData[2]);
            SensorData[3] = E_Acc[0];
            SensorData[4] = E_Acc[1];
            SensorData[5] = E_Acc[2];
            SensorData[7] = Math.toDegrees(RM_Orientation[0]);
            SensorData[8] = Math.toDegrees(GRM_Orientation[0]);
        }
    };

    private static class tempView extends View {
        private static float OX, OY;
        private Vector<Float> PointVector = new Vector<Float>();
        public tempView(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
        }
        public void getValue(float dx, float dy, float ox, float oy) {
            PointVector.add(dx);
            PointVector.add(dy);
            OX = ox;
            OY = oy;
        }

        @Override protected void onDraw(Canvas canvas) {
            @SuppressLint("DrawAllocation")
            Paint paint = new Paint();
            int LENGTH1 = PointVector.size();
            @SuppressLint("DrawAllocation")
            float[] PointList = new float[LENGTH1];
            paint.setStrokeWidth(4f);
            paint.setColor(Color.BLUE);
            // Galaxy S22+ = X : +30f, Y : -15f, Pixel 2
            // Galaxy   A8 = X : f, Y : f
            for (int i = 0; i < LENGTH1; i++) {
                if ((i % 2) == 0) {
                    PointList[i] = PointVector.get(i) + 30f - OX;
                } else {
                    PointList[i] = PointVector.get(i) - 125f - OY;
                }
            }
            canvas.drawPoints(PointList, paint);
            invalidate();
        };
    }

    // GUI
    private void updateGUI() {
        runOnUiThread(new Runnable() {
            @SuppressLint({"DefaultLocale", "SetTextI18n"})
            @Override
            public void run() {
                double ScreenTime = (System.currentTimeMillis() - InitTime) / 1000.0;
                double Heading_Direction, temp;
                float BetaX, BetaY, DeltaX, DeltaY, SinX, CosY;
                float OffSetX = 0, OffSetY = 0;
                final String text;

                DeltaX = Img_Pointer.getX();
                DeltaY = Img_Pointer.getY();

                // KM filter
                if (CurrentTime() > 500) {
                    for (int i = 0; i < Data_Length; i++) {
                        SensorData[i] = K_Vector.get(i).update(SensorData[i]);
                    }
                }

                // Initializing
                if (CurrentTime() < 500) {
                    temp = SensorData[8];
                    InitX = (Img_BackGround.getRight() - Img_BackGround.getLeft()) / 2f;
                    InitY = (Img_BackGround.getBottom() - Img_BackGround.getTop()) / 2f;
                    CoordinatesX = InitX;
                    CoordinatesY = InitY;
                    if (temp < 0) {
                        temp += 360.0;
                    }
                    Init_Heading_Direction = temp;
                }

                // SensorData calibration
                if (SensorData[7] < 0) { SensorData[7] += 360.0; }
                if (SensorData[8] < 0) { SensorData[8] += 360.0; }

                // Determine heading direction
                if (!BT_Sensor) { Heading_Direction = SensorData[7]; }
                else            { Heading_Direction = SensorData[8] - Init_Heading_Direction; }

                // Get prior coordinates
                if (BT_Method) { BetaX = CoordinatesX;       BetaY = CoordinatesY; }
                else           { BetaX = Img_Pointer.getX(); BetaY = Img_Pointer.getY(); }

                // Calibrate azimuth from game rotation vector
                if (Heading_Direction < 0) { Heading_Direction += 360.0; }

                // Calculate angle
                SinX = Math.abs((float)Math.sin(Heading_Direction));
                CosY = Math.abs((float)Math.cos(Heading_Direction));

                // Set pointer direction
                Img_Pointer.setRotation((float)Heading_Direction);

                text = "Time : " + String.format("%.0f", ScreenTime) +
                     "\nHeading : " + String.format("%.4f", Heading_Direction);

                // Calculate coordinates
                {
                    if (BT_Method) {
                        if (Heading_Direction >= 0 && Heading_Direction < 11) {
                            DeltaX = BetaX + (SinX * Traveled_Distance2);
                            DeltaY = BetaY - (CosY * Traveled_Distance1);
                        } else if (Heading_Direction >= 11 && Heading_Direction < 79) {
                            DeltaX = BetaX + (SinX * Traveled_Distance1);
                            DeltaY = BetaY - (CosY * Traveled_Distance1);
                        } else if (Heading_Direction >= 79 && Heading_Direction < 90) {
                            DeltaX = BetaX + (SinX * Traveled_Distance1);
                            DeltaY = BetaY - (CosY * Traveled_Distance2);
                        }

                        else if (Heading_Direction >= 90 && Heading_Direction < 101) {
                            DeltaX = BetaX + (SinX * Traveled_Distance1);
                            DeltaY = BetaY + (CosY * Traveled_Distance2);
                        } else if (Heading_Direction >= 101 && Heading_Direction < 169) {
                            DeltaX = BetaX + (SinX * Traveled_Distance1);
                            DeltaY = BetaY + (CosY * Traveled_Distance1);
                        } else if (Heading_Direction >= 169 && Heading_Direction < 180) {
                            DeltaX = BetaX + (SinX * Traveled_Distance2);
                            DeltaY = BetaY + (CosY * Traveled_Distance1);
                        }

                        else if (Heading_Direction >= 180 && Heading_Direction < 191) {
                            DeltaX = BetaX - (SinX * Traveled_Distance2);
                            DeltaY = BetaY + (CosY * Traveled_Distance1);
                        } else if (Heading_Direction >= 191 && Heading_Direction < 259) {
                            DeltaX = BetaX - (SinX * Traveled_Distance1);
                            DeltaY = BetaY + (CosY * Traveled_Distance1);
                        } else if (Heading_Direction >= 259 && Heading_Direction < 270) {
                            DeltaX = BetaX - (SinX * Traveled_Distance1);
                            DeltaY = BetaY + (CosY * Traveled_Distance2);
                        }

                        else if (Heading_Direction >= 270 && Heading_Direction < 281) {
                            DeltaX = BetaX - (SinX * Traveled_Distance1);
                            DeltaY = BetaY - (CosY * Traveled_Distance2);
                        } else if (Heading_Direction >= 281 && Heading_Direction < 349) {
                            DeltaX = BetaX - (SinX * Traveled_Distance1);
                            DeltaY = BetaY - (CosY * Traveled_Distance1);
                        } else if (Heading_Direction >= 349 && Heading_Direction < 360) {
                            DeltaX = BetaX - (SinX * Traveled_Distance2);
                            DeltaY = BetaY - (CosY * Traveled_Distance1);
                        }

                        // Checking coordinates before set coordinates
                        if (DeltaX <= MaxLeft || DeltaX >= MaxRight) {
                            TextView1.setText(text + "\nX is out of Bound!");
                            DeltaX = BetaX;
                            DeltaY = BetaY;
                        }
                        else if (DeltaY <= MaxTop || DeltaY >= MaxBottom) {
                            TextView1.setText(text + "\nY is out of Bound!");
                            DeltaX = BetaX;
                            DeltaY = BetaY;
                        }

                        OffSetX = DeltaX - InitX;
                        OffSetY = DeltaY - InitY;

                        // Fix pointer position
                        Img_Pointer.setX(InitX);
                        Img_Pointer.setY(InitY);

                        // Set coordinates
                        setCoordinates(DeltaX, DeltaY, OffSetX, OffSetY);
                    }
                    else {
                        if (Heading_Direction >= 0 && Heading_Direction < 11) {
                            DeltaX = BetaX + (SinX * Traveled_Distance2);
                            DeltaY = BetaY - (CosY * Traveled_Distance1);
                        } else if (Heading_Direction >= 11 && Heading_Direction < 79) {
                            DeltaX = BetaX + (SinX * Traveled_Distance1);
                            DeltaY = BetaY - (CosY * Traveled_Distance1);
                        } else if (Heading_Direction >= 79 && Heading_Direction < 90) {
                            DeltaX = BetaX + (SinX * Traveled_Distance1);
                            DeltaY = BetaY - (CosY * Traveled_Distance2);
                        }

                        else if (Heading_Direction >= 90 && Heading_Direction < 101) {
                            DeltaX = BetaX + (SinX * Traveled_Distance1);
                            DeltaY = BetaY + (CosY * Traveled_Distance2);
                        } else if (Heading_Direction >= 101 && Heading_Direction < 169) {
                            DeltaX = BetaX + (SinX * Traveled_Distance1);
                            DeltaY = BetaY + (CosY * Traveled_Distance1);
                        } else if (Heading_Direction >= 169 && Heading_Direction < 180) {
                            DeltaX = BetaX + (SinX * Traveled_Distance2);
                            DeltaY = BetaY + (CosY * Traveled_Distance1);
                        }

                        else if (Heading_Direction >= 180 && Heading_Direction < 191) {
                            DeltaX = BetaX - (SinX * Traveled_Distance2);
                            DeltaY = BetaY + (CosY * Traveled_Distance1);
                        } else if (Heading_Direction >= 191 && Heading_Direction < 259) {
                            DeltaX = BetaX - (SinX * Traveled_Distance1);
                            DeltaY = BetaY + (CosY * Traveled_Distance1);
                        } else if (Heading_Direction >= 259 && Heading_Direction < 270) {
                            DeltaX = BetaX - (SinX * Traveled_Distance1);
                            DeltaY = BetaY + (CosY * Traveled_Distance2);
                        }

                        else if (Heading_Direction >= 270 && Heading_Direction < 281) {
                            DeltaX = BetaX - (SinX * Traveled_Distance1);
                            DeltaY = BetaY - (CosY * Traveled_Distance2);
                        } else if (Heading_Direction >= 281 && Heading_Direction < 349) {
                            DeltaX = BetaX - (SinX * Traveled_Distance1);
                            DeltaY = BetaY - (CosY * Traveled_Distance1);
                        } else if (Heading_Direction >= 349 && Heading_Direction < 360) {
                            DeltaX = BetaX - (SinX * Traveled_Distance2);
                            DeltaY = BetaY - (CosY * Traveled_Distance1);
                        }

                        // Checking coordinates before set coordinates
                        if (DeltaX <= MaxLeft || DeltaX >= MaxRight) {
                            TextView1.setText(text + "\nX is out of Bound!");
                            DeltaX = BetaX;
                            DeltaY = BetaY;
                        }
                        else if (DeltaY <= MaxTop || DeltaY >= MaxBottom) {
                            TextView1.setText(text + "\nY is out of Bound!");
                            DeltaX = BetaX;
                            DeltaY = BetaY;
                        }

                        // Set coordinates
                        setCoordinates(DeltaX, DeltaY, OffSetX, OffSetY);
                    }
                }

                // Set screen text
                TextView1.setText(text);
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
    private void setCoordinates(float Dx, float Dy, float Ox, float Oy) {
        if (Step_Detect) {
            if(BT_Method) {
                mView_Draw.getValue(Dx, Dy, Ox, Oy);
                CoordinatesX = Dx;
                CoordinatesY = Dy;
            } else {
                mView_Draw.getValue(Dx, Dy, Ox, Oy);
                Img_Pointer.setX(Dx);
                Img_Pointer.setY(Dy);
            }
            mView_Draw.invalidate();
            Step_Detect = false;
        }
    }
    private void StepDetecting() {
        if (SensorData[5] > 10.9f) { Max_X = SensorData[5]; }
        else { Max_X = 0; }

        if (SensorData[5] < 9.1f) {  Min_X = SensorData[5]; }
        else { Min_X = 0; }

        if ( (Max_X - Min_X) > 1.8f) {
            Step_Detect = true;
        }
    }
    private long CurrentTime() {
        return System.currentTimeMillis() - InitTime;
    }
}