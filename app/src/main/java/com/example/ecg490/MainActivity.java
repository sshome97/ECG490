package com.example.ecg490;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.auth.FirebaseAuth;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LineGraphSeries<DataPoint> mSeries1;
    //private Runnable mTimer1;
    Double samples[] = new Double[250];
    static Double plotData[] = new Double[1000];
    public static int currentTimeSec = 0;
    public static int min = 0;
    public static int max = 40;
   //public static int max = 1000;
    public static int flag = 0;

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;
    private ScanSettings settings;

    private Button disconnectButton;
    private Button saveButton;
    private Spinner mySpinner;
    private TextView Interval;
    private TextView heartRate;

    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter btAdapter = null;
    private ListView BLEListView;
    private ArrayAdapter<String> listAdapter;
    private Button connectButton;
    private GraphView graph;
    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    private BluetoothGattCharacteristic characteristicRX;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private Double[] lead1 = new Double[250];
    private Double[] lead2 = new Double[250];
    private Double[] lead3 = new Double[250];
    private Double[] leadV1 = new Double[250];
    private Double[] leadV2 = new Double[250];
    //Double data[] = new Double[250];
    int position = 0;
    int whichLead = 0;

    double dataAvgValue = 0;
    double lastAvgValue = 0;
    double rPeakLocalMax = 0;
    double rPeakMaxTemp = 0;
    double prevRPeak = 0;
    int locationLocal = 0;
    int locationMax = 0;
    int locationPrev = 0;
    double beatRate = 0;
    double RRInterval = 0;
    double prevRRInterval = 0;
    boolean firstRPeak = false;

    double RpeakV2 = 0;
    double dataAvgValueV2 = 0;
    double SpeakV2 = 0;

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Really Exit?")
                .setMessage("Are you sure you want to exit?")
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface arg0, int arg1) {
                        MainActivity.super.onBackPressed();
                        mService.disconnect();

                    }
                }).create().show();

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        BLEListView = (ListView) findViewById(R.id.BLEDevices);
        listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
        BLEListView.setAdapter(listAdapter);
        BLEListView.setDivider(null);
        connectButton = (Button) findViewById(R.id.connectButton);

        graph = (GraphView) findViewById(R.id.graph);
        graph.setVisibility(View.INVISIBLE);

        //enable scaling and scrolling

        graph.getViewport().setScrollable(true);
        graph.getViewport().setScrollableY(true);
        graph.getViewport().setScalable(true);
        graph.getViewport().setScalableY(true);

        //other parameters
        graph.setKeepScreenOn(true);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time");
        graph.getGridLabelRenderer().setVerticalAxisTitle("mV");
        graph.getGridLabelRenderer().setHighlightZeroLines(false);
        graph.getGridLabelRenderer().setPadding(40);
        graph.getGridLabelRenderer().setGridColor(Color.argb(255,242,189,205));

        disconnectButton = (Button) findViewById(R.id.disconnectButton);
        disconnectButton.setVisibility(View.INVISIBLE);

        saveButton = (Button) findViewById(R.id.saveButton);
        saveButton.setVisibility(View.INVISIBLE);

        Interval = (TextView) findViewById(R.id.intervalTextView);
        Interval.setVisibility(View.INVISIBLE);
        heartRate = (TextView) findViewById(R.id.heartRateTextView);
        heartRate.setVisibility(View.INVISIBLE);
        mySpinner = (Spinner) findViewById(R.id.spinner1);

        ArrayAdapter<String> myAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.leads));
        myAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mySpinner.setAdapter(myAdapter);
        mySpinner.setVisibility(View.INVISIBLE);

        service_init();
        CheckBlueToothState(); // Make sure bluetooth is enabled, if not, prompt the user to enable it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Make sure we have access coarse location enabled, if not, prompt the user to enable it
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect peripherals.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!btAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                } else {
                    if (connectButton.getText().equals("Connect")) {
                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    }
                }
            }
        });
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.disconnect();
                graph.removeAllSeries();
                graph.setVisibility(View.INVISIBLE);
                disconnectButton.setVisibility(View.INVISIBLE);
                saveButton.setVisibility(View.INVISIBLE);
                mySpinner.setVisibility(View.INVISIBLE);
                Interval.setVisibility(View.INVISIBLE);
                heartRate.setVisibility(View.INVISIBLE);
                connectButton.setVisibility(View.VISIBLE);

            }
        });
        mySpinner.setSelection(0,false);
        mySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position){
                    case 0:
                        graph.removeAllSeries();
                        whichLead = 0;
                        break;
                    case 1:
                        graph.removeAllSeries();
                        whichLead = 1;
                        break;
                    case 2:
                        graph.removeAllSeries();
                        whichLead = 2;
                        break;
                    case 3:
                        graph.removeAllSeries();
                        whichLead = 3;
                        break;
                    case 4:
                        graph.removeAllSeries();
                        whichLead = 4;
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.Logout:
                mService.disconnect();
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(MainActivity.this, SignInActivity.class);
                startActivity(intent);
                Toast.makeText(this, "You have been logged out", Toast.LENGTH_SHORT).show();
                break;
        }

        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy()");

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(UARTStatusChangeReceiver);
        } catch (Exception ignore) {
            Log.e(TAG, ignore.toString());
        }
        unbindService(mServiceConnection);
        mService.stopSelf();
        mService = null;
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        mService.disconnect();

    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        if (btAdapter == null || !btAdapter.isEnabled())
            CheckBlueToothState();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //added supercall
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_SELECT_DEVICE:
                //When the DeviceListActivity return, with the selected device address
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String deviceAddress = data.getStringExtra(BluetoothDevice.EXTRA_DEVICE);
                    mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress); //address
                    Log.d(TAG, "... onActivityResultdevice.address==" + mDevice + "mserviceValue" + mService);
                    mService.connectToBleDevice(mDevice);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "Bluetooth has turned on ", Toast.LENGTH_SHORT).show();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(this, "Problem in BT Turning ON ", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                Log.e(TAG, "Wrong request code");
                break;
        }
    }

    public void CheckBlueToothState() {
        if (btAdapter == null || !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
            }
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        public void onServiceDisconnected(ComponentName classname) {
            //mService.disconnect(mDevice);
            mService = null;
        }
    };
    private Handler mHandler = new Handler() {
        @Override
        //Handler events that received from UART service
        public void handleMessage(Message msg) {
        }
    };

    public final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            String action = intent.getAction();

            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                Log.d(TAG, "UART_CONNECT_MSG");
                Toast.makeText(MainActivity.this, "Successfully Connected", Toast.LENGTH_SHORT).show();

                mState = UART_PROFILE_CONNECTED;

                connectButton.setVisibility(View.INVISIBLE);
                graph.setVisibility(View.VISIBLE);
                saveButton.setVisibility(View.VISIBLE);
                disconnectButton.setVisibility(View.VISIBLE);
                mySpinner.setVisibility(View.VISIBLE);
                Interval.setVisibility(View.VISIBLE);
                heartRate.setVisibility(View.VISIBLE);
            }
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {

                Log.d(TAG, "UART_DISCONNECT_MSG");
                mState = UART_PROFILE_DISCONNECTED;

                mService.close();
            }
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {

                Log.d(TAG, "call displaygatt");
                findGattServices(mService.getSupportedGattServices());
            }

            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {

                String dataString = intent.getStringExtra(UartService.EXTRA_DATA);
                displayGraph(dataString);
                }
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)) {
                    showMessage("Device doesn't support UART. Disconnecting");
                    //stopCommand();
                    mService.disconnect();
                }
            }
        };

    private void displayGraph(String dataString) {
        //GraphView graph = (GraphView) findViewById(R.id.graph);
        //graph.setVisibility(View.VISIBLE);

//        GraphView graph = (GraphView) findViewById(R.id.graph);
        //graph.setVisibility(View.INVISIBLE);
        mSeries1 = new LineGraphSeries<>();
        //graph.addSeries(mSeries1);
        //setting the graph bounds
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(min);
        graph.getViewport().setMaxX(max);

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(-2.5);
        graph.getViewport().setMaxY(100);

        //enable scaling and scrolling


        mSeries1.setColor(Color.BLACK);


        //Double dataDouble = !dataString.equals("") ? Double.parseDouble(dataString) : 0;
            String[] dataStringArray = dataString.split(" ");
            //make sure we receive full frame of data
            if (dataStringArray.length == 5) {
                double[] dataDoubleArray = new double[dataStringArray.length];
                for (int i = 0; i < dataDoubleArray.length; i++) {
                    dataDoubleArray[i] = Double.parseDouble(dataStringArray[i]);
                }
                lead1[position] = dataDoubleArray[0];
                lead2[position] = dataDoubleArray[1];
                lead3[position] = dataDoubleArray[2];
                leadV1[position] = dataDoubleArray[3];
                leadV2[position] = dataDoubleArray[4];
                position++;
                try {
                    if (position % 10 == 0) {

                        //only analysze lead2, maybe implement ways of analyzing others leads;
                        EcgDataAnalysis(lead2);
                        EcgDataAnalysis2(leadV1);

                        switch (whichLead){
                            case 0:
                                samples = Arrays.copyOf(lead2,lead2.length);
                                break;
                            case 1:
                                samples = Arrays.copyOf(lead1,lead1.length);
                                break;
                            case 2:
                                samples = Arrays.copyOf(lead3,lead3.length);
                                break;
                            case 3:
                                samples = Arrays.copyOf(leadV1,leadV1.length);
                                break;
                            case 4:
                                samples = Arrays.copyOf(leadV2,leadV2.length);
                                break;



                        }

//                        for (int i = 0; i < 10; i++) {
//                            samples[i] = data[i];
//                        }
//                EcgDataAnalysis(samples);


                        if (flag < 4) {
                            for (int i = 0; i < 10; i++)
                                plotData[flag * 10 + i] = samples[i];
                        } else {
                            for (int i = 0; i < 30; i++)
                                plotData[i] = plotData[i + 10];

                            for (int i = 0; i < 10; i++)
                                plotData[i + 30] = samples[i];
                        }
                        flag++;

                        //dataFiltering();
                        //createPlotArray(samples);
                        //channelFilter(samples, in, fb);


                        //Get UartService data and append them on the graph
                        if (flag == 1) {
                            for (int i = 1; i < 10; i++) {
                                mSeries1.appendData(new DataPoint(i, plotData[i]), false, 10);
                            }
//1st 100 samples - 1 sec
                        } else if (flag == 2) {
                            for (int i = 1; i < 20; i++) {
                                mSeries1.appendData(new DataPoint(i, plotData[i]), false, 20);//1st 500 samples - 2 secs
                            }
                        } else if (flag == 3) {
                            for (int i = 1; i < 30; i++) {
                                if (i % 10 == 0)
                                    i = i + 1;
                                mSeries1.appendData(new DataPoint(i, plotData[i]), false, 30);//1st 750 samples - 3 secs
                            }
                        } else if (flag == 4) {
                            for (int i = 1; i < 40; i++) {
                                if (i % 10 == 0)
                                    i = i + 1;
                                mSeries1.appendData(new DataPoint(i, plotData[i]), false, 40);// 1st 400 samples - 4 secs
                            }
                        } else {
                            for (int i = 1; i < 40; i++) {
                                if (i % 10 == 0)
                                    i = i + 1;
                                mSeries1.appendData(new DataPoint(min + i, plotData[i]), false, 40);//refresh to graph the last 400 samples - 4 last secs
                            }
                        }

                        currentTimeSec = currentTimeSec + 10;

                        if (currentTimeSec == max) {
                            min = min + 10;
                            max = max + 10;
                        }

                        graph.addSeries(mSeries1);
                        position = 0;

                    }

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }

        }

//            if (position % 100 == 0) {
//                for (int i = 0; i < 100; i++) {
//                    samples[i] = data[i];
//                }
//                if (flag < 4) {
//                    for (int i = 0; i < 100; i++)
//                        plotData[flag * 100 + i] = samples[i];
//                } else {
//                    for (int i = 0; i < 750; i++)
//                        plotData[i] = plotData[i + 100];
//
//                    for (int i = 0; i < 100; i++)
//                        plotData[i + 750] = samples[i];
//                }
//                flag++;
//
//                //dataFiltering();
//                //createPlotArray(samples);
//                //channelFilter(samples, in, fb);
//
//
//                //Get UartService data and append them on the graph
//                if (flag == 1) {
//                    for (int i = 0; i < 100; i++)
//                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 99);//1st 100 samples - 1 sec
//                } else if (flag == 2) {
//                    for (int i = 100; i < 500; i++) {
//                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 199);//1st 500 samples - 2 secs
//                    }
//                } else if (flag == 3) {
//                    for (int i = 500; i < 750; i++) {
//                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 249);//1st 750 samples - 3 secs
//                    }
//                } else if (flag == 4) {
//                    for (int i = 750; i < 400; i++) {
//                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 399);// 1st 400 samples - 4 secs
//                    }
//                } else {
//                    for (int i = 750; i < 400; i++) {
//                        mSeries1.appendData(new DataPoint(min + i, plotData[i]), false, 399);//refresh to graph the last 400 samples - 4 last secs
//                    }
//                }
//
//                currentTimeSec = currentTimeSec + 100;
//
//                if (currentTimeSec == max) {
//                    min = min + 100;
//                    max = max + 100;
//                }
//
//                graph.addSeries(mSeries1);
//                    position = 0;
//            }
//            if (position % 250 == 0) {
//
//
//                for (int i = 0; i < 250; i++) {
//                    samples[i] = data[i];
//                }
//                if (flag < 4) {
//                    for (int i = 0; i < 250; i++)
//                        plotData[flag * 250 + i] = samples[i];
//                } else {
//                    for (int i = 0; i < 750; i++)
//                        plotData[i] = plotData[i + 250];
//
//                    for (int i = 0; i < 250; i++)
//                        plotData[i + 750] = samples[i];
//                }
//                flag++;
//
//                //dataFiltering();
//                //createPlotArray(samples);
//                //channelFilter(samples, in, fb);
//
//
//                //Get UartService data and append them on the graph
//                if (flag == 1) {
//                    for (int i = 10; i < 250; i++)
//                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 249);//1st 250 samples - 1 sec
//                } else if (flag == 2) {
//                    for (int i = 10; i < 500; i++) {
//                        if (i % 250 == 0)
//                            i = i + 10;
//                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 499);//1st 500 samples - 2 secs
//                    }
//                } else if (flag == 3) {
//                    for (int i = 10; i < 750; i++) {
//                        if (i % 250 == 0)
//                            i = i + 10;
//                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 749);//1st 750 samples - 3 secs
//                    }
//                } else if (flag == 4) {
//                    for (int i = 10; i < 1000; i++) {
//                        if (i % 250 == 0)
//                            i = i + 10;
//                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 999);// 1st 1000 samples - 4 secs
//                    }
//                } else {
//                    for (int i = 10; i < 1000; i++) {
//                        if (i % 250 == 0)
//                            i = i + 10;
//                        mSeries1.appendData(new DataPoint(min + i, plotData[i]), false, 999);//refresh to graph the last 1000 samples - 4 last secs
//                    }
//                }
//
//                currentTimeSec = currentTimeSec + 250;
//
//                if (currentTimeSec == max) {
//                    min = min + 250;
//                    max = max + 250;
//                }
//
//                graph.addSeries(mSeries1);
//                position = 0;
//            }
    //}

    }


    private void EcgDataAnalysis(Double[] samples) {
        
        Double dataTemp[] = new Double[10];
        for (int i =0; i<10;i++)
            dataTemp[i] = samples[i];
        Log.d(TAG, "dataTemp");
        //average of closest 10 values
//        for (int i = 5; i<6;i++){
//            dataTemp[i] = ((dataTemp[i-5] + dataTemp[i-4] + dataTemp[i-3] + dataTemp[i-2] + dataTemp[i-1] + dataTemp[i]
//                    + dataTemp[i+1] + dataTemp[i+1] + dataTemp[i+2] + dataTemp[i+3] + dataTemp[i+4])/10);
//        }
        Log.d(TAG, "made it here");
        rPeakLocalMax = dataTemp[0];
        for (int i = 0; i<10;i++){
            dataAvgValue += dataTemp[i];
            if (rPeakLocalMax<dataTemp[i]){
                rPeakLocalMax = dataTemp[i];
                locationLocal = i;
            }
        }
        dataAvgValue = dataAvgValue/10;
        if (firstRPeak){
            if((Math.abs((rPeakMaxTemp-lastAvgValue) / (rPeakLocalMax-dataAvgValue) - 1) < 0.5) && ((locationLocal-locationMax)>60)){
                rPeakMaxTemp = rPeakLocalMax;
                locationMax = locationLocal;
                lastAvgValue = dataAvgValue;
                Log.d(TAG, Double.toString(rPeakMaxTemp));
                if (firstRPeak){
                    RRInterval = (locationMax - locationLocal) * 1/250; //250 samples/s = 250Hz
                    beatRate = 60 / RRInterval;

                    prevRPeak = rPeakMaxTemp;
                    locationPrev = locationMax;
                } else {
                    prevRPeak = rPeakMaxTemp;
                    locationPrev = locationMax;

                }
            }
            //set text for heart rate and RR interval
            Interval.setText("RR Interval: " + RRInterval);
            heartRate.setText("Heart Rate: " + beatRate);
            if (Math.abs(RRInterval - prevRRInterval) > 0.12){
                addNotification(2);

            }
            prevRRInterval = RRInterval;
        }
        else {
            if (rPeakMaxTemp < rPeakLocalMax){
                rPeakMaxTemp = rPeakLocalMax;
                locationMax = locationLocal;
                lastAvgValue = dataAvgValue;
            }
            if ((rPeakMaxTemp / Math.abs(dataAvgValue)) > 1.1){
                Log.d(TAG, Double.toString(rPeakMaxTemp));
                firstRPeak = true;
                prevRPeak = rPeakMaxTemp;
                locationPrev = locationMax;
            }
        }


    }
    private void EcgDataAnalysis2(Double[] samples) {
        RpeakV2 = samples[0];
        SpeakV2 = samples[0];

        for (int i = 0; i<10;i++){
            dataAvgValueV2 += samples[i];
            if (RpeakV2<samples[i]){
                RpeakV2 = samples[i];
            }
            if (SpeakV2>samples[i]){
                SpeakV2 = samples[i];
            }
        }
      if ((RpeakV2 > 0.25) || (RpeakV2 > Math.abs(SpeakV2))){
            addNotification(0);
        }
    }

    private void addNotification(int x) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("W^2 ECG System Alert");
        if (x == 0) {

            builder.setContentText("An abnormality has been found in lead V1: R peak is greater than S peak.");
        }
        else if (x == 1){
            builder.setContentText("An abnormality has been found in lead 2: R peak exceeds 1 mV.");
        }
        else if (x == 1){
            builder.setContentText("An abnormality has been found in lead 2: RR intervals greater than 0.12 seconds apart.");
        }
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(0, builder.build());
    }
