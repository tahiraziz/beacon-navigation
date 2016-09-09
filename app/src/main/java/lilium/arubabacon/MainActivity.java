package lilium.arubabacon;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.*;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    BluetoothManager btManager;
    BluetoothAdapter btAdapter;

    ArrayList<iBeacon> beacons = new ArrayList<>();
    ArrayAdapter<iBeacon> iBeaconAdapter;

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
                                                     requestPermissions(new String[]{
                                                             Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                                                 }
                });
                builder.show();
            }
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

        Button reset = (Button) findViewById(R.id.reset);
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                beacons.clear();
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
