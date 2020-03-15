package com.example.ecg490;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int UART_PROFILE_READY = 10;
    public static final String TAG = "nRFUART";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private static final int STATE_OFF = 10;
    private ScanSettings settings;

    private int mState = UART_PROFILE_DISCONNECTED;
    private UartService mService = null;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter btAdapter = null;
    private ListView BLEListView;
    private ArrayAdapter<String> listAdapter;
    private Button connectDisconnectButton;
    private Button logoutButton;

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("Really Exit?")
                .setMessage("Are you sure you want to exit?")
                .setNegativeButton(android.R.string.no, null)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface arg0, int arg1) {
                        MainActivity.super.onBackPressed();
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
        connectDisconnectButton =(Button) findViewById(R.id.connectButton);
        service_init();
        CheckBlueToothState(); // Make sure bluetooth is enabled, if not, prompt the user to enable it
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
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
            }}

        connectDisconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!btAdapter.isEnabled()) {
                    Log.i(TAG, "onClick - BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }
                else {
                    if (connectDisconnectButton.getText().equals("Connect")){
                        //Connect button pressed, open DeviceListActivity class, with popup windows that scan for devices
                        Intent newIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(newIntent, REQUEST_SELECT_DEVICE);
                    } else {
                        //Disconnect button pressed
                        if (mDevice!=null)
                            mService.disconnect();

                    }
            }
        }});
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();
        switch (id){
            case R.id.Logout:
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(MainActivity.this, SignInActivity.class);
                startActivity(intent);
                Toast.makeText(this,"You have been logged out", Toast.LENGTH_SHORT).show();
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
        mService= null;
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
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
        public void handleMessage(Message msg) {}
    };

    public final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, final Intent intent) {
            String action = intent.getAction();

            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                /*runOnUiThread(new Runnable() {
                    public void run() {*/
                Log.d(TAG, "UART_CONNECT_MSG");
                connectDisconnectButton.setText("Disconnect");
                mState = UART_PROFILE_CONNECTED;
                //   }
                // });
            }
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                //runOnUiThread(new Runnable() {
                //  public void run() {
                Log.d(TAG, "UART_DISCONNECT_MSG");
                connectDisconnectButton.setText("Connect");
                mState = UART_PROFILE_DISCONNECTED;
                //stopCommand();
                mService.close();
                // }
                //});
            }
           /* if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                //startCommand();
                setCommand();
                //mService.enableTXNotification();
            }
            *//*********************//*
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
                final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                 *//*runOnUiThread(new Runnable() {
                     public void run() {*//*
                try {

                    for (int i = 0; i < 5; i++) {
                        temp_sample = fromByteArray(txValue, i);
                        data[position] = registerValueToVolt(temp_sample);
                        position++;
                    }

                    if(position % 250 == 0) {
                        //set RealTimeUpdates Arguments
                        RealtimeUpdates realTimeData = new RealtimeUpdates();

                        //Begin the transaction
                        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                        ft.replace(R.id.frameLayout, realTimeData);
                        ft.commit();

                        position = 0;
                    }
                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
                // }
                //});
            }*/

            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Device doesn't support UART. Disconnecting");
                //stopCommand();
                mService.disconnect();
            }
        }
    };
    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        //intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        //intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }
    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }
    //FANCY COULD BE USEFUL
//    public void onBackPressed() {
//        if (mState == UART_PROFILE_CONNECTED) {
//            Intent startMain = new Intent(Intent.ACTION_MAIN);
//            startMain.addCategory(Intent.CATEGORY_HOME);
//            startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            startActivity(startMain);
//            //showMessage("nRFUART's running in background.\n             Disconnect to exit");
//        }
//        else {
//            new AlertDialog.Builder(this)
//                    .setIcon(android.R.drawable.ic_dialog_alert)
//                    .setTitle(R.string.popup_title)
//                    .setMessage(R.string.popup_message)
//                    .setPositiveButton(R.string.popup_yes, new DialogInterface.OnClickListener()
//                    {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                            finish();
//                        }
//                    })
//                    .setNegativeButton(R.string.popup_no, null)
//                    .show();
//        }
//    }
    //CONVERTING FROM BYTES TO DATA--->ALSO CHECK CHICKENJOHN
//    int fromByteArray(byte[] bytes, int position) {
//        if((bytes[position*3]>>7) == 1)
//            return (bytes[0 + position*3]) << 16 | (bytes[1 + position*3] & 0xFF) << 8 | (bytes[2 + position*3] & 0xFF);
//        else
//            return (bytes[0 + position*3]) << 16 | (bytes[1 + position*3] & 0xFF) << 8 | (bytes[2 + position*3] & 0xFF);
//    }
    float registerValueToVolt(int value){
        return (float) (((value/(Math.pow(2,23)-1))) * 2.5f);
    }
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    //Configuration commands
    public void startCommand(){
        byte[] configuration = new byte[1];
        configuration[0] = 0;
        //mService.writeRXCharacteristic(configuration);
    }

    public void stopCommand(){
        byte[] configuration = new byte[1];
        configuration[0] = 1;
        mService.writeRXCharacteristic(configuration);
    }
    public void setCommand(){
        stopCommand();
        android.os.SystemClock.sleep(50);
        byte[] configuration = new byte[3];
        configuration[0] = 2;
        for(int i=0; i <=22; i++){
            //configuration[1] = (byte) EcgConfig[i][0];
            //configuration[2] = (byte) EcgConfig[i][1];
            //mService.writeRXCharacteristic(configuration);
            android.os.SystemClock.sleep(50);
            android.os.SystemClock.sleep(50);
            android.os.SystemClock.sleep(50);
        }
        startCommand();
    }
}
