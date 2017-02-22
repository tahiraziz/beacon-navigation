package lilium.beaconnavigation;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatTextView;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import lilium.beaconnavigation.Classes.Location;
import lilium.beaconnavigation.Classes.MapGraph;
import lilium.beaconnavigation.Classes.Room;
import lilium.beaconnavigation.Enums.ActivityRequestCodeEnum;
import lilium.beaconnavigation.Implementations.MultiThreadedBeaconKeeper;
import lilium.beaconnavigation.Implementations.MultiThreadedPositionUpdater;
import lilium.beaconnavigation.Implementations.RssiAveragingBeacon;
import lilium.beaconnavigation.Implementations.StandardBluetoothMonitor;
import lilium.beaconnavigation.Interfaces.Beacon;
import lilium.beaconnavigation.Interfaces.BeaconKeeper;
import lilium.beaconnavigation.Interfaces.BluetoothMonitor;
import lilium.beaconnavigation.Interfaces.ImageProcessingService;
import lilium.beaconnavigation.Interfaces.PositionUpdater;
import lilium.beaconnavigation.Services.BasicImageProcessingService;
import lilium.beaconnavigation.Services.DBManager;
import lilium.beaconnavigation.Views.DrawableImageView;

public class MainActivity extends AppCompatActivity {

    //View references
    private ImageView newBeaconMarker;
    private ListView beaconListView;

    //Walking navigator variables
    Spinner rooms;
    TextView mapDimensText;
    public static SeekBar mapWidthSeekBar;
    public static SeekBar mapHeightSeekBar;

    private List<Location> path;

    private int source;
    private int dest;
    private MapGraph mapGraph;

    //Native objects for Bluetooth control
    public static BluetoothManager btManager;
    public static BluetoothAdapter btAdapter;

    //Non-native objects created for this app's purpose(s)
    public static BluetoothMonitor btMonitor;
    public static BeaconKeeper beaconKeeper;
    public static PositionUpdater positionUpdater;
    public static ArrayAdapter<Beacon> beaconArrayAdapter;
    public static DBManager dbManager;

    //Library
    public static DrawableImageView map;

    public static PointF position = new PointF(0, 0);
    public static ArrayList<String> availableDbFilePaths;

    public static boolean loaded = false;

    public MainActivity() {
    }


    //First thing that fires off in app:
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Run base class onCreate (standard Android startup code that may be defined in base class)
        super.onCreate(savedInstanceState);

        //Setup application configuration wrapper (appConfig can be statically accessed from anywhere in the app)
        AppConfig.SetupConfig(getPreferences(0));

        //Set the app's view to activity_main
        setContentView(R.layout.activity_main);

        //Make sure we have the permissions we need to run this app, if we don't exit the app.
        if(!initializePermissions()) {
            System.runFinalization();
            System.exit(0);
        };

