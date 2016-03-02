package com.bryanford.weatherstation;

import android.app.ListFragment;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

public class DeviceListFragment extends ListFragment {
    private OnItemSelectedListener listener;
    private DeviceListAdapter mDeviceListAdapter;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnItemSelectedListener) {
            listener = (OnItemSelectedListener) context;
        } else {
            throw new ClassCastException(context.toString()
                    + " must implement MyListFragment.OnItemSelectedListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initializes list view adapter.
        mDeviceListAdapter = new DeviceListAdapter();
        setListAdapter(mDeviceListAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();

        // Initializes list view adapter.
        mDeviceListAdapter = new DeviceListAdapter();
        setListAdapter(mDeviceListAdapter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        listener = null;
    }

    public interface OnItemSelectedListener {
        void onDeviceListItemSelected(BluetoothDevice device);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mDeviceListAdapter.getDevice(position);

        if (device == null)
            return;

        // Update the activity with the selected device
        listener.onDeviceListItemSelected(device);
    }

    // Calling method for device activity to add new devices
    public void addDevice(BluetoothDevice device) {
        mDeviceListAdapter.addDevice(device);
        mDeviceListAdapter.notifyDataSetChanged();
    }

    // Calling method to clear the list adapter
    public void clearDevices() {
        mDeviceListAdapter.clear();
    }

    // Adapter for holding devices found through scanning
    private class DeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflater;

        public DeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<>();
            mInflater = getActivity().getLayoutInflater();
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
                view = mInflater.inflate(R.layout.fragment_device_list, null);
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

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
