package lilium.arubabacon;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.*;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity {
    private android.bluetooth.le.ScanCallback ScanCallback;
    BluetoothManager btManager;
    BluetoothAdapter btAdapter;

    ArrayList<iBeacon> beacons = new ArrayList<>();
    ArrayAdapter<iBeacon> iBeaconAdapter;
    double ploss = 2.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //android 6.0 requires runtime user permission (api level 23 required...)
        if (Build.VERSION.SDK_INT >= 23) {
            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @TargetApi(23)
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                    }
                });
                builder.show();
            }

            String[] PERMISSIONS_STORAGE = new String[] {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
            // Check if we have write permission
            int permission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission so prompt the user
                requestPermissions(PERMISSIONS_STORAGE, 1);
            }
        }

        //device discovery for API level 21+
        if (Build.VERSION.SDK_INT >= 21) {
            ScanCallback = new android.bluetooth.le.ScanCallback() {
                @Override
                @TargetApi(21)
                public void onScanResult(int callbackType, ScanResult result) {
                    iBeacon bacon = new iBeacon(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());

                    if (bacon.isiBeacon) {
                        if (beacons.contains(bacon)) {
                            //write to csv for power regression analysis
                            try {
                                File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/rssi.csv");
                                if (!file.exists()) file.createNewFile();
                                FileOutputStream csv = new FileOutputStream(file, true);
                                csv.write((result.getDevice().getAddress() + "," + result.getRssi() + "\n").getBytes());
                                csv.close();
                            } catch (Exception e) {
                                System.out.println(e.getMessage());
                            }

                            //we need historical rssi and interval tracking
                            //this is a bad way to do it, but I hate object oriented programming

                            long now = System.currentTimeMillis();
                            bacon.advertInterval = now - beacons.get(beacons.indexOf(bacon)).lastUpdate;
                            bacon.lastUpdate = now;

                            //lower in this sense means closer to 0 from the negative side
                            bacon.lowRssi = Math.max(result.getRssi(), beacons.get(beacons.indexOf(bacon)).lowRssi);

                            bacon.cummulativeRssi = beacons.get(beacons.indexOf(bacon)).cummulativeRssi + result.getRssi();
                            bacon.numRssi = beacons.get(beacons.indexOf(bacon)).numRssi + 1;

                            //lower in this sense means further from 0 from the negative side
                            bacon.highRssi = Math.min(result.getRssi(), beacons.get(beacons.indexOf(bacon)).highRssi);

                            bacon.pathLoss = ploss;

                            //okay, we have all the data so lets init those distances
                            bacon.postInit();

                            beacons.set(beacons.indexOf(bacon), bacon);
                            iBeaconAdapter.notifyDataSetChanged();
                        } else {
                            beacons.add(bacon);
                            iBeaconAdapter.notifyDataSetChanged();
                        }
                    }
                }
            };
        }

        //get and enable BT adapter
        btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,1);
        }

        //beacon array/list
        iBeaconAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, beacons);
        ListView debug = (ListView)findViewById(R.id.listView);
        debug.setAdapter(iBeaconAdapter);

        //pathLoss for debugging distance equation
        final EditText pathLoss = (EditText) findViewById(R.id.pathLoss);
        Button setPathLoss = (Button) findViewById(R.id.setPathLoss);
        setPathLoss.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ploss = Double.parseDouble(pathLoss.getText().toString());
            }
        });

        Button reset = (Button) findViewById(R.id.reset);
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                beacons.clear();
                try {
                    File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/rssi.csv");
                    if(file.exists()) file.delete();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
                iBeaconAdapter.notifyDataSetChanged();
            }
        });

        if (Build.VERSION.SDK_INT >= 21) {
            ArrayList<android.bluetooth.le.ScanFilter> filters = new ArrayList<>();
            android.bluetooth.le.ScanSettings settings =
                    new android.bluetooth.le.ScanSettings.Builder()
                            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            btAdapter.getBluetoothLeScanner().startScan(filters, settings, ScanCallback);
        } else {
            btAdapter.startLeScan(depScanCallback);
        }
    }

    //device discovery for API level 18-20
    private BluetoothAdapter.LeScanCallback depScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    iBeacon bacon = new iBeacon(device, rssi, scanRecord);

                    if (bacon.isiBeacon) {
                        if (beacons.contains(bacon)) {
                            //write to csv for power regression analysis
                            try {
                                File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/rssi.csv");
                                if (!file.exists()) file.createNewFile();
                                FileOutputStream csv = new FileOutputStream(file, true);
                                csv.write((device.getAddress() + "," + rssi + "\n").getBytes());
                                csv.close();
                            } catch (Exception e) {
                                System.out.println(e.getMessage());
                            }

                            //we need historical rssi and interval tracking
                            //this is a bad way to do it, but I hate object oriented programming

                            long now = System.currentTimeMillis();
                            bacon.advertInterval = now - beacons.get(beacons.indexOf(bacon)).lastUpdate;
                            bacon.lastUpdate = now;

                            //lower in this sense means closer to 0 from the negative side
                            bacon.lowRssi = Math.max(rssi, beacons.get(beacons.indexOf(bacon)).lowRssi);

                            bacon.cummulativeRssi = beacons.get(beacons.indexOf(bacon)).cummulativeRssi + rssi;
                            bacon.numRssi = beacons.get(beacons.indexOf(bacon)).numRssi + 1;

                            //lower in this sense means further from 0 from the negative side
                            bacon.highRssi = Math.min(rssi, beacons.get(beacons.indexOf(bacon)).highRssi);

                            bacon.pathLoss = ploss;

                            //okay, we have all the data so lets init those distances
                            bacon.postInit();

                            beacons.set(beacons.indexOf(bacon), bacon);
                            iBeaconAdapter.notifyDataSetChanged();
                        } else {
                            beacons.add(bacon);
                            iBeaconAdapter.notifyDataSetChanged();
                        }
                    }
                }
            });
        }
    };
}
