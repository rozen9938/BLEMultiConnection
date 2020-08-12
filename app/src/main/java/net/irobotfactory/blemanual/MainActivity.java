package net.irobotfactory.blemanual;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothClass.Device;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanCallback mScanCallBack;
    private BluetoothService mBluetoothLeService;
    private ArrayList<BluetoothDevice> devices= new ArrayList<>();
    private final String TAG = getClass().getSimpleName();
    private int a = 0;
    private int maximun_device = 6;
    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothService.BluetoothServiceBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            Log.e(TAG,componentName.toShortString());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (Build.VERSION.SDK_INT >= 23) {
            int isPermissiongGranted = 0;
            for(int i=0;i<permissions.length;i++){
                if(grantResults[i]!= PackageManager.PERMISSION_GRANTED && grantResults[i]!=PackageManager.PERMISSION_GRANTED){
                    //resume tasks needing this permission
                    //return;
                    isPermissiongGranted ++;
                }
            }
            if(isPermissiongGranted==0){

            }else{

            }
            //Get Permission Fail
        }
    }

    //Use to SDK version 23 Higher
    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            int isPermissiongGranted = 0;
            String [] permission = new String[]{Manifest.permission.ACCESS_FINE_LOCATION ,Manifest.permission.ACCESS_COARSE_LOCATION};
            for (int i=0;i<permission.length;i++){
                if(checkSelfPermission(permission[i]) != PackageManager.PERMISSION_GRANTED){
                    isPermissiongGranted ++;
                }else{
                }
            }
            if(isPermissiongGranted > 0){
                requestPermissions(permission,1002);
            }else{

            }
        }else{

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        checkPermission();

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1000);
        }
        Button btn = findViewById(R.id.btn_scan);
        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        mScanCallBack = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                if(result.getDevice().getName()!=null && result.getDevice().getName().contains("PINGPONG")) {
                    if (findDevice(result.getDevice()) == null) {
                        if(a<maximun_device){
                            devices.add(result.getDevice());
                            a = a++;
                        }

                    }
                }
            }
        };

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBluetoothAdapter.enable()){
                    mBluetoothLeScanner.startScan(mScanCallBack);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothLeScanner.stopScan(mScanCallBack);
                            for(int i=0;i<devices.size();i++){
                                mBluetoothLeService.connect(devices.get(i).getAddress(),i);
                            }
                        }
                    },3000);
                }
            }
        });
        Intent gattServiceIntent = new Intent(this, BluetoothService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }
    //Device Duplicate check
    private BluetoothDevice findDevice(final BluetoothDevice result) {
        for(final BluetoothDevice scanResult : devices){
            if(scanResult.getAddress().equals(result.getAddress())) {
                return scanResult;
            }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for(int i=0;i<6;i++){
            mBluetoothLeService.disconnect(i);
        }
        unbindService(mServiceConnection);
    }
}