//}


    private void findGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            if (SampleGattAttributes.lookup(uuid, unknownServiceString) == "W2ECG") {
                Log.d(TAG, "ECG discovered");
            } else {
            }
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);
            // get characteristic when UUID matches RX/TX UUID
            characteristicRX = gattService.getCharacteristic(UartService.UUID_RX_CHAR_UUID);
            if (characteristicRX != null) {
                final int charaProp = characteristicRX.getProperties();
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    if (mNotifyCharacteristic != null) {
                        mService.setCharacteristicNotification(
                                mNotifyCharacteristic, false);
                        mNotifyCharacteristic = null;
                    }
                    mService.readCharacteristic(characteristicRX);
                }
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristicRX;
                    mService.setCharacteristicNotification(
                            characteristicRX, true);
                }
            }

//            // SEcond characteristic
//            // get characteristic when UUID matches RX/TX UUID
//            characteristicRX2 = gattService.getCharacteristic(UartService.UUID_RX_CHAR_UUID);
//            if(characteristicRX2 != null){
//                final int charaProp = characteristicRX2.getProperties();
//                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
//                    // If there is an active notification on a characteristic, clear
//                    // it first so it doesn't update the data field on the user interface.
//                    if (mNotifyCharacteristic != null) {
//                        mService.setCharacteristicNotification(
//                                mNotifyCharacteristic, false);
//                        mNotifyCharacteristic = null;
//                    }
//                    mService.readCharacteristic(characteristicRX);
//                }
//                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
//                    mNotifyCharacteristic = characteristicRX;
//                    mService.setCharacteristicNotification(
//                            characteristicRX, true);
//                }
//            }

        }
    }

    //called in on create
    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }
}