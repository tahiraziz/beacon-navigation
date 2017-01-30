package lilium.beaconnavigation;

import android.Manifest;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatTextView;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.io.File;
import java.util.ArrayList;

import lilium.beaconnavigation.Enums.ActivityRequestCodeEnum;
import lilium.beaconnavigation.Implementations.RssiAveragingBeacon;
import lilium.beaconnavigation.Implementations.MultiThreadedBeaconKeeper;
import lilium.beaconnavigation.Implementations.MultiThreadedPositionUpdater;
import lilium.beaconnavigation.Implementations.SqlLiteDataHandler;
import lilium.beaconnavigation.Implementations.StandardBluetoothMonitor;
import lilium.beaconnavigation.Interfaces.Beacon;
import lilium.beaconnavigation.Interfaces.BeaconKeeper;
import lilium.beaconnavigation.Interfaces.BluetoothMonitor;
import lilium.beaconnavigation.Interfaces.DataHandler;
import lilium.beaconnavigation.Interfaces.PositionUpdater;
import lilium.beaconnavigation.R;

import static lilium.beaconnavigation.Enums.ActivityRequestCodeEnum.ImageSelectActivity;

public class MainActivity extends AppCompatActivity {

    //View references
    private ImageView newBeaconMarker;
    private ListView beaconListView;
    private String newMapName;

    //Native objects for Bluetooth control
    public static BluetoothManager btManager;
    public static BluetoothAdapter btAdapter;

    //Non-native objects created for this app's purpose(s)
    public static BluetoothMonitor btMonitor;
    public static BeaconKeeper beaconKeeper;
    public static PositionUpdater positionUpdater;
    public static ArrayAdapter<Beacon> beaconArrayAdapter;
    public static DataHandler dataHandler;

    //Library
    public static SubsamplingScaleImageView map;

    public static PointF position = new PointF(0, 0);
    public static ArrayList<String> availableDbFilePaths;

