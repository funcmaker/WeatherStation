package com.bryanford.weatherstation;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class DeviceActivity extends ListActivity {
    private final static String TAG = DeviceActivity.class.getSimpleName();

    // Bluetooth request codes
    private static final int REQUEST_ENABLE_BT = 1;

    // Scanning timeout ms
    public static final long SCAN_PERIOD = 10000;

    private DeviceListAdapter mDeviceListAdapter;
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner btScanner;
    private boolean mScanning;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getActionBar() != null) {
            getActionBar().setTitle(R.string.list_item_device);  // App Theme allows this
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mHandler = new Handler();
        final BluetoothManager btManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();

        // Initializes list view adapter.
        mDeviceListAdapter = new DeviceListAdapter();
        setListAdapter(mDeviceListAdapter);
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
            mDeviceListAdapter.clear();
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

        // Initializes list view adapter.
        mDeviceListAdapter = new DeviceListAdapter();
        setListAdapter(mDeviceListAdapter);
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

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mDeviceListAdapter.getDevice(position);

        if (device == null)
            return;

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

    // Adapter for holding devices found through scanning
    private class DeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflater;

        public DeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflater = DeviceActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        public int getCount() {
            return mLeDevices.size();
        }

        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;

            // General ListView optimization code.
            if (view == null) {
                view = mInflater.inflate(R.layout.activity_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = (TextView) view.findViewById(R.id.text_device_name);
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                view.setTag(viewHolder);
            }
            else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            String deviceName = device.getName();

            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);

            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
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
                            mDeviceListAdapter.addDevice(result.getDevice());
                            mDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
