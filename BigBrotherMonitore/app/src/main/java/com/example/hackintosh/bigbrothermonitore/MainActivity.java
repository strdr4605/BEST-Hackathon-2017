package com.example.hackintosh.bigbrothermonitore;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private TextView textView;

    private static final int REQUEST_ENABLE_BT = 1;

    private  BroadcastReceiver mReceiver;

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    List<String> devicesName = new ArrayList<String>();

    List<String> devicesMac = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = (TextView) findViewById(R.id.textView);;

        //BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
        }
        else {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }

        createBroadcastReceiver();

        // Register for broadcasts when a device is discovered.
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filter);

        Log.v("Discovery", "" + mBluetoothAdapter.startDiscovery());

        int size = devicesName.size();

//        while (true) {
//            if(devicesName.size() != size) {
//                textView.append(devicesName.get(devicesName.size() - 1));
//                textView.append(devicesMac.get(devicesMac.size() - 1));
//                size = devicesName.size();
//            }
//        }


    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    public void createBroadcastReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String deviceName = device.getName();
                    String deviceHardwareAddress = device.getAddress(); // MAC address
                    devicesName.add(deviceName);
                    devicesMac.add(deviceHardwareAddress);
                    int  rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,Short.MIN_VALUE);
                    String message = "Name:" + deviceName + "\nMac:" + deviceHardwareAddress +
                            "  \nRSSI: " + rssi + "dBm";
                    Toast.makeText(getApplicationContext(),message, Toast.LENGTH_SHORT).show();
//                    textView.append(deviceName);
//                    textView.append(deviceHardwareAddress);
//                    textView.append("\n");
                }
                if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    Log.v("Discovery","finished");
                    //mBluetoothAdapter.cancelDiscovery();
                    mBluetoothAdapter.startDiscovery();
//                    unregisterReceiver(mReceiver);
//                    showDevices();
                }
            }
        };

        this.mReceiver = receiver;
    }

    public void showDevices() {
        Log.v("DevicesName","" + devicesName);
        Log.v("DevicesLen","" + devicesName.size());
        Log.v("DevicesMac","" + devicesMac);
        Log.v("DevicesMacLen","" + devicesMac.size());
        for (int i = 0; i < devicesName.size(); i++) {
            if(devicesName.get(i) != null) {
                textView.append(devicesName.get(i));
            }
//            textView.append(devicesMac.get(i));
            textView.append("\n");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(mReceiver);
    }
}
