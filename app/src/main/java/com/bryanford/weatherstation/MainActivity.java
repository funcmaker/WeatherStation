package com.bryanford.weatherstation;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {
    // Logging
    private static final String TAG = MainActivity.class.getSimpleName();

    // Bt request codes
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_DEVICE = 2;
    private static final int PERMISSIONS_REQUEST_LOCATION = 3;

    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothAdapter btAdapter;
    private TextView tTemp, tHumid, tPress;
    private TextView tDevice, tAddress, tState;
    private ActionBar tBar;
    private View tView;

    // Connection variables
    private HashMap<UUID, BluetoothGattService> mGattServiceMap = new HashMap<>();
    private List<BluetoothGattService> mGattServices;
    private BluetoothService mBluetoothService;
    private boolean mConnected = false;


    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tBar = getActionBar();
        if (tBar != null) {
            tBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE);
            tBar.setIcon(R.mipmap.ic_launcher);
        }

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "No LE Support", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get the UI components
        tView = findViewById(R.id.main_layout);
        tTemp = (TextView) findViewById(R.id.text_temp);
        tHumid = (TextView) findViewById(R.id.text_humid);
        tPress = (TextView) findViewById(R.id.text_press);
        tDevice = (TextView) findViewById(R.id.data_device_name);
        tAddress = (TextView) findViewById(R.id.data_device_address);
        tState = (TextView) findViewById(R.id.data_device_state);

        // Set the default values for the text views
        clearDisplayValues();

        // Get the bluetooth manager and instantiate the adapter
        final BluetoothManager btManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();

        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Check and request location permissions
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSIONS_REQUEST_LOCATION);

                // PERMISSIONS_REQUEST_LOCATION is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        // Enforce Bluetooth
        if (btAdapter == null || !btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        if (mBluetoothService != null) {
            mBluetoothService.disconnect();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        if (mConnected) {
            menu.findItem(R.id.main_disconnect).setVisible(true);
            menu.findItem(R.id.main_view_services).setVisible(true);
        } else {
            menu.findItem(R.id.main_disconnect).setVisible(false);
            menu.findItem(R.id.main_view_services).setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        Intent deviceIntent;
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.main_devices) {
            deviceIntent = new Intent(this, DeviceActivity.class);
            startActivityForResult(deviceIntent, REQUEST_DEVICE);
            return true;
        } else  if (id == R.id.main_disconnect) {
            runOnUiThread(onDisconnect_Click);
        } else if (id == R.id.main_view_services) {
            runOnUiThread(onViewList_Click);
        } else if (id == android.R.id.home) {
            setContentView(R.layout.activity_main);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_DEVICE && resultCode != Activity.RESULT_CANCELED) {
            // If connecting to new device, disconnect from any possible connection
            if (mBluetoothService != null) {
                mBluetoothService.disconnect();
            }

            // Get the device name and address from the result
            mDeviceAddress = data.getStringExtra(EXTRAS_DEVICE_ADDRESS);
            mDeviceName = data.getStringExtra(EXTRAS_DEVICE_NAME);

            // Create the bind to the BluetoothService class
            Intent gattServiceIntent = new Intent(this, BluetoothService.class);
            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

            // Register the broadcast receiver for updates from the service
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

            if (mBluetoothService != null) {
                final boolean result = mBluetoothService.connect(mDeviceAddress);
                Log.d(TAG, "Connect request result=" + result);
            }
        } else if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
    }

    /* Needed to request location permissions for the app (New requirement to 6.0) */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    // Service management
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mBluetoothService = ((BluetoothService.LocalBinder) iBinder).getService();

            if (!mBluetoothService.initialize()) {
                Log.e(TAG, "Unable to initialize");
                finish();
            }

            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothService = null;
        }
    };

    private Runnable updateOnConnection = new Runnable() {
        @Override
        public void run() {
            tDevice.setText(" " + mDeviceName);
            tAddress.setText(" " + mDeviceAddress);
            tState.setText(" " + mConnected);

            // Reset the option menu
            invalidateOptionsMenu();
        }
    };

    private Runnable updateOnDisconnect = new Runnable() {
        @Override
        public void run() {
            mConnected = false;
            unregisterReceiver(mGattUpdateReceiver);
            unbindService(mServiceConnection);
            clearDisplayValues();

            // Reset the option menu
            invalidateOptionsMenu();
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            final String action = intent.getAction();

            if (BluetoothService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                runOnUiThread(updateOnConnection);
            } else if (BluetoothService.ACTION_GATT_DISCONNECTED.equals(action)) {
                runOnUiThread(updateOnDisconnect);
            } else if (BluetoothService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                BluetoothGattCharacteristic characteristic;

                mGattServices = mBluetoothService.getSupportedGattServices();

                for (BluetoothGattService service : mGattServices) {
                    // Add all of the services to a hashmap for easier access
                    mGattServiceMap.put(service.getUuid(), service);

                    // Find pressure service and enable notifications
                    if (service.getUuid().equals(DeviceTags.HUMIDITY_SERVICE)) {
                        characteristic = service.getCharacteristic(DeviceTags.HUMIDITY_CONFIG_CHAR);

                        // Enable characteristic
                        characteristic.setValue(new byte[]{0x01});
                        mBluetoothService.writeCharacteristic(characteristic);
                    } else if (service.getUuid().equals(DeviceTags.PRESSURE_SERVICE)) {
                        characteristic = service.getCharacteristic(DeviceTags.PRESSURE_CONFIG_CHAR);

                        // Enable characteristic
                        characteristic.setValue(new byte[]{0x01});
                        mBluetoothService.writeCharacteristic(characteristic);
                    } else if (service.getUuid().equals(DeviceTags.GENERIC_SERVICE)) {
                        characteristic = service.getCharacteristic(DeviceTags.SERVICE_CHANGED_CHAR);
                        mBluetoothService.setCharacteristicNotification(characteristic, true);
                    }
                }
            } else if (BluetoothService.TEMP_DATA.equals(action)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tTemp.setText(intent.getStringExtra(BluetoothService.TEMP_DATA));
                    }
                });
            } else if (BluetoothService.HUMID_DATA.equals(action)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                double temp = intent.getDoubleExtra(BluetoothService.TEMP_DATA, 0.d);

                                tTemp.setText(String.format("Temp: %.2f", temp));
                                tHumid.setText(intent.getStringExtra(BluetoothService.HUMID_DATA));
                                adjustViewColorByTemp(tView, temp);
                            }
                        });
            } else if (BluetoothService.PRESS_DATA.equals(action)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tPress.setText(intent.getStringExtra(BluetoothService.PRESS_DATA));
                    }
                });
            } else if (BluetoothService.ACTION_DATA_AVAILABLE.equals(action)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tTemp.setText(intent.getStringExtra(BluetoothService.RX_DATA));
                    }
                });
            }
        }
    };

    private Runnable onDisconnect_Click = new Runnable() {
        @Override
        public void run() {
            // Cleanup the service connection and hide the disconnect button
            mBluetoothService.disconnect();
        }
    };

    private Runnable onViewList_Click = new Runnable() {
        @Override
        public void run() {
            // Display all of the services available
            Intent serviceIntent = new Intent(MainActivity.this, ServiceListActivity.class);
            startActivity(serviceIntent);
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothService.RX_DATA);
        intentFilter.addAction(BluetoothService.HUMID_DATA);
        intentFilter.addAction(BluetoothService.TEMP_DATA);
        intentFilter.addAction(BluetoothService.PRESS_DATA);
        return intentFilter;
    }

    private void clearDisplayValues() {
        tView.setBackgroundColor(Color.WHITE);
        tTemp.setText("");
        tHumid.setText("");
        tPress.setText("");
        tDevice.setText("");
        tAddress.setText("");
        tState.setText(" " + mConnected);
    }

    private void adjustViewColorByTemp(View view, double temp) {
        int alpha = 225;
        int red = 0;
        int grn = 0;
        int blu = 0;

        if (temp > 100.d) {
            red = 255;
        } else if (temp > 90.d) {
            red = 255; grn = 30;
        } else if (temp > 80.d) {
            red = 255; grn = 100;
        } else if (temp > 70.d) {
            red = 255; grn = 200;
        } else if (temp > 60.d) {
            grn = 225;
        } else if (temp > 50.d) {
            grn = 225; blu = 150;
        } else if (temp > 40.d) {
            grn = 175; blu = 225;
        } else if (temp > 32.d) {
            blu = 255;
        }
        view.setBackgroundColor(Color.argb(alpha, red, grn, blu));
    }
}