        //Initialize our bluetooth services in the application so we can read BLE advertisements
        initializeBluetooth();
    }

    //This method is checks if we are using Android 6.0 or less
    //If we are, then it runs the native permission requesting functions for accessing location, reading external storage, and writing external storage
    //that the app needs in order to run
    boolean initializePermissions() {
        //Are we running less than Android 6.0? If so permissions are good so return true
        if (Build.VERSION.SDK_INT < 23)
        {
            return true;
        }

        //Check permissions (perhaps they are already good?) If they're good return true
        if(checkPermissions())
        {
            return true;
        }

        //Run the native android functionality for getting permissions for this app since permissions are not good
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

        //If they are good now return true
        if(checkPermissions())
        {
            return true;
        }

        //We have done everything we can, permissions are not accepted so return false
        return false;
    }

    //Checks for the permissions that we need in this app
    boolean checkPermissions() {
        if(Build.VERSION.SDK_INT < 23) {
            return true;
        }
        if (    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
        {
            return true;
        }

        return false;
    }

    void initializeBluetooth() {
        //First we need our Bluetooth Manager
        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        //Then we need our Bluetooth Adapter
        btAdapter = btManager.getAdapter();


        if (!btAdapter.isEnabled()) {
            //Once we get our adapter, we need to enable the adapter
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, 1);
        } else {
            //If initializeBluetooth was called from "onActivityResult" (the result of enabling it)
            //then it will be enabled and we will get here.
            setup();
        }
    }

    //Android 6.0 requires runtime user permission (api level 23 required...)
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        checkPermissions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCodeEnum.fromInt(requestCode))
        {
            case BlueToothActivity:
                initializeBluetooth();
                break;
        }

    }

    //This setup method runs after the Bluetooth has been enabled. It gives us handlers for all the buttons on the view
    void setup() {
        //dataHandler is used to do SQLLite database transactions (reading and writing data)
        dbManager = DBManager.getDBManager(this);

        //Get a reference to the SubSamplingScaleImageView in our view so we can do things with it
        map = (DrawableImageView)findViewById(R.id.map);

        //Walking navigator init
        rooms = (Spinner) findViewById(R.id.rooms);

        //Text box and two sliders to tweak the map width/map height constants to fit the screen correctly
        mapDimensText = (TextView) findViewById(R.id.mapdimenstext);
        mapWidthSeekBar = (SeekBar) findViewById(R.id.mapwidth);
        mapHeightSeekBar = (SeekBar) findViewById(R.id.mapheight);

        mapDimensText.setText("Map Width: " + mapWidthSeekBar.getProgress() + ", Map Height: " + mapHeightSeekBar.getProgress());

        mapWidthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mapDimensText.setText("Map Width: " + i + ", Map Height: " + mapHeightSeekBar.getProgress());
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        mapHeightSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mapDimensText.setText("Map Width: " + mapWidthSeekBar.getProgress() + ", Map Height: " + i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //Load in the map
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inMutable = true;

        Bitmap mapImage = BitmapFactory.decodeResource(getResources(), R.drawable.map, opts);

        ImageProcessingService imageProcessingService = new BasicImageProcessingService();

        int[][] wallPixelPositions = imageProcessingService.DeduceWallPxPositions(mapImage);

        //Set image and wall pixel postions int he DrawableImageView (custom setImage method)
        map.setImage(ImageSource.bitmap(mapImage),wallPixelPositions);

        //build DB
        mapGraph = new MapGraph(dbManager);
        mapGraph.buildGraph();
        source = 74;

        //set drop down list
        List<String> roomNo = new ArrayList<>();
        final List<Room> roomList = dbManager.queryRoom();
        for (Room s : roomList)
            roomNo.add(s.name);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, roomNo);
        rooms.setAdapter(adapter);
        rooms.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                dest = roomList.get(i).id;
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        position = new PointF(93,120); //the current location
        map.invalidate();

        //Do things when the map is touched
        map.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                newBeaconMarker.setX(event.getX());
                newBeaconMarker.setY(event.getY());
                newBeaconMarker.setVisibility(View.VISIBLE);
                return false;
            }
        });

        //Initialize the "BeaconKeeper" object and start it
        beaconKeeper = new MultiThreadedBeaconKeeper(AppConfig.get_maximum_quiet());
        beaconKeeper.start();

        //Initialize the "BluetoothMonitor" object and start it
        btMonitor = new StandardBluetoothMonitor();
        btMonitor.start();
