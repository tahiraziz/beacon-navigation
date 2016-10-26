package lilium.arubabacon;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PointF;
import android.os.Build;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;

public class MainActivity extends AppCompatActivity {
    SubsamplingScaleImageView map;
    ImageView newBeaconMarker;
    ListView beaconListView;

    BluetoothManager btManager;
    BluetoothAdapter btAdapter;

    SQLiteDatabase db = null;
    static ArrayList<iBeacon> beacons = new ArrayList<>();
    ArrayList<iBeacon> newBeacons = new ArrayList<>();
    ArrayAdapter<iBeacon> adapter;

    static PointF position = new PointF(0, 0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermissions();
    }

    void checkPermissions(){
        //android 6.0 requires runtime user permission (api level 23 required...)
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
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
                } else {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                }
            }

            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("This app needs filesystem access");
                    builder.setMessage("Please grant read/write access so this app can save and load the beacon database.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @TargetApi(23)
                        public void onDismiss(DialogInterface dialog) {
                            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                        }
                    });
                    builder.show();
                } else {
                    requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                }
            }

            //final check
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) checkBluetooth();
        } else {
            checkBluetooth();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        checkPermissions();
    }

    void checkBluetooth(){
        //get and enable BT adapter
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, 1);
        } else {
            setup();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            checkBluetooth();
        }
    }

    void setup() {
        //init database
        db = openOrCreateDatabase(Environment.getExternalStorageDirectory().getAbsolutePath() + "/beacons.db", Context.MODE_PRIVATE, null);
        db.execSQL("DROP TABLE beacons");
        db.execSQL("CREATE TABLE IF NOT EXISTS beacons(mac VARCHAR(12), x float, y float);");

        //device discovery for API level 21+
        if (Build.VERSION.SDK_INT >= 21) {
            ScanCallback ScanCallback = new ScanCallback() {
                @Override
                @TargetApi(21)
                public void onScanResult(int callbackType, final ScanResult result) {
                    //filter out anything that is not an Aruba
                    byte[] prefix = new byte[9];
                    System.arraycopy(result.getScanRecord().getBytes(), 0, prefix, 0, 9);
                    if (Arrays.equals(prefix, new byte[] {0x02,0x01,0x06,0x1a,(byte)0xff,0x4c,0x00,0x02,0x15})) {
                        updateBeacons(result.getDevice().getAddress().replace(":", ""), result.getRssi());
                        updatePosition();
                    }
                }
            };

            ArrayList<ScanFilter> filters = new ArrayList<>();
            ScanSettings settings =
                    new ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            btAdapter.getBluetoothLeScanner().startScan(filters, settings, ScanCallback);
        } else {
            //device discovery for API level 18-20, this is very slow
            BluetoothAdapter.LeScanCallback depScanCallback = new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
                    //filter out anything that is not an Aruba
                    byte[] prefix = new byte[9];
                    System.arraycopy(scanRecord, 0, prefix, 0, 9);
                    if (Arrays.equals(prefix, new byte[] {0x02,0x01,0x06,0x1a,(byte)0xff,0x4c,0x00,0x02,0x15})) {
                        updateBeacons(device.getAddress().replace(":", ""), rssi);
                        updatePosition();
                    }
                }
            };
            btAdapter.startLeScan(depScanCallback);
        }

        //map imageView
        map = (SubsamplingScaleImageView) findViewById(R.id.map);
        map.setImage(ImageSource.resource(R.mipmap.map));
        map.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                newBeaconMarker.setX(event.getX() - newBeaconMarker.getWidth() / 2);
                newBeaconMarker.setY(event.getY() - newBeaconMarker.getHeight() / 2);
                newBeaconMarker.setVisibility(View.VISIBLE);
                return false;
            }
        });

        newBeaconMarker = (ImageView) findViewById(R.id.newBeaconMarker);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!adapter.isEmpty()) {
                    beaconListView.setVisibility(View.VISIBLE);
                }else{
                    Snackbar.make(view, "There are no new configurable beacons nearby.", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });

        //new beacon list
        beaconListView = (ListView) findViewById(R.id.beaconListView);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, newBeacons);
        beaconListView.setAdapter(adapter);
        beaconListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
                PointF pos = map.viewToSourceCoord(newBeaconMarker.getX(), newBeaconMarker.getY());
                iBeacon beacon = newBeacons.get(index);
                beacon.x = pos.x;
                beacon.y = pos.y;
                db.execSQL("INSERT INTO beacons (mac, x, y) VALUES ('" + beacon.mac + "'," + pos.x + "," + pos.y + ")");
                beacons.add(beacon);
                newBeacons.remove(beacon);
                adapter.notifyDataSetChanged();
                beaconListView.setVisibility(View.INVISIBLE);
                map.invalidate();
            }
        });
    }

    void updateBeacons(String mac, int rssi) {
        Cursor c = db.rawQuery("SELECT * FROM beacons WHERE mac='" + mac + "'", null);
        if (c.getCount() > 0) {
            c.moveToFirst(); //this moves to the first row
            iBeacon beacon = new iBeacon(mac, rssi,
                    c.getFloat(c.getColumnIndex("x")), c.getFloat(c.getColumnIndex("y")));
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
                beacon = newBeacons.get(b);

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
                newBeacons.add(beacon);
            }
        }
        c.close();

        //remove beacons which have not been updated in a while
        long now = System.currentTimeMillis();
        int index = 0;
        while (index < beacons.size()) {
            if (now - beacons.get(index).lastUpdate > 1242) {
                beacons.remove(index);
            } else {
                index++;
            }
        }

        now = System.currentTimeMillis();
        index = 0;
        while (index < newBeacons.size()) {
            if (now - newBeacons.get(index).lastUpdate > 1242) {
                newBeacons.remove(index);
            } else {
                index++;
            }
        }
    }

    void updatePosition() {
        //https://github.com/lemmingapex/Trilateration
        if(beacons.size() >= 2) {
            double[][] positions = new double[beacons.size()][2];
            double[] distances = new double[beacons.size()];

            for (int i = 0; i < beacons.size(); i++) {
                positions[i][0] = beacons.get(i).x;
                positions[i][1] = beacons.get(i).y;
                //we want linear distances, the distance readings don't have to be accurate
                //they just need to be consistent across all beacons
                //because the trilateration function uses them as relative to each other
                distances[i] = Math.pow(10.0, (-61 - (beacons.get(i).cummulativeRssi / beacons.get(i).numRssi)) / (10.0 * 3.5));
                //Log.v("Lilium", Integer.toString(beacons.get(i).cummulativeRssi / beacons.get(i).numRssi) + " " + Double.toString(distances[i]));
            }

            NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
            LeastSquaresOptimizer.Optimum optimum = solver.solve();

            double[] calculatedPosition = optimum.getPoint().toArray();
            position = new PointF((float)calculatedPosition[0], (float)calculatedPosition[1]);
            //Log.v("Lilium", Double.toString(calculatedPosition[0]) + " " + Double.toString(calculatedPosition[1]));

            map.invalidate();
        }
    }
}
