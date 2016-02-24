package com.bryanford.weatherstation;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;

public class DeviceActivity extends AppCompatActivity implements DeviceListFragment.OnItemSelectedListener{
    private final static String TAG = DeviceActivity.class.getSimpleName();

    // Bluetooth request codes
    private static final int REQUEST_ENABLE_BT = 1;

    // Scanning timeout ms
    public static final long SCAN_PERIOD = 10000;

    // List fragment to display the device on BtLeCallback
    public DeviceListFragment mDeviceListFragment;

    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner btScanner;
    private boolean mScanning;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.list_item_device);  // App Theme allows this
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mHandler = new Handler();
        final BluetoothManager btManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();

        mDeviceListFragment = new DeviceListFragment();
        getFragmentManager().beginTransaction().replace(R.id.device_list_frame, mDeviceListFragment).commit();
        scanLeDevice(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_device, menu);

        if (mScanning) {
            menu.findItem(R.id.start_action).setVisible(false);
            menu.findItem(R.id.stop_action).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        else {
            menu.findItem(R.id.start_action).setVisible(true);
            menu.findItem(R.id.stop_action).setVisible(false);
            menu.findItem(R.id.menu_refresh).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.start_action) {
            mDeviceListFragment.clearDevices();
            scanLeDevice(true);
            return true;
        }
        else if (id == R.id.stop_action) {
            scanLeDevice(false);
        }
        else if (id == android.R.id.home) {
            onBackPressed();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanLeDevice(false);
    }

    // Runnable used for the stopScan delay
    private Runnable scanRunnable = new Runnable() {
        @Override
        public void run() {
            mScanning = false;
            btScanner.stopScan(mLeScanCallback);

            // Reset the option menu
            invalidateOptionsMenu();
        }
    };

    // Main method scan for BLE devices
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(scanRunnable, SCAN_PERIOD);

            mScanning = true;
            btScanner.startScan(mLeScanCallback);
        }
        else {
            mScanning = false;

            // Remove the scanRunnable if it exists
            mHandler.removeCallbacks(scanRunnable);
            btScanner.stopScan(mLeScanCallback);
        }

        // Reset the option menu
        invalidateOptionsMenu();
    }

    // Device scan callback.
    private ScanCallback mLeScanCallback =
            new ScanCallback() {

                @Override
                public void onScanResult(int callbackType, final ScanResult result) {
                    super.onScanResult(callbackType, result);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mDeviceListFragment.addDevice(result.getDevice());
                        }
                    });
                }
            };

    @Override
    public void onDeviceListItemSelected(BluetoothDevice device) {
        final Intent intent = new Intent();

        intent.putExtra(MainActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(MainActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());

        if (mScanning) {
            btScanner.stopScan(mLeScanCallback);
            mScanning = false;
        }

        setResult(RESULT_OK, intent);
        finish();
    }
}

