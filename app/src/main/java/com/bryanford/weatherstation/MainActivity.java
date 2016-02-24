package com.bryanford.weatherstation;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.Cast.MessageReceivedCallback;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.media.MediaRouter.RouteInfo;
import android.support.v4.view.MenuItemCompat;
import android.Manifest;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    // Logging
    private static final String TAG = MainActivity.class.getSimpleName();

    // Bt request codes
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_DEVICE = 2;
    private static final int PERMISSIONS_REQUEST_LOCATION = 3;

    private String mDeviceName;
    private String mDeviceAddress;
    private String mSessionId;
    private BluetoothAdapter btAdapter;
    private TextView tTemp, tHumid, tPress, tTempText;
    private TextView tDevice, tAddress, tState;
    private ActionBar tBar;
    private View tView;

    // Connection variables
    private HashMap<UUID, BluetoothGattService> mGattServiceMap = new HashMap<>();
    private List<BluetoothGattService> mGattServices;
    private BluetoothService mBluetoothService;

    // Media Router variables
    private MediaRouter.Callback mMediaRouterCallback;
    private MediaRouteSelector mMediaRouteSelector;
    private CastDevice mSelectedDevice;
    private MediaRouter mMediaRouter;
    private GoogleApiClient mApiClient;
    private Cast.Listener mCastListener;
    private GoogleApiClient.ConnectionCallbacks mConnectionCallbacks;
    private ConnectionFailedListener mConnectionFailedListener;
    private WeatherStationChannel mWeatherStationChannel;
    private boolean mApplicationStarted = false;
    private boolean mWaitingForReconnect = false;
    private boolean mBtConnected = false;


    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tBar = getSupportActionBar();
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
        tTemp = (TextView) findViewById(R.id.data_ambient_temp);
        tTempText = (TextView) findViewById(R.id.text_temp);
        tHumid = (TextView) findViewById(R.id.data_humid);
        tPress = (TextView) findViewById(R.id.data_press);
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

        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(getResources()
                        .getString(R.string.app_id))).build();
        mMediaRouterCallback = new MediaRouterCallback();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Start media router discovery
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
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

        MenuItem mediaRouterMenuItem = menu.findItem(R.id.media_route_menu_item);
        MediaRouteActionProvider mediaRouteActionProvider
                = (MediaRouteActionProvider) MenuItemCompat
                .getActionProvider(mediaRouterMenuItem);
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);

        if (mBtConnected) {
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
        return true;
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

                    // permission was granted, yay!
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Callback for MediaRouter events
     */
    private class MediaRouterCallback extends MediaRouter.Callback {

        @Override
        public void onRouteSelected(MediaRouter router, RouteInfo info) {
            Log.d(TAG, "onRouteSelected");
            // Handle the user route selection.
            mSelectedDevice = CastDevice.getFromBundle(info.getExtras());

            launchReceiver();
        }

        @Override
        public void onRouteUnselected(MediaRouter router, RouteInfo info) {
            Log.d(TAG, "onRouteUnselected: info=" + info);
            teardown(false);
            mSelectedDevice = null;
        }
    }

    /**
     * Start the receiver app
     */
    private void launchReceiver() {
        try {
            mCastListener = new Cast.Listener() {

                @Override
                public void onApplicationDisconnected(int errorCode) {
                    Log.d(TAG, "application has stopped");
                    teardown(true);
                }

            };
            // Connect to Google Play services
            mConnectionCallbacks = new ConnectionCallbacks();
            mConnectionFailedListener = new ConnectionFailedListener();
            Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                    .builder(mSelectedDevice, mCastListener);
            mApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Cast.API, apiOptionsBuilder.build())
                    .addConnectionCallbacks(mConnectionCallbacks)
                    .addOnConnectionFailedListener(mConnectionFailedListener)
                    .build();

            mApiClient.connect();
        } catch (Exception e) {
            Log.e(TAG, "Failed launchReceiver", e);
        }
    }

    /**
     * Google Play services callbacks
     */
    private class ConnectionCallbacks implements
            GoogleApiClient.ConnectionCallbacks {

        @Override
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected");

            if (mApiClient == null) {
                // We got disconnected while this runnable was pending
                // execution.
                return;
            }

            try {
                if (mWaitingForReconnect) {
                    mWaitingForReconnect = false;

                    // Check if the receiver app is still running
                    if ((connectionHint != null)
                            && connectionHint.getBoolean(Cast.EXTRA_APP_NO_LONGER_RUNNING)) {
                        Log.d(TAG, "App  is no longer running");
                        teardown(true);
                    } else {
                        // Re-create the custom message channel
                        try {
                            Cast.CastApi.setMessageReceivedCallbacks(
                                    mApiClient,
                                    mWeatherStationChannel.getNamespace(),
                                    mWeatherStationChannel);
                        } catch (IOException e) {
                            Log.e(TAG, "Exception while creating channel", e);
                        }
                    }
                } else {
                    // Launch the receiver app
                    Cast.CastApi.launchApplication(mApiClient, getString(R.string.app_id), false)
                            .setResultCallback(
                                    new ResultCallback<Cast.ApplicationConnectionResult>() {
                                        @Override
                                        public void onResult(
                                                Cast.ApplicationConnectionResult result) {
                                            Status status = result.getStatus();
                                            Log.d(TAG,
                                                    "ApplicationConnectionResultCallback.onResult:"
                                                            + status.getStatusCode());
                                            if (status.isSuccess()) {
                                                ApplicationMetadata applicationMetadata = result
                                                        .getApplicationMetadata();
                                                mSessionId = result.getSessionId();
                                                String applicationStatus = result
                                                        .getApplicationStatus();
                                                boolean wasLaunched = result.getWasLaunched();
                                                Log.d(TAG, "application name: "
                                                        + applicationMetadata.getName()
                                                        + ", status: " + applicationStatus
                                                        + ", sessionId: " + mSessionId
                                                        + ", wasLaunched: " + wasLaunched);
                                                mApplicationStarted = true;

                                                // Create the custom message
                                                // channel
                                                mWeatherStationChannel = new WeatherStationChannel();
                                                try {
                                                    Cast.CastApi.setMessageReceivedCallbacks(
                                                            mApiClient,
                                                            mWeatherStationChannel.getNamespace(),
                                                            mWeatherStationChannel);
                                                } catch (IOException e) {
                                                    Log.e(TAG, "Exception while creating channel",
                                                            e);
                                                }

                                                // set the initial instructions
                                                // on the receiver
                                                sendMessage(getString(R.string.app_name));
                                            } else {
                                                Log.e(TAG, "application could not launch");
                                                teardown(true);
                                            }
                                        }
                                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch application", e);
            }
        }

        @Override
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended");
            mWaitingForReconnect = true;
        }
    }

    /**
     * Google Play services callbacks
     */
    private class ConnectionFailedListener implements
            GoogleApiClient.OnConnectionFailedListener {

        @Override
        public void onConnectionFailed(ConnectionResult result) {
            Log.e(TAG, "onConnectionFailed ");

            teardown(false);
        }
    }

    /**
     * Tear down the connection to the receiver
     */
    private void teardown(boolean selectDefaultRoute) {
        Log.d(TAG, "teardown");
        if (mApiClient != null) {
            if (mApplicationStarted) {
                if (mApiClient.isConnected() || mApiClient.isConnecting()) {
                    try {
                        Cast.CastApi.stopApplication(mApiClient, mSessionId);
                        if (mWeatherStationChannel != null) {
                            Cast.CastApi.removeMessageReceivedCallbacks(
                                    mApiClient,
                                    mWeatherStationChannel.getNamespace());
                            mWeatherStationChannel = null;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Exception while removing channel", e);
                    }
                    mApiClient.disconnect();
                }
                mApplicationStarted = false;
            }
            mApiClient = null;
        }
        if (selectDefaultRoute) {
            mMediaRouter.selectRoute(mMediaRouter.getDefaultRoute());
        }
        mSelectedDevice = null;
        mWaitingForReconnect = false;
        mSessionId = null;
    }

    /**
     * Send a text message to the receiver
     */
    private void sendMessage(String message) {
        if (mApiClient != null && mWeatherStationChannel != null) {
            try {
                Cast.CastApi.sendMessage(mApiClient,
                        mWeatherStationChannel.getNamespace(), message).setResultCallback(
                        new ResultCallback<Status>() {
                            @Override
                            public void onResult(Status result) {
                                if (!result.isSuccess()) {
                                    Log.e(TAG, "Sending message failed");
                                }
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Exception while sending message", e);
            }
        } else {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Custom message channel
     */
    class WeatherStationChannel implements MessageReceivedCallback {

        /**
         * @return custom namespace
         */
        public String getNamespace() {
            return getString(R.string.namespace);
        }

        /*
         * Receive message from the receiver app
         */
        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace,
                                      String message) {
            Log.d(TAG, "onMessageReceived: " + message);
        }

    }

    /**
     * Service management
     */
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

    /**
     * Service receiver to get updates from the BLE GATT device server
     */
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            final String action = intent.getAction();

            if (BluetoothService.ACTION_GATT_CONNECTED.equals(action)) {
                mBtConnected = true;
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
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        tTemp.setText(intent.getStringExtra(BluetoothService.TEMP_DATA));
//                    }
//                });
            } else if (BluetoothService.HUMID_DATA.equals(action)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                double temp = intent.getDoubleExtra(BluetoothService.TEMP_DATA, 0.d);
                                double humid = intent.getDoubleExtra(BluetoothService.HUMID_DATA, 0.d);

                                tTemp.setText(String.format("%.0f %cF", temp, (char)0x00B0));
                                tHumid.setText(String.format("%.2f %%", humid));
                                adjustViewColorByTemp(tView, temp);
                            }
                        });
            } else if (BluetoothService.PRESS_DATA.equals(action)) {
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        tPress.setText(intent.getStringExtra(BluetoothService.PRESS_DATA));
//                    }
//                });
            } else if (BluetoothService.ACTION_DATA_AVAILABLE.equals(action)) {

            }
        }
    };

    /**
     * Follows a list of runnable statements to be called on the UI thread
     */
    private Runnable updateOnConnection = new Runnable() {
        @Override
        public void run() {
            tDevice.setText(" " + mDeviceName);
            tAddress.setText(" " + mDeviceAddress);
            tState.setText(" " + mBtConnected);

            // Reset the option menu
            invalidateOptionsMenu();
        }
    };

    private Runnable updateOnDisconnect = new Runnable() {
        @Override
        public void run() {
            mBtConnected = false;
            unregisterReceiver(mGattUpdateReceiver);
            unbindService(mServiceConnection);
            clearDisplayValues();

            // Reset the option menu
            invalidateOptionsMenu();
        }
    };

    private Runnable onDisconnect_Click = new Runnable() {
        @Override
        public void run() {
            // Cleanup the service connection and hide the disconnect button
            mBluetoothService.disconnect();

            // Wait until we have successful disconnect from the device
            while (mBluetoothService != null) {
                mBluetoothService = null;
            }
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
        intentFilter.addAction(BluetoothService.HUMID_DATA);
        intentFilter.addAction(BluetoothService.TEMP_DATA);
        intentFilter.addAction(BluetoothService.PRESS_DATA);
        return intentFilter;
    }

    private void clearDisplayValues() {
        tTempText.setTextColor(Color.parseColor("#FFFFFF"));
        tTemp.setText(R.string.dashes);
        tHumid.setText(R.string.dashes);
        tPress.setText(R.string.dashes);
        tDevice.setText("");
        tAddress.setText("");
        tState.setText(" " + mBtConnected);
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
        tTempText.setTextColor(Color.argb(alpha, red, grn, blu));
    }
}
