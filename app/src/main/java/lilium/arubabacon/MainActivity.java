package lilium.arubabacon;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.*;
import android.database.sqlite.*;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import java.util.ArrayList;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;

public class MainActivity extends AppCompatActivity {
    SQLiteDatabase db = null;
    ArrayList<iBeacon> beacons = new ArrayList<>();
    ArrayList<iBeacon> newBeacons = new ArrayList<>();

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

            String[] PERMISSIONS_STORAGE = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
            // Check if we have write permission
            int permission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                // We don't have permission so prompt the user
                requestPermissions(PERMISSIONS_STORAGE, 1);
            }
        }

        //get and enable BT adapter
        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();
        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, 1);
        }

        db = openOrCreateDatabase(Environment.getExternalStorageDirectory().getAbsolutePath() + "/beacons.db", Context.MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS beacons(mac VARCHAR(12), x double, y double);");

        //device discovery for API level 21+
        if (Build.VERSION.SDK_INT >= 21) {
            ScanCallback ScanCallback = new ScanCallback() {
                @Override
                @TargetApi(21)
                public void onScanResult(int callbackType, ScanResult result) {
                    updateBeacons(result.getDevice().getAddress().replace(":", ""), result.getRssi());
                    updatePosition();
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
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateBeacons(device.getAddress().replace(":", ""), rssi);
                            updatePosition();
                        }
                    });
                }
            };
            btAdapter.startLeScan(depScanCallback);
        }
    }

    void updateBeacons(String mac, int rssi) {
        Cursor c = db.rawQuery("SELECT * FROM beacons WHERE mac='" + mac + "'", null);
        if (c.getCount() > 0) {
            c.moveToFirst(); //this moves to the first row
            iBeacon beacon = new iBeacon(mac, rssi,
                    c.getDouble(c.getColumnIndex("x")), c.getDouble(c.getColumnIndex("y")));
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

        String debug = "";
        //remove beacons which have not been updated in a while
        long now = System.currentTimeMillis();
        int index = 0;
        while (index < beacons.size()) {
            if (now - beacons.get(index).lastUpdate > 1242) {
                beacons.remove(index);
            } else {
                debug += beacons.get(index).mac + " ";
                index++;
            }
        }

        debug += " - ";

        now = System.currentTimeMillis();
        index = 0;
        while (index < newBeacons.size()) {
            if (now - newBeacons.get(index).lastUpdate > 1242) {
                newBeacons.remove(index);
            } else {
                debug += newBeacons.get(index).mac + " ";
                index++;
            }
        }

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.map);
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        canvas.translate(0, 50);
        canvas.scale(4f, 4f);
        //canvas.drawBitmap(bitmap, null, new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), paint);
        canvas.drawText(debug, 0, 0, new Paint());

        canvas.drawText(dist, 0, 100, new Paint());

        ImageView imageView = (ImageView)findViewById(R.id.imageView);
        imageView.setImageBitmap(bitmap);
    }

    void placeBeacon(double x, double y) {
        int closest = 0;
        for (int i = 0; i < newBeacons.size(); i++) {
            if (newBeacons.get(i).rssi > newBeacons.get(closest).rssi) closest = i;
        }
        iBeacon beacon = newBeacons.get(closest);
        beacon.x = x;
        beacon.y = y;
        beacons.add(beacon);
        db.execSQL("INSERT INTO beacons (mac, x, y) VALUES ('" + beacon.mac + "'," + x + "," + y + ")");
        newBeacons.remove(closest);
    }

    String dist = "";
    void updatePosition() {
        //https://stackoverflow.com/questions/16485370/wifi-position-triangulation
        //https://en.wikipedia.org/wiki/Trilateration
        //https://github.com/lemmingapex/Trilateration

        if(beacons.size() >= 2) {
            double[][] positions = new double[beacons.size()][2];
            double[] distances = new double[beacons.size()];

            for (int i = 0; i < beacons.size(); i++) {
                positions[i][0] = beacons.get(i).x;
                positions[i][1] = beacons.get(i).y;
                //we want linear distances, the distance readings don't have to be accurate
                //they just need to be consistant across all beacons
                //because the trilateration function uses them as relative to each other
                distances[i] = Math.pow(10.0, (-61 - (beacons.get(i).cummulativeRssi / beacons.get(i).numRssi)) / (10.0 * 3.5));
                Log.v("Lilium", Integer.toString(beacons.get(i).cummulativeRssi / beacons.get(i).numRssi) + " " + Double.toString(distances[i]));
            }

            NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
            LeastSquaresOptimizer.Optimum optimum = solver.solve();

            double[] calculatedPosition = optimum.getPoint().toArray();
            dist = Double.toString(calculatedPosition[0]) + " " + Double.toString(calculatedPosition[1]);
            Log.v("Lilium", dist);
        }
    }
}