    public static boolean diag_resolved;
    public static boolean loaded = false;



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
            case ImageSelectActivity:
                if (data == null) {
                    //Display an error
                    return;
                }
                File file = new File(getRealPathFromURI(data.getData()));
                if (!file.exists()) {
                    Toast.makeText(MainActivity.this, "File does not exist", Toast.LENGTH_LONG).show();
                    LoadMap(dataHandler.getIsOpen());
                } else {
                    dataHandler.newMap(newMapName, getFilesDir(), file);
                    setMap(dataHandler.getMap());
                    AppConfig.set_last_map_filename(newMapName);
                }
                break;
        }

    }

    void setMap(byte[] bytes) {
        map.setImage(ImageSource.bitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.length)));
    }

    //This setup method runs after the Bluetooth has been enabled. It gives us handlers for all the buttons on the view
    void setup() {
        //init database
        newMapName = new String();

        //dataHandler is used to do SQLLite database transactions (reading and writing data)
        dataHandler = new SqlLiteDataHandler();

        //Get a reference to the SubsamplingScaleImageView in our view so we can do things with it
        map = (SubsamplingScaleImageView) findViewById(R.id.map);

        //Do things when the map is touched
        map.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                newBeaconMarker.setX(event.getX() - newBeaconMarker.getWidth() / 2);
                newBeaconMarker.setY(event.getY() - newBeaconMarker.getHeight() / 2);
                newBeaconMarker.setVisibility(View.VISIBLE);
                return false;
            }
        });

        //Run GetLastMap (this sets "map" static field to the last loaded bitmap from the sqlite db on the device
        //if it returns 0, then that means there has been no map in the past, so we will LoadMap (this will force the user to choose a map if one doesn't exist)
        if (!GetLastMap())
            LoadMap(true);

        //Initialize the "BeaconKeeper" object and start it
        beaconKeeper = new MultiThreadedBeaconKeeper(AppConfig.get_maximum_quiet());
        beaconKeeper.start();

        //Initialize the "BluetoothMonitor" object and start it
        btMonitor = new StandardBluetoothMonitor();
        btMonitor.start();

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
                dataHandler.addBeacon(mac, pos.x, pos.y);

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
                    dataHandler.removeBeacon(nearestBeacon);
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
                    dataHandler.wipeDB();
                    beaconKeeper.wipeBeacons();
                }
                beaconArrayAdapter.notifyDataSetChanged();
                Toast.makeText(MainActivity.this, "Removed All Beacons", Toast.LENGTH_LONG).show();
                map.invalidate();
            }
        });

        //Get a reference to the open map button
        imageButton = (ImageButton) findViewById(R.id.OpenMapButton);

        //Set the on click for the open map button
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CreateNewMap();
            }
        });

        //Get a reference to the load map button
        imageButton = (ImageButton) findViewById(R.id.LoadMapButton);

        //Set the on click for the load map button
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LoadMap(false);
            }
        });

        //Get a reference to the delete map button
        imageButton = (ImageButton) findViewById(R.id.DeleteMapButton);

        //Set the on click for the delete map button
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DeleteMap();
            }
        });

        //Instantiate the position updater
        positionUpdater = new MultiThreadedPositionUpdater(AppConfig.get_minimium_position_delay());
        positionUpdater.start();
        loaded = true;
    }


    //Sets the static property "map" to the last record of the map from the sqlite db on the device,
    //if the file does not exist in preferences, return false
    boolean GetLastMap() {
        String lastMapFileName = AppConfig.get_last_map_filename();
        if (lastMapFileName != null) {
            String lastMap = String.format("%s/%s", getFilesDir(), lastMapFileName);
            File filename = new File(lastMap);
            if (filename.exists()) {
                dataHandler.open(lastMap);
                setMap(dataHandler.getMap());
                return true;
            }
        }
        return false;
    }

    //Start the create new map process
    void CreateNewMap() {
        NewMapDialog().show();
    }

    //The dialog to create a new map
    public Dialog NewMapDialog() {
        final Dialog dialog = new Dialog(MainActivity.this);
        diag_resolved = false;

        //The view is set to the newmap
        dialog.setContentView(R.layout.newmap);
        dialog.setTitle("Add New Map");
        Button button = (Button) dialog.findViewById(R.id.select_map_button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                EditText edit = (EditText) dialog.findViewById(R.id.mapName);
                String text = edit.getText().toString();

                //Pick a file to use for a map
                newMapName = text;
                if (newMapName.length() > 0) {
                    diag_resolved = true;
                    Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    getIntent.setType("image/*");

                    Intent pickIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    pickIntent.setType("image/*");

                    Intent chooserIntent = Intent.createChooser(getIntent, "Select Image");
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{pickIntent});

                    startActivityForResult(chooserIntent, ImageSelectActivity.value());
                }

                dialog.cancel();
            }

        });
        /*dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                if (!diag_resolved) {
                    if (!dataHandler.is_open) {
                        Toast.makeText(MainActivity.this, "You Must Create a Map to Continue", Toast.LENGTH_LONG).show();
                        LoadMap(true);
                    }
                    else {
                        Toast.makeText(MainActivity.this, "Cancelled", Toast.LENGTH_LONG).show();
                    }
                }else{diag_resolved = false;}
            }
        });*/

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if (!diag_resolved) {
                    if (!dataHandler.getIsOpen()) {
                        Toast.makeText(MainActivity.this, "You Must Create a Map to Continue", Toast.LENGTH_LONG).show();
                        LoadMap(true);
                    } else {
                        Toast.makeText(MainActivity.this, "Cancelled", Toast.LENGTH_LONG).show();
                    }
                } else {
                    diag_resolved = false;
                }
            }
        });
        return dialog;
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

    //Tries to get the map to use
    private void LoadMap(final boolean ForceSelect) {
        //gets the filepaths of all files ending in .db from the files directory for this application
        availableDbFilePaths = dataHandler.availableDbs(getFilesDir().getPath());

        if (availableDbFilePaths.size() > 0) {
            //pick a db file to use to get the maps using an alert dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            CharSequence[] cs = availableDbFilePaths.toArray(new CharSequence[availableDbFilePaths.size()]);
            builder.setTitle("Pick a Map")
                    .setItems(cs, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // The 'which' argument contains the index position
                            // of the selected item
                            dataHandler.open(String.format("%s/%s.db", getFilesDir(), availableDbFilePaths.get(which)));
                            setMap(dataHandler.getMap());
                            AppConfig.set_last_map_filename(availableDbFilePaths.get(which));
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            if (!ForceSelect)
                                Toast.makeText(MainActivity.this, "Cancelled", Toast.LENGTH_LONG).show();
                            else
                                CreateNewMap();
                        }
                    });
            Dialog dialog = builder.create();
            dialog.show();
            //last one opened?
        } else {
            //If there were no available db file paths
            if (!ForceSelect) {
                //There are no available maps
                Toast.makeText(MainActivity.this, "No Available Maps", Toast.LENGTH_LONG).show();
            }
            else {
                //Create a new map
                CreateNewMap();
            }
        }
    }

    private void DeleteMap() {

        availableDbFilePaths = dataHandler.availableDbs(getFilesDir().getPath());
        if (
        availableDbFilePaths.size() > 0) {
            //pick one
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            CharSequence[] cs =
        availableDbFilePaths.toArray(new CharSequence[
        availableDbFilePaths.size()]);
            builder.setTitle("Delete a Map")
                    .setItems(cs, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // The 'which' argument contains the index position
                            // of the selected item
                            if (dataHandler.getMapName().equals(
        availableDbFilePaths.get(which))) {
                                dataHandler.close();
                                map.setImage(ImageSource.resource(R.mipmap.black));
                                beaconKeeper.wipeBeacons();
                                File file = new File(String.format("%s/%s.db", getFilesDir(),
        availableDbFilePaths.get(which)));
                                if (file.exists())
                                    file.delete();
                                LoadMap(true);
                            } else {
                                File file = new File(String.format("%s/%s.db", getFilesDir(),
        availableDbFilePaths.get(which)));
                                if (file.exists())
                                    file.delete();
                            }

                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            Toast.makeText(MainActivity.this, "Cancelled", Toast.LENGTH_LONG).show();
                        }
                    });
            Dialog dialog = builder.create();
            dialog.show();
        } else {
            Toast.makeText(MainActivity.this, "No Availible Maps", Toast.LENGTH_LONG).show();
        }
    }
}
