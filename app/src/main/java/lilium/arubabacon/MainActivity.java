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
import android.widget.ArrayAdapter;
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

        iBeaconAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, beacons);
        ListView debug = (ListView)findViewById(R.id.listView);
        debug.setAdapter(iBeaconAdapter);
    }

    //device discovery
    private android.bluetooth.le.ScanCallback ScanCallback = new android.bluetooth.le.ScanCallback(){
        @Override
        public void onScanResult(int callbackType, ScanResult result){
            iBeacon bacon = new iBeacon(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());

            if (bacon.isiBeacon) {
                if (beacons.contains(bacon)) {
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
