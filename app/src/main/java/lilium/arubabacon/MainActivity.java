package lilium.arubabacon;

import android.Manifest;
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
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;


public class MainActivity extends AppCompatActivity {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                    }
                });
                builder.show();
            }
        }

        String[] PERMISSIONS_STORAGE = new String[] {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        // Check if we have write permission
        int permission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            requestPermissions(PERMISSIONS_STORAGE, 1);
        }

        //get and enable BT adapter
        btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,1);
        }

        iBeaconAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, beacons);
        ListView debug = (ListView)findViewById(R.id.listView);
        debug.setAdapter(iBeaconAdapter);

        final EditText pathLoss = (EditText) findViewById(R.id.pathLoss);
        pathLoss.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                //if (actionId == EditorInfo.IME_ACTION_DONE) {
                        ploss = Double.parseDouble(pathLoss.getText().toString());
                    return true;
                //}
                //return false;
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


        ToggleButton toggle = (ToggleButton) findViewById(R.id.toggleButton);
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked)
                {
                    btAdapter.getBluetoothLeScanner().startScan(ScanCallback);
                }
                else
                {
                    btAdapter.getBluetoothLeScanner().stopScan(ScanCallback);
                }
            }
        });

        toggle.setChecked(true);
    }

    //device discovery
    private android.bluetooth.le.ScanCallback ScanCallback = new android.bluetooth.le.ScanCallback(){
        @Override
        public void onScanResult(int callbackType, ScanResult result){
            iBeacon bacon = new iBeacon(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());

            if (bacon.isiBeacon) {
                if (beacons.contains(bacon)) {
                    //write to csv for power regression analysis
                    try {
                        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/rssi.csv");
                        if(!file.exists()) file.createNewFile();
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
