package com.example.ecg490;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LineGraphSeries<DataPoint> mSeries1;
    //private Runnable mTimer1;
    float samples[] = new float[250];
    static float plotData[] = new float[1000];
    public static int currentTimeSec = 0;
    public static int min = 0;
    public static int max = 1000;
    public static int flag = 0;
    float in[] = new float[3];
    float fb[] = new float[2];

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;
    private ScanSettings settings;
    private TextView mDataField;

    private int k;
    private Button disconnectButton;

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
    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    float data[] = new float[250];
    int temp_sample;
    int position = 0;

    @Override
    public void onBackPressed() {
        if (k ==1) {
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
        } else if (k ==2){
            setContentView(R.layout.activity_main);
            k = 1;
            mService.disconnect();
        }
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
        k = 1;
        BLEListView = (ListView) findViewById(R.id.BLEDevices);
        listAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
        BLEListView.setAdapter(listAdapter);
        BLEListView.setDivider(null);
        connectButton = (Button) findViewById(R.id.connectButton);

        mDataField = (TextView) findViewById(R.id.data_value);
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

            Log.d(TAG, "before if");

            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                /*runOnUiThread(new Runnable() {
                    public void run() {*/
                Log.d(TAG, "UART_CONNECT_MSG");

                mState = UART_PROFILE_CONNECTED;
                //   }
                // });
            }
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                //runOnUiThread(new Runnable() {
                //  public void run() {
                Log.d(TAG, "UART_DISCONNECT_MSG");
                connectButton.setText("Connect");
                mState = UART_PROFILE_DISCONNECTED;
                mDataField.setText("");

                //stopCommand();
                //mService.disconnect();
                mService.close();
                // }
                //});
            }
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                //startCommand();
                // setCommand();
                Log.d(TAG, "call displaygatt");
                displayGattServices(mService.getSupportedGattServices());
                //mService.enableTXNotification();
            }
            //*//*********************//*
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
                //displayData(intent.getStringExtra(UartService.EXTRA_DATA));
//                final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
//                 *//*runOnUiThread(new Runnable() {
//                     public void run() {*//*
//                try {
//
//                    for (int i = 0; i < 5; i++) {
//                        temp_sample = fromByteArray(txValue, i);
//                        data[position] = registerValueToVolt(temp_sample);
//                        position++;
//                    }
//
//                    if(position % 250 == 0) {
//                        //set RealTimeUpdates Arguments
//                        RealtimeUpdates realTimeData = new RealtimeUpdates();
//
//                        //Begin the transaction
//                        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
//                        ft.replace(R.id.frameLayout, realTimeData);
//                        ft.commit();
//
//                        position = 0;
//                    }
//                } catch (Exception e) {
//                    Log.e(TAG, e.toString());
//                }
//                // }
//                //});
                //
                
                String dataString = intent.getStringExtra(UartService.EXTRA_DATA);
                displayGraph(dataString);
                displayData(dataString);
