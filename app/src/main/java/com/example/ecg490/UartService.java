package com.example.ecg490;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;
import java.util.UUID;

public class UartService extends Service {
    private final static String TAG = UartService.class.getSimpleName();

    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    BluetoothLeScanner btScanner;
    private String mBluetoothDeviceAddress = "0A:0A:0A:0A:0A:0A";
    private BluetoothGatt mGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private DeviceListActivity mScan;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED = "com.example.ecg490.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.example.ecg490.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.ecg490.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.example.ecg490.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "com.example.ecg490.EXTRA_DATA";
    public final static String DEVICE_DOES_NOT_SUPPORT_UART = "com.example.ecg490.DEVICE_DOES_NOT_SUPPORT_UART";

    public final static UUID UUID_TX_CHAR_UUID =
            UUID.fromString(SampleGattAttributes.TX_CHAR_UUID);

    //
//    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
//   public static final UUID RX_SERVICE_UUID = UUID.fromString("59462F12-9543-9999-12C8-58B459A2712D");
//    public static final UUID RX_CHAR_UUID = UUID.fromString("5C3A659E-897E-45E1-B016-007107C96DF6");
//    public static final UUID TX_CHAR_UUID = UUID.fromString("5C3A659E-897E-45E1-B016-007107C96DF7");
    public boolean initialize() {
        if (btManager == null) {
            btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (btManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }
        btAdapter = btManager.getAdapter();
        if (btAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
//sames remember
        btScanner = btAdapter.getBluetoothLeScanner();

        return true;

    }

    //hope yyou remember
    private ScanCallback leScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            //super.onScanResult(callbackType, result);
            if (mBluetoothDeviceAddress.equals(result.getDevice().getAddress())) {
                connectToBleDevice(result.getDevice());
            }
        }
    };

    public boolean connectToBleDevice(BluetoothDevice device) {
        if (btAdapter == null || device.getAddress() == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        //Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && device.getAddress().equals(mBluetoothDeviceAddress) && mGatt != null) {
            Log.d(TAG, "Trying to use an existing mGatt for connection.");
            if (mGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                //if (!mGatt.connect()) {
                //mConnectionState = STATE_DISCONNECTED;
                return false;
            }
        }
        if (mGatt == null) {
            mBluetoothDeviceAddress = device.getAddress();
            if (device == null) {
                Log.w(TAG, "Device not found.  Unable to connect.");
                return false;
            } else {
                Log.d(TAG, "Trying to create a new connection.");
                btScanner.stopScan(leScanCallback);
                mGatt = device.connectGatt(this, false, gattCallback);
                int state = btManager.getConnectionState(mGatt.getDevice(), BluetoothProfile.GATT);
                mConnectionState = STATE_CONNECTING;
            }
        }


        return true;

    }

    public void disconnect() {
        if (btAdapter == null || mGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        mGatt.disconnect();

    }

    public void close() {
        if (mGatt == null) {
            return;
        }
        Log.w(TAG, "mGatt closed");
        mBluetoothDeviceAddress = null;
        mGatt.close();
        mGatt = null;
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (btAdapter == null || mGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mGatt.readCharacteristic(characteristic);
    }

    public BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == 0)
                gatt.connect();
            mGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
            String intentAction;

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                mGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                //mGatt.close();
                //mGatt.disconnect();
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }

        }

        //FOR RECIEVING ACTUAL DATA NOT JUST CONNECTION
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "mGatt = " + mGatt);
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

                // List<BluetoothGattService> services = gatt.getServices();
                // Log.w(TAG, "onServicesDiscovered received: " + services.toString() + "\n");
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        Log.d(TAG, "broadcast");
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        //sendBroadcast(intent);
    }

    //ACTUALLY RECIEVING DATA
    private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is handling for the notification on TX Character of NUS service
        if (UUID_TX_CHAR_UUID.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }

            final String ECGData = characteristic.getStringValue(0); //receive data in byte
            Log.d(TAG, String.format("Received ECG data rate: %s", ECGData));


            intent.putExtra(EXTRA_DATA, String.valueOf(ECGData).trim());
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    public class LocalBinder extends Binder {
        UartService getService() {
            return UartService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Enables or disables notification on a give characteristic.
     * <p>
     * Enable Notification on TX characteristic
     *
     * @return
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (btAdapter == null || mGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (UUID_TX_CHAR_UUID.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CCCD));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mGatt.writeDescriptor(descriptor);
        }
    }

    public List<BluetoothGattService> getSupportedGattServices() {
        if (mGatt == null) return null;

        return mGatt.getServices();
    }
}