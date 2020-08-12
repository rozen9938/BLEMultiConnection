package net.irobotfactory.blemanual;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.HashMap;
import java.util.UUID;

public class BluetoothService extends Service {
    private final static String TAG = BluetoothService.class.getSimpleName();
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGattService mBluetoothGattService;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt[] = new BluetoothGatt[6];
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    public static final UUID UART_SERVICE = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID UART_TX_Characteristic = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");//Properties : Notify
    public static final UUID UART_RX_Characteristic = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"); //Properties : Write, Write No Response

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    BluetoothGattCharacteristic mCH;
    HashMap<BluetoothDevice,Integer> mNum = new HashMap();
    // Various callback methods defined by the BLE API.
    private final BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    String intentAction;
                    int i = mNum.get(gatt.getDevice());
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        intentAction = ACTION_GATT_CONNECTED;
                        mConnectionState = STATE_CONNECTED;
                        broadcastUpdate(intentAction);
                        Log.i(TAG, "Connected to GATT server.:"+gatt.getDevice().getAddress());
                        Log.i(TAG, "Attempting to start service discovery:" + mBluetoothGatt[i].discoverServices());
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        intentAction = ACTION_GATT_DISCONNECTED;
                        mConnectionState = STATE_DISCONNECTED;
                        Log.i(TAG, "Disconnected from GATT server.");
                        broadcastUpdate(intentAction);
                    }
                }

                @Override
                // New services discovered
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        String intentAction;
                        int i = mNum.get(gatt.getDevice());
                        broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                        mBluetoothGattService = gatt.getService(UART_SERVICE);
                        mCH = mBluetoothGattService.getCharacteristic(UART_RX_Characteristic);
                        mCH.setValue(setColor(i));
                        Log.e(TAG,"device:"+i);
                        mBluetoothGatt[i].writeCharacteristic(mCH);
                    } else {
                        Log.w(TAG, "onServicesDiscovered received: " + status);
                    }
                }

                @Override
                // Result of a characteristic read operation
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                    }
                }
            };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile. Data
        // parsing is carried out as per profile specifications.
        if (UART_TX_Characteristic.equals(characteristic.getUuid())) {
            int flag = characteristic.getProperties();
            int format = -1;
            if ((flag & 0x01) != 0) {
                format = BluetoothGattCharacteristic.FORMAT_UINT16;
                Log.d(TAG, "Heart rate format UINT16.");
            } else {
                format = BluetoothGattCharacteristic.FORMAT_UINT8;
                Log.d(TAG, "Heart rate format UINT8.");
            }
            final int heartRate = characteristic.getIntValue(format, 1);
            Log.d(TAG, String.format("Received heart rate: %d", heartRate));
            intent.putExtra(EXTRA_DATA, String.valueOf(heartRate));
        } else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                intent.putExtra(EXTRA_DATA, new String(data) + "\n" +
                        stringBuilder.toString());
            }
        }
        sendBroadcast(intent);
    }

    //서비스 바인더 내부 클래스 선언
    public class BluetoothServiceBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this; //현재 서비스를 반환.
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new BluetoothServiceBinder();
    }

    public boolean connect(final String address,int num) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt[num].connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.

        switch (num){
            case 0:
                mBluetoothGatt[0] = device.connectGatt(this, false, mGattCallback);
                mNum.put(device,0);
                break;
            case 1:
                mBluetoothGatt[1] = device.connectGatt(this, false, mGattCallback);
                mNum.put(device,1);
                break;
            case 2:
                mBluetoothGatt[2] = device.connectGatt(this, false, mGattCallback);
                mNum.put(device,2);
                break;
            case 3:
                mBluetoothGatt[3] = device.connectGatt(this, false, mGattCallback);
                mNum.put(device,3);
                break;
            case 4:
                mBluetoothGatt[4] = device.connectGatt(this, false, mGattCallback);
                mNum.put(device,4);
                break;
            case 5:
                mBluetoothGatt[5] = device.connectGatt(this, false, mGattCallback);
                mNum.put(device,5);
                break;
        }
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        return true;
    }
    public void disconnect(int num) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt[num].disconnect();
    }

    //PingPong
    public byte[] setColor(int cubeNum){
        byte [] data = new byte[10];
        data [0] = (byte)0x08;
        data [1] = (byte)0x53;
        data [2] = (byte) 0xCE;
        data [3] = (byte) 0x00;
        data [4] = (byte) 0x0A;
        data [5] = (byte) 0x2;
        data [6] = (byte) 0;
        data [7] = (byte) 0;
        data [8] = (byte) cubeNum;
        data [9] = (byte) 0x64;
        return data;
    }
}