//            graph.setVisibility(View.VISIBLE);
//            series = LineGraphSeries<DataPoint>();
//            graph.addSeries(series);
//
//            String dataString = intent.getStringExtra(UartService.EXTRA_DATA);
//            displayData(dataString);
//
//            Integer dataInt = !dataString.equals("")?Integer.parseInt(dataString) : 0;
//            try{
//                data[position] = dataInt;
//                position++;
//                Log.d(TAG,"tryna frag");
//                Log.d(TAG, "" + position);
//                if (position  % 250 == 0){
//                    //fragment
//
//                    RealTimeUpdate realTimeData = new RealTimeUpdate();
//                    Log.d(TAG,"frag success");
//                    FragmentTransaction showFrag = getSupportFragmentManager().beginTransaction();
//                    showFrag.replace(R.id.frameLayout, realTimeData);
//                    showFrag.commit();
//                    position = 0;
//                }
//            }
//            catch (Exception e){
//                Log.e(TAG, e.toString());
//            }
            }
            //*********************//
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
        k = 2;
        setContentView(R.layout.graph_viewer);
        Button disconnectButton = (Button) findViewById(R.id.disconnectButton);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.disconnect();
                setContentView(R.layout.activity_main);
            }
        });

        Spinner mySpinner = (Spinner) findViewById(R.id.spinner1);
        ArrayAdapter<String> myAdapter = new ArrayAdapter<String>(MainActivity.this,
                android.R.layout.simple_list_item_1, getResources().getStringArray(R.array.leads));
        myAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mySpinner.setAdapter(myAdapter);

        GraphView graph = (GraphView) findViewById(R.id.graph);
        //graph.setVisibility(View.INVISIBLE);
        mSeries1 = new LineGraphSeries<>();
        //setting the graph bounds
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(min);
        graph.getViewport().setMaxX(max);

        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(-2.5);
        graph.getViewport().setMaxY(100);

        //enable scaling and scrolling
        graph.getViewport().setScrollable(true);
        graph.getViewport().setScrollableY(true);
        graph.getViewport().setScalable(true);
        graph.getViewport().setScalableY(true);

        //other parameters
        graph.setKeepScreenOn(true);
        graph.getGridLabelRenderer().setHorizontalAxisTitle("Time");
        graph.getGridLabelRenderer().setVerticalAxisTitle("mV");
        graph.getGridLabelRenderer().setHumanRounding(true);
        mSeries1.setColor(Color.BLUE);
        Integer dataInt = !dataString.equals("") ? Integer.parseInt(dataString) : 0;

        try {
            data[position] = dataInt;
            position++;
            Log.d(TAG, "tryna frag");
            Log.d(TAG, "" + position);
            if (position % 250 == 0) {


                for (int i = 0; i < 250; i++) {
                    samples[i] = data[i];
                }
                if (flag < 4) {
                    for (int i = 0; i < 250; i++)
                        plotData[flag * 250 + i] = samples[i];
                } else {
                    for (int i = 0; i < 750; i++)
                        plotData[i] = plotData[i + 250];

                    for (int i = 0; i < 250; i++)
                        plotData[i + 750] = samples[i];
                }
                flag++;

                //dataFiltering();
                //createPlotArray(samples);
                //channelFilter(samples, in, fb);

                //Get UartService data and append them on the graph
                if (flag == 1) {
                    for (int i = 10; i < 250; i++)
                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 249);//1st 250 samples - 1 sec
                } else if (flag == 2) {
                    for (int i = 10; i < 500; i++) {
                        if (i % 250 == 0)
                            i = i + 10;
                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 499);//1st 500 samples - 2 secs
                    }
                } else if (flag == 3) {
                    for (int i = 10; i < 750; i++) {
                        if (i % 250 == 0)
                            i = i + 10;
                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 749);//1st 750 samples - 3 secs
                    }
                } else if (flag == 4) {
                    for (int i = 10; i < 1000; i++) {
                        if (i % 250 == 0)
                            i = i + 10;
                        mSeries1.appendData(new DataPoint(i, plotData[i]), false, 999);// 1st 1000 samples - 4 secs
                    }
                } else {
                    for (int i = 10; i < 1000; i++) {
                        if (i % 250 == 0)
                            i = i + 10;
                        mSeries1.appendData(new DataPoint(min + i, plotData[i]), false, 1000);//refresh to graph the last 1000 samples - 4 last secs
                    }
                }
                currentTimeSec = currentTimeSec + 250;
                if (currentTimeSec == max) {
                    min = min + 250;
                    max = max + 250;
                }
                graph.addSeries(mSeries1);
                position = 0;
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void displayData(String data) {
        if (data != null) {
            mDataField.setText(data);
        }
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {

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
            Log.d(TAG, "right above");
            // If the service exists for HM 10 Serial, say so.
            if (SampleGattAttributes.lookup(uuid, unknownServiceString) == "W2ECG") {
                //isSerial.setText("Yes, serial :-)");
                Log.d(TAG, "ECG discovered");
            } else {
                //isSerial.setText("No, serial :-(");
                Log.d(TAG, "Nathan bruv");
            }
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            // get characteristic when UUID matches RX/TX UUID
            characteristicTX = gattService.getCharacteristic(UartService.UUID_TX_CHAR_UUID);
            if(characteristicTX != null){
                final int charaProp = characteristicTX.getProperties();
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    // If there is an active notification on a characteristic, clear
                    // it first so it doesn't update the data field on the user interface.
                    if (mNotifyCharacteristic != null) {
                        mService.setCharacteristicNotification(
                                mNotifyCharacteristic, false);
                        mNotifyCharacteristic = null;
                    }
                    mService.readCharacteristic(characteristicTX);
                }
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = characteristicTX;
                    mService.setCharacteristicNotification(
                            characteristicTX, true);
                }
            }

//            // SEcond characteristic
//            // get characteristic when UUID matches RX/TX UUID
//            characteristicTX2 = gattService.getCharacteristic(UartService.UUID_TX_CHAR_UUID);
//            if(characteristicTX2 != null){
//                final int charaProp = characteristicTX2.getProperties();
//                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
//                    // If there is an active notification on a characteristic, clear
//                    // it first so it doesn't update the data field on the user interface.
//                    if (mNotifyCharacteristic != null) {
//                        mService.setCharacteristicNotification(
//                                mNotifyCharacteristic, false);
//                        mNotifyCharacteristic = null;
//                    }
//                    mService.readCharacteristic(characteristicTX);
//                }
//                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
//                    mNotifyCharacteristic = characteristicTX;
//                    mService.setCharacteristicNotification(
//                            characteristicTX, true);
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
