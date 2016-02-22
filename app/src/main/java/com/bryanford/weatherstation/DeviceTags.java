package com.bryanford.weatherstation;

import java.util.HashMap;
import java.util.UUID;

/**
 * Created by Bryan on 2/22/2016.
 */
public class DeviceTags {
    // Lookup table for the descriptors
    private static HashMap<UUID, String> attributes = new HashMap<>();

    // IR Temperature
    public static final UUID IR_TEMP_SERVICE      = UUID.fromString("f000aa00-0451-4000-b000-000000000000");
    public static final UUID IR_TEMP_DATA_CHAR    = UUID.fromString("f000aa01-0451-4000-b000-000000000000");
    public static final UUID IR_TEMP_CONFIG_CHAR  = UUID.fromString("f000aa02-0451-4000-b000-000000000000");

    // Accelerometer
    public static final UUID ACCEL_SERVICE      = UUID.fromString("f000aa10-0451-4000-b000-000000000000");
    public static final UUID ACCEL_DATA_CHAR    = UUID.fromString("f000aa11-0451-4000-b000-000000000000");
    public static final UUID ACCEL_CONFIG_CHAR  = UUID.fromString("f000aa12-0451-4000-b000-000000000000");

    // Humidity
    public static final UUID HUMIDITY_SERVICE      = UUID.fromString("f000aa20-0451-4000-b000-000000000000");
    public static final UUID HUMIDITY_DATA_CHAR    = UUID.fromString("f000aa21-0451-4000-b000-000000000000");
    public static final UUID HUMIDITY_CONFIG_CHAR  = UUID.fromString("f000aa22-0451-4000-b000-000000000000");

    // Magnetometer
    public static final UUID MAGNET_SERVICE      = UUID.fromString("f000aa30-0451-4000-b000-000000000000");
    public static final UUID MAGNET_DATA_CHAR    = UUID.fromString("f000aa31-0451-4000-b000-000000000000");
    public static final UUID MAGNET_CONFIG_CHAR  = UUID.fromString("f000aa32-0451-4000-b000-000000000000");

    // Barometric
    public static final UUID PRESSURE_SERVICE      = UUID.fromString("f000aa40-0451-4000-b000-000000000000");
    public static final UUID PRESSURE_DATA_CHAR    = UUID.fromString("f000aa41-0451-4000-b000-000000000000");
    public static final UUID PRESSURE_CONFIG_CHAR  = UUID.fromString("f000aa42-0451-4000-b000-000000000000");
    public static final UUID PRESSURE_CAL_CHAR     = UUID.fromString("f000aa43-0451-4000-b000-000000000000");

    // Gyroscope
    public static final UUID GYRO_SERVICE      = UUID.fromString("f000aa50-0451-4000-b000-000000000000");
    public static final UUID GYRO_DATA_CHAR    = UUID.fromString("f000aa51-0451-4000-b000-000000000000");
    public static final UUID GYRO_CONFIG_CHAR  = UUID.fromString("f000aa52-0451-4000-b000-000000000000");

    // UART
    public static final UUID UART_SERVICE  = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    public static final UUID UART_CHAR     = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");

    // Misc
    public static final UUID GENERIC_SERVICE  = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb");
    public static final UUID SERVICE_CHANGED_CHAR     = UUID.fromString("00002a05-0000-1000-8000-00805f9b34fb");

    // Client Config Descriptor
    public static final UUID CLIENT_CONFIG_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID MANUFACTURER_NAME_STRING = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");

    public static final UUID DEVICE_INFO_SERVICE = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb");
    public static final UUID DEVICE_NAME_CHAR    = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");
    public static final UUID APPEARANCE_CHAR     = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb");
    public static final UUID PERPH_PRIC_FLAG     = UUID.fromString("00002a02-0000-1000-8000-00805f9b34fb");
    public static final UUID RECONN_ADDR         = UUID.fromString("00002a03-0000-1000-8000-00805f9b34fb");
    public static final UUID PERF_CONN_PARAM     = UUID.fromString("00002a04-0000-1000-8000-00805f9b34fb");

    static {
        // Services.
        attributes.put(DEVICE_INFO_SERVICE, "Device Information Service");
        attributes.put(GENERIC_SERVICE, "Generic GATT Service");
        attributes.put(IR_TEMP_SERVICE, "IR Temp Service");
        attributes.put(ACCEL_SERVICE, "Accelerometer Service");
        attributes.put(HUMIDITY_SERVICE, "Humidity Service");
        attributes.put(MAGNET_SERVICE, "Magnetometer Service");
        attributes.put(PRESSURE_SERVICE, "Pressure Service");
        attributes.put(GYRO_SERVICE, "Gyroscope Service");
        attributes.put(UART_SERVICE, "UART Service");

        // Characteristics.
        attributes.put(ACCEL_DATA_CHAR, "Accelerometer Data Characteristic");
        attributes.put(ACCEL_CONFIG_CHAR, "Accelerometer Configuration Characteristic");

        attributes.put(HUMIDITY_DATA_CHAR, "Humidity Data Characteristic");
        attributes.put(HUMIDITY_CONFIG_CHAR, "Humidity Configuration Characteristic");

        attributes.put(PRESSURE_DATA_CHAR, "Pressure Data Characteristic");
        attributes.put(PRESSURE_CONFIG_CHAR, "Pressure Configuration Characteristic");
        attributes.put(PRESSURE_CAL_CHAR, "Pressure Calibration Characteristic");

        // UART
        attributes.put(UART_CHAR, "UART Characteristic");

        // Descriptor
        attributes.put(CLIENT_CONFIG_DESCRIPTOR, "Client Configuration Descriptor");

        // Other
        attributes.put(MANUFACTURER_NAME_STRING, "Manufacturer Name String");
    }

    // Lookup method for finding service, char., or config string name
    public static String lookup(UUID uuid) {
        if (attributes.containsKey(uuid)) {
            return attributes.get(uuid);
        }
        else {
            return null;
        }
    }
}
