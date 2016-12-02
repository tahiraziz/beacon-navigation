package lilium.arubabacon;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Build;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatTextView;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    static SubsamplingScaleImageView map;
    ImageView newBeaconMarker;
    ListView beaconListView;
    final long MAXIMUM_QUIET = 1300;
    final long MINIMUM_POSITION_DELAY = 200;
    static BluetoothManager btManager;
    static BluetoothAdapter btAdapter;
    AtomicBoolean AddingBeacon;
    static ArrayAdapter<iBeacon> adapter;
    static DataHandler dataHandler;
    static BeaconKeeper beaconKeeper;
    static BluetoothMonitor bluetoothMonitor;
    static PositionUpdater positionUpdater;
    static PointF position = new PointF(0, 0);
    static boolean notLoaded = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AddingBeacon = new AtomicBoolean();
        AddingBeacon.set(false);
        checkPermissions();
    }

    void checkPermissions(){
        //android 6.0 requires runtime user permission (api level 23 required...)
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            {
                checkBluetooth();
            }
            else{
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
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
        notLoaded = false;
        //init database
        dataHandler = new DataHandler();
        dataHandler.open(Environment.getExternalStorageDirectory().getAbsolutePath() + "/beacons.db");
        dataHandler.wipeDB();
        dataHandler.initDB();
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

        beaconKeeper = new BeaconKeeper(MAXIMUM_QUIET);
        beaconKeeper.start();

        bluetoothMonitor = new BluetoothMonitor();
        bluetoothMonitor.start();

        newBeaconMarker = (ImageView) findViewById(R.id.newBeaconMarker);

        FloatingActionButton placeBeacon = (FloatingActionButton) findViewById(R.id.placeBeacon);
        placeBeacon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(beaconListView.getVisibility() == View.INVISIBLE) {
                    if (!adapter.isEmpty()) {
                        beaconListView.setVisibility(View.VISIBLE);
                    } else {
                        Snackbar.make(view, "There are no new configurable beacons nearby.", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                    }
                } else {
                    beaconListView.setVisibility(View.INVISIBLE);
                }
            }
        });

        //new beacon list
        beaconListView = (ListView) findViewById(R.id.beaconListView);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, beaconKeeper.cloneUnplaced());
        beaconListView.setAdapter(adapter);
        beaconListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
                beaconKeeper.stop(); // watchdog
                PointF pos = map.viewToSourceCoord(newBeaconMarker.getX() + newBeaconMarker.getWidth() / 2, newBeaconMarker.getY() + newBeaconMarker.getHeight() / 2);
                //Since the ArrayList in the Adapter is constantly changing,
                //we can't trust that it will contain the item at index.
                //We only need the MAC address, so we can pull the string from the TextViews of the ArrayList
                String mac = ((AppCompatTextView) beaconListView.getChildAt(index)).getText().toString();
                dataHandler.addBeacon(mac,pos.x,pos.y);

                //Try to move the beacon from newBeacons to beacons, if it still exists
                //Create a new beacon so we don't have to reference and old one.
                beaconKeeper.placeBeacon(new iBeacon(mac,-1,pos.x,pos.y));

                adapter.notifyDataSetChanged();
                beaconListView.setVisibility(View.INVISIBLE);
                map.invalidate();

                beaconKeeper.start(); // watchdog
            }
        });

        positionUpdater = new PositionUpdater(MINIMUM_POSITION_DELAY);
    }


}
