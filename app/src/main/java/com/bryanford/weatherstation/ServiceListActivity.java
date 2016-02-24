package com.bryanford.weatherstation;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.util.List;

public class ServiceListActivity extends ListActivity {
    // Constant to get bt service list to display
    public static final String EXTRA_LIST_VIEW
            = "com.bryanford.weatherstation.list_view";

    private List<BluetoothGattService> mGattServices;
    private BluetoothService mBluetoothService;
    private String gattServicesNames[];
    private ListAdapter listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setTitle(R.string.activity_service_list);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // Create the bind to the BluetoothService class
        Intent gattServiceIntent = new Intent(this, BluetoothService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mServiceConnection);
        mBluetoothService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_service_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }



    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        AlertDialog.Builder charDialog = new AlertDialog.Builder(this);
        BluetoothGattService service = mGattServices.get(position);
        String charListText = "";
        AlertDialog charNotify;
        int count = 0;


        // Build the output string
        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
            charListText += ++count + ". " + characteristic.getUuid().toString() + "\n";
        }

        charDialog.setTitle("Characteristics");
        charDialog.setMessage(charListText);
        charDialog.setCancelable(true);

        charNotify = charDialog.create();
        charNotify.show();
    }

    // Service management
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mBluetoothService = ((BluetoothService.LocalBinder) iBinder).getService();

            if (!mBluetoothService.initialize()) {
                finish();
            }
            else {
                String gattLookup;
                int i = 0;

                mGattServices = mBluetoothService.getSupportedGattServices();
                gattServicesNames = new String[mGattServices.size()];

                for (BluetoothGattService gatt : mGattServices) {
                    gattLookup = DeviceTags.lookup(gatt.getUuid());

                    if (gattLookup != null) {
                        gattServicesNames[i++] = i + ". " + gattLookup;
                    }
                    else {
                        gattServicesNames[i++] = i + ". " + gatt.getUuid().toString();
                    }
                }

                listAdapter = new ArrayAdapter<>(ServiceListActivity.this, R.layout.activity_service_list, gattServicesNames);
                setListAdapter(listAdapter);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothService = null;
        }
    };
}
