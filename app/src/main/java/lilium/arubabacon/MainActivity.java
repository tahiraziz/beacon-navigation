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
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.*;
import android.database.sqlite.*;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;

public class MainActivity extends AppCompatActivity {
    final SQLiteDatabase db = openOrCreateDatabase("BeaconsDB", Context.MODE_PRIVATE, null);

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

        //get and enable BT adapter
        BluetoothManager btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,1);
        }


        db.execSQL("CREATE TABLE IF NOT EXISTS beacons(mac VARCHAR(12), x double, y double);");

        //device discovery for API level 21+
        if (Build.VERSION.SDK_INT >= 21) {
            android.bluetooth.le.ScanCallback ScanCallback= new android.bluetooth.le.ScanCallback() {
                @Override
                @TargetApi(21)
                public void onScanResult(int callbackType, ScanResult result) {
                    updateBeacons(result.getDevice().getAddress(), result.getRssi());
                    updatePosition();
                }
            };

            ArrayList<android.bluetooth.le.ScanFilter> filters = new ArrayList<>();
            android.bluetooth.le.ScanSettings settings =
                    new android.bluetooth.le.ScanSettings.Builder()
                            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            btAdapter.getBluetoothLeScanner().startScan(filters, settings, ScanCallback);
        } else {
            //device discovery for API level 18-20, this is very slow
            BluetoothAdapter.LeScanCallback depScanCallback = new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateBeacons(device.getAddress(), rssi);
                            updatePosition();
                        }
                    });
                }
            };

            btAdapter.startLeScan(depScanCallback);
        }
    }

    //update stuff when we receive new beacon data
    ArrayList<iBeacon> beacons = new ArrayList<>();
    ArrayList<iBeacon> newBeacons = new ArrayList<>();
    void updateBeacons(String mac, int rssi) {
        android.database.Cursor c = db.rawQuery("SELECT 1 FROM beacons WHERE mac='" + mac + "'", null);
        if (c.getCount() == 1) {
            iBeacon beacon = new iBeacon(mac, rssi, c.getDouble(1), c.getDouble(2));
            if (beacons.contains(beacon)) {
                int b = beacons.indexOf(beacon);
                beacon = beacons.get(b);

                long now = System.currentTimeMillis();
                beacon.advertInterval = now - beacon.lastUpdate;
                beacon.lastUpdate = now;

                //lower in this sense means closer to 0 from the negative side
                beacon.lowRssi = Math.max(rssi, beacon.lowRssi);

                //lower in this sense means further from 0 from the negative side
                beacon.highRssi = Math.min(rssi, beacon.highRssi);

                beacon.cummulativeRssi = beacon.cummulativeRssi + rssi;
                beacon.numRssi = beacon.numRssi + 1;

                beacons.set(b, beacon);
            } else {
                beacons.add(beacon);
            }
        } else {
            iBeacon beacon = new iBeacon(mac, rssi, -1, -1);
            if (newBeacons.contains(beacon)) {
                int b = newBeacons.indexOf(beacon);
                beacon = beacons.get(b);

                long now = System.currentTimeMillis();
                beacon.advertInterval = now - beacon.lastUpdate;
                beacon.lastUpdate = now;

                //lower in this sense means closer to 0 from the negative side
                beacon.lowRssi = Math.max(rssi, beacon.lowRssi);

                //lower in this sense means further from 0 from the negative side
                beacon.highRssi = Math.min(rssi, beacon.highRssi);

                beacon.cummulativeRssi = beacon.cummulativeRssi + rssi;
                beacon.numRssi = beacon.numRssi + 1;

                newBeacons.set(b, beacon);
            } else {
                beacons.add(beacon);
            }
        }

        //remove beacons which have not been updated in a while
        long now = System.currentTimeMillis();
        int index = 0;
        while(index < beacons.size()){
            if(now - beacons.get(index).lastUpdate > 1242){
                beacons.remove(index);
            } else {
                index++;
            }
        }

        now = System.currentTimeMillis();
        index = 0;
        while(index < newBeacons.size()){
            if(now - newBeacons.get(index).lastUpdate > 1242){
                newBeacons.remove(index);
            } else {
                index++;
            }
        }
    }

    void placeBeacon(double x, double y){
        int closest = 0;
        for (int i = 0; i < newBeacons.size(); i++){
            if (newBeacons.get(i).rssi > newBeacons.get(closest).rssi) closest = i;
        }
        iBeacon beacon = newBeacons.get(closest);
        beacon.x = x;
        beacon.y = y;
        beacons.add(beacon);
        db.execSQL("INSERT INTO beacons (mac, x, y) VALUES ("+beacon.mac+","+x+","+y+")");
        newBeacons.remove(closest);
    }

    void updatePosition(){
        //https://stackoverflow.com/questions/16485370/wifi-position-triangulation
        //https://en.wikipedia.org/wiki/Trilateration
        //https://github.com/lemmingapex/Trilateration

        double[][] positions = new double[beacons.size()][2];
        double[] distances = new double[beacons.size()];

        for (int i = 0; i < beacons.size(); i++){
            positions[i][0] = beacons.get(i).x;
            positions[i][1] = beacons.get(i).y;
            distances[i] = Math.pow(10.0, (-61 - beacons.get(i).rssi) / (10.0 * 2.0));
        }

        NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
        LeastSquaresOptimizer.Optimum optimum = solver.solve();

        double[] calculatedPosition = optimum.getPoint().toArray();

    }
}