//
//        //Send toast messages with rssi strengths:
//        Context context = getApplicationContext();
//        String text;
//        int duration = Toast.LENGTH_LONG, i=0;
//        Queue<Integer> rssiQueue=beaconHolder.getRssiQueue();
//        ArrayList<Integer> rssiList=new ArrayList();
//        while(rssiQueue.peek()!=null)
//        {
//            rssiList.add(i,rssiQueue.remove());
//            i++;
//        }
//
//        text="";
//        long time=0;
//
//        for(int j=0; j<rssiList.size(); j++)S
//        {
//            time= System.currentTimeMillis();
//            text+="RSSI Strengths: "+rssiList.get(j)+time+"\n";
//        }
//
//        Toast toast = Toast.makeText(context, text, duration);
//        toast.show();

        //Get a reference to the ImageView called newBeaconMarker from the main app view
        newBeaconMarker = (ImageView) findViewById(R.id.newBeaconMarker);

        //Get a reference to the beacon list of the main app view
        beaconListView = (ListView) findViewById(R.id.beaconListView);

        //Setup the array adapter to bind beacons to the main view's simple_list_item_1
        beaconArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, beaconKeeper.cloneUnplaced());

        //Setup the list view to use this array of beacons
        beaconListView.setAdapter(beaconArrayAdapter);

        //Setup an on click listener for the list view of beacons that will fire when they are clicked
        beaconListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
                //Stop watching beacons (so the list does not keep fluctuating?)
                beaconKeeper.stop(); // watchdog

                //Mark the position on the map of the beacon
                PointF pos = map.viewToSourceCoord(newBeaconMarker.getX() - map.getX() + newBeaconMarker.getWidth() / 2, newBeaconMarker.getY() - map.getY() + newBeaconMarker.getHeight() / 2);

                //Since the ArrayList in the Adapter is constantly changing,
                //we can't trust that it will contain the item at index.
                //We only need the MAC address, so we can pull the string from the TextViews of the ArrayList
                String mac = ((AppCompatTextView) beaconListView.getChildAt(index)).getText().toString();
                dbManager.addBeacon(mac, pos.x, pos.y);

                //Try to move the beacon from newBeacons to beacons, if it still exists
                //Create a new beacon so we don't have to reference an old one. Initial RSSI is -1
                beaconKeeper.placeBeacon(new RssiAveragingBeacon(mac, -1, pos.x, pos.y));

                beaconArrayAdapter.notifyDataSetChanged();
                beaconListView.setVisibility(View.INVISIBLE);
                map.invalidate();

                //Start watching beacons again
                beaconKeeper.start(); // watchdog
            }
        });

        //Hide the beacon list view
        beaconListView.setVisibility(View.INVISIBLE);

        //Get a reference to the AddBeaconButton on the main view
        ImageButton imageButton = (ImageButton) findViewById(R.id.AddBeaconButton);

        //Set the on click for the add beacon button
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (beaconListView.getVisibility() == View.INVISIBLE) {
                    if (!beaconArrayAdapter.isEmpty()) {
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

        //Get a reference to the remove beacon button
        imageButton = (ImageButton) findViewById(R.id.RemoveBeaconButton);

        //Set the on click for the remove beacon button
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PointF pos = map.viewToSourceCoord(newBeaconMarker.getX() + newBeaconMarker.getWidth() / 2, newBeaconMarker.getY() + newBeaconMarker.getHeight() / 2);

                Beacon nearestBeacon = beaconKeeper.nearestBeacon(pos.x, pos.y);
                if (nearestBeacon != null) {
                    dbManager.removeBeacon(nearestBeacon);
                    beaconKeeper.removeBeacon(nearestBeacon);
                }

                beaconArrayAdapter.notifyDataSetChanged();
                map.invalidate();
            }
        });

        //Get a reference to the wipe beacons button
        imageButton = (ImageButton) findViewById(R.id.WipeBeaconsButton);

        //Set the on click for the wipe beacons button
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PointF pos = map.viewToSourceCoord(newBeaconMarker.getX() + newBeaconMarker.getWidth() / 2, newBeaconMarker.getY() + newBeaconMarker.getHeight() / 2);

                Beacon nearestBeacon = beaconKeeper.nearestBeacon(pos.x, pos.y);
                if (nearestBeacon != null) {
                    dbManager.wipeBeacons();
                    beaconKeeper.wipeBeacons();
                }
                beaconArrayAdapter.notifyDataSetChanged();
                Toast.makeText(MainActivity.this, "Removed All Beacons", Toast.LENGTH_LONG).show();
                map.invalidate();
            }
        });

        //Instantiate the position updater
        positionUpdater = new MultiThreadedPositionUpdater(AppConfig.get_minimium_position_delay());
        positionUpdater.start();
        loaded = true;
    }

    private String getRealPathFromURI(Uri contentURI) {
        String result;
        Cursor cursor = getContentResolver().query(contentURI, null, null, null, null);
        if (cursor == null) { // Source is Dropbox or other similar local file path
            result = contentURI.getPath();
        } else {
            cursor.moveToFirst();
            int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            result = cursor.getString(idx);
            cursor.close();
        }
        return result;
    }

    public void getRoute(View view) {
        if(source==dest)
            return;
        path = mapGraph.getPath(source, dest);
        map.setPath(path);
        map.invalidate();
    }
}
