package com.bryanford.weatherstation;

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
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static java.lang.Math.pow;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothService extends Service {
    private static final String TAG = BluetoothService.class.getSimpleName();

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    // Static action string identifiers
    public final static String ACTION_GATT_CONNECTED =
            "com.bryanford.weatherstation.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.bryanford.weatherstation.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.bryanford.weatherstation.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.bryanford.weatherstation.ACTION_DATA_AVAILABLE";
    public final static String RX_DATA =
            "com.bryanford.weatherstation.RX_DATA";
    public final static String TEMP_ACT_DATA =
            "com.bryanford.weatherstation.TEMP_ACT_DATA";
    public final static String TEMP_IR_DATA =
            "com.bryanford.weatherstation.TEMP_IR_DATA";
    public final static String PRESS_DATA =
            "com.bryanford.weatherstation.PRESS_DATA";
    public final static String HUMID_DATA =
            "com.bryanford.weatherstation.HUMID_DATA";

    private final IBinder mBinder = new LocalBinder();

    private HashMap<UUID, BluetoothGattService> mGattServiceMap = new HashMap<>();
    private List<BluetoothGattService> mGattServices;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;

    private int mConnectionState = STATE_DISCONNECTED;
    private int[] mPressCalibration;
    private  int mState;

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;

                // Attempts to discover services after successful connection.
                Log.i(TAG, "Connected to GATT server.");
                Log.i(TAG, "Attempting to start service discovery: " +
                        mBluetoothGatt.discoverServices());

                broadcastUpdate(intentAction);
            }
            else if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;

                Log.i(TAG, "Disconnected from GATT server.");

                broadcastUpdate(intentAction);
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                // Disconnect on failed status
                mBluetoothGatt.disconnect();

                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;

                Log.i(TAG, "Disconnected from GATT server.");

                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Get all of the services
                mGattServices = mBluetoothGatt.getServices();

                for (BluetoothGattService s : mGattServices) {
                    // Add all of the services to a hash map for easier access
                    mGattServiceMap.put(s.getUuid(), s);
                }

                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                nextSensorNotify(mBluetoothGatt);
                broadcastUpdate(characteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(characteristic);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                nextSensorRead(mBluetoothGatt);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            nextSensorEnable(mBluetoothGatt, false);
        }
    };

    private void nextSensorEnable(BluetoothGatt gatt, boolean reset) {
        BluetoothGattCharacteristic characteristic;

        // Start at zero if reset is true else advance
        if (reset) { mState = 0; }
        else { mState++; }

        switch (mState) {
            case 0:
                // Enable pressure calibration
                characteristic = gatt.getService(DeviceTags.PRESSURE_SERVICE)
                        .getCharacteristic(DeviceTags.PRESSURE_CONFIG_CHAR);
                characteristic.setValue(new byte[]{0x02});
                break;
            case 1:
                // Enable pressure characteristic
                characteristic = gatt.getService(DeviceTags.PRESSURE_SERVICE)
                        .getCharacteristic(DeviceTags.PRESSURE_CONFIG_CHAR);
                characteristic.setValue(new byte[]{0x01});
                break;
            case 2:
                // Find humidity service and enable notifications
                characteristic = gatt.getService(DeviceTags.HUMIDITY_SERVICE)
                        .getCharacteristic(DeviceTags.HUMIDITY_CONFIG_CHAR);
                characteristic.setValue(new byte[]{0x01});
                break;
            default:
                Log.i(TAG, "All sensors enabled!");
                return;
        }

        writeCharacteristic(characteristic);
    }

    private void nextSensorRead(BluetoothGatt gatt) {
        BluetoothGattCharacteristic characteristic;

        switch (mState) {
            case 0:
                // Read pressure calibration
                characteristic = gatt.getService(DeviceTags.PRESSURE_SERVICE)
                        .getCharacteristic(DeviceTags.PRESSURE_CAL_CHAR);
                break;
            case 1:
                // Read pressure characteristic
                characteristic = gatt.getService(DeviceTags.PRESSURE_SERVICE)
                        .getCharacteristic(DeviceTags.PRESSURE_DATA_CHAR);
                break;
            case 2:
                // Find humidity service and read it
                characteristic = gatt.getService(DeviceTags.HUMIDITY_SERVICE)
                        .getCharacteristic(DeviceTags.HUMIDITY_DATA_CHAR);
                break;
            default:
                Log.i(TAG, "All sensors enabled!");
                return;
        }

        readCharacteristic(characteristic);
    }

    private void nextSensorNotify(BluetoothGatt gatt) {
        BluetoothGattCharacteristic characteristic;

        switch (mState) {
            case 0:
                // Enable pressure calibration
                characteristic = gatt.getService(DeviceTags.PRESSURE_SERVICE)
                        .getCharacteristic(DeviceTags.PRESSURE_CAL_CHAR);
                break;
            case 1:
                // Enable pressure characteristic
                characteristic = gatt.getService(DeviceTags.PRESSURE_SERVICE)
                        .getCharacteristic(DeviceTags.PRESSURE_DATA_CHAR);
                break;
            case 2:
                // Find humidity service and enable notifications
                characteristic = gatt.getService(DeviceTags.HUMIDITY_SERVICE)
                        .getCharacteristic(DeviceTags.HUMIDITY_DATA_CHAR);
                break;
            default:
                Log.i(TAG, "All sensors enabled!");
                return;
        }
        // Enable local notifications
        setCharacteristicNotification(characteristic, true);

        // Enable notification on device
        BluetoothGattDescriptor desc =
                characteristic.getDescriptor(DeviceTags.CLIENT_CONFIG_DESCRIPTOR2);
        desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        writeDescriptor(desc);
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final BluetoothGattCharacteristic characteristic) {
        final Intent intent;

        if (DeviceTags.PRESSURE_CAL_CHAR.equals(characteristic.getUuid())) {
            mPressCalibration = new int[8];

            // Extract the calibration data
            mPressCalibration[0] = shortUnsignedAtOffset(characteristic, 0);
            mPressCalibration[1] = shortUnsignedAtOffset(characteristic, 2);
            mPressCalibration[2] = shortUnsignedAtOffset(characteristic, 4);
            mPressCalibration[3] = shortUnsignedAtOffset(characteristic, 6);
            mPressCalibration[4] = shortUnsignedAtOffset(characteristic, 8);
            mPressCalibration[5] = shortUnsignedAtOffset(characteristic, 10);
            mPressCalibration[6] = shortUnsignedAtOffset(characteristic, 12);
            mPressCalibration[7] = shortUnsignedAtOffset(characteristic, 14);
        }
        else if (DeviceTags.PRESSURE_DATA_CHAR.equals(characteristic.getUuid())) {
            double press_raw, press_act, temp_raw;
            double S, O;

            if (mPressCalibration != null) {
                temp_raw = shortSignedAtOffset(characteristic, 0);
                press_raw = shortUnsignedAtOffset(characteristic, 2);

                /* temp_act = (100 * (mPressCalibration[0] * temp_raw / pow(2, 8) + mPressCalibration[1] * pow(2, 6))) / pow(2, 16); */
                S = mPressCalibration[2] + mPressCalibration[3] * temp_raw / pow(2, 17) + ((mPressCalibration[4] * temp_raw / pow(2, 15)) * temp_raw) / pow(2, 19);
                O = mPressCalibration[5] * pow(2, 14) + mPressCalibration[6] * temp_raw / pow(2, 3) + ((mPressCalibration[7] * temp_raw / pow(2, 15)) * temp_raw) / pow(2, 4);
                press_act = (S * press_raw + O) / pow(2, 14);

                Log.d(TAG, String.format("Press: %fin. Hg", press_act * 0.000296));

                intent = new Intent(PRESS_DATA);
                intent.putExtra(PRESS_DATA, press_act);
                sendBroadcast(intent);
            }
        }
        else if (DeviceTags.HUMIDITY_DATA_CHAR.equals(characteristic.getUuid())) {
            double temp = -46.85 + 175.72/65536 * (double)shortSignedAtOffset(characteristic, 0);
            double humid = shortUnsignedAtOffset(characteristic, 2);

            // Convert to F
            temp = (temp * 9 / 5) + 32;

            // Convert to %RH
            humid = humid - (humid % 4);
            humid = -6f + 125f * (humid / 65535f);

            Log.d(TAG, String.format("Temp: %f", temp));
            Log.d(TAG, String.format("Humid: %f", humid));

            intent = new Intent(HUMID_DATA);
            intent.putExtra(TEMP_ACT_DATA, temp);
            intent.putExtra(HUMID_DATA, humid);
            sendBroadcast(intent);
        }
        else if (DeviceTags.UART_CHAR.equals((characteristic.getUuid()))){
            final byte[] data = characteristic.getValue();

            if (data != null && data.length > 0) {
                intent = new Intent(RX_DATA);
                intent.putExtra(RX_DATA, new String(data));
                sendBroadcast(intent);
            }
        }
        else {
            // For all other profiles, writes the data as a string
            final byte[] data = characteristic.getValue();

            if (data != null && data.length > 0) {
                intent = new Intent(ACTION_DATA_AVAILABLE);
                intent.putExtra(ACTION_DATA_AVAILABLE, data.toString());
                sendBroadcast(intent);
            }
        }
    }

    public class LocalBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
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

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        final BluetoothDevice device;

        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Get the device from the connection
        device = mBluetoothAdapter.getRemoteDevice(address);

        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void writeDescriptor(BluetoothGattDescriptor descriptor) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.writeDescriptor(descriptor);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enable If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enable) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enable);
    }

    public  void startSensorEnable() {
        nextSensorEnable(mBluetoothGatt, true);
    }


    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    /**
     * Gyroscope, Magnetometer, Barometer, IR temperature
     * all store 16 bit two's complement values in the awkward format
     * LSB MSB, which cannot be directly parsed as getIntValue(FORMAT_SINT16, offset)
     * because the bytes are stored in the "wrong" direction.
     *
     * This function extracts these 16 bit two's complement values.
     * */
    public static Integer shortSignedAtOffset(BluetoothGattCharacteristic c, int offset) {
        Integer lowerByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, offset);
        Integer upperByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, offset + 1); // Note: interpret MSB as signed.

        return (upperByte << 8) + lowerByte;
    }

    public static Integer shortUnsignedAtOffset(BluetoothGattCharacteristic c, int offset) {
        Integer lowerByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset);
        Integer upperByte = c.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, offset + 1); // Note: interpret MSB as unsigned.

        return (upperByte << 8) + lowerByte;
    }
}
