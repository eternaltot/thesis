package mu.grad.thesis;

import android.Manifest;
import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

public class MainActivity extends ListActivity  {
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;

    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 456;
    private int i = 0;
    private File path;
    private FileOutputStream outputStream;
    private String filename = "log.txt";
    private ArrayList<String> list_device = new ArrayList<>();
    private int write_count = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        setContentView(R.layout.activity_main);
        path = Environment.getDataDirectory();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        list_device.add("3C:A3:08:97:E0:F9");
        list_device.add("3C:A3:08:97:E4:B0");
        list_device.add("3C:A3:08:97:DA:5A");
        list_device.add("3C:A3:08:97:E4:E3");
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        mBluetoothAdapter = bluetoothManager.getAdapter();
        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(i%2 ==0){
                    mLeDeviceListAdapter.clear();
                    scanLeDevice(true);
                }else{
                    scanLeDevice(false);
                }
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        // Initializes list view adapter.

        try {
            outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
            mLeDeviceListAdapter = new LeDeviceListAdapter(filename,outputStream);
            setListAdapter(mLeDeviceListAdapter);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


//        scanLeDevice(true);
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
        mLeDeviceListAdapter.clear();
        try {
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;

        if (mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private ArrayList<Double> mRssis;
        private LayoutInflater mInflator;
        String filename = "log.txt";
        FileOutputStream outputStream;

        public double calculateDistance(int rssi,int txPower){
            if(rssi == 0){
                return -1.0;
            }
            double pow_num = ((txPower - rssi)*1.0) / (10*4);
            double distance = Math.pow(10.0,pow_num);
            return distance;
        }

        public LeDeviceListAdapter(String filename,FileOutputStream outputStream) {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mRssis = new ArrayList<Double>();
            mInflator = MainActivity.this.getLayoutInflater();
            this.filename = filename;
            this.outputStream = outputStream;
        }

        public void addDevice(BluetoothDevice device,int rssi,int txPower) {
            double distance = calculateDistance(rssi,txPower);
            if(!mLeDevices.contains(device) && device.getName()!= null && device.getName().length() > 0) {
                mLeDevices.add(device);
                mRssis.add(Double.valueOf(distance));
            }
            if(mLeDevices.contains(device)){
                int index = mLeDevices.indexOf(device);
                mRssis.set(index,Double.valueOf(distance));
            }
            if(list_device.contains(device.getAddress())){
                try {
                    String fileContents = device.getAddress()+"\t"+String.valueOf(rssi)+"\t"+String.valueOf(distance)+"\n";
                    outputStream.write(fileContents.getBytes());
                    Log.d("Write to File","Write Success");
                    Log.d("Tx Power",String.valueOf(txPower));
                    Log.d("Write count",String.valueOf(write_count));
                    write_count++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                viewHolder.deviceRssi = (TextView) view.findViewById(R.id.device_rssi);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            Double distance = mRssis.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());
            viewHolder.deviceRssi.setText(distance.toString());
            return view;
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device,final int rssi,final byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            byte txpw = scanRecord[29];
                            mLeDeviceListAdapter.addDevice(device,rssi,(int)txpw);
                            mLeDeviceListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceRssi;
    }

}
