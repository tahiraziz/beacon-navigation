package lilium.arubabacon;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatTextView;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static android.R.attr.accessibilityEventTypes;
import static android.R.attr.bitmap;
import static android.R.attr.data;

public class MainActivity extends AppCompatActivity {
    static SubsamplingScaleImageView map;
    ImageView newBeaconMarker;
    ListView beaconListView;
    final long MAXIMUM_QUIET = 1300;
    final long MINIMUM_POSITION_DELAY = 200;
    static BluetoothManager btManager;
    static BluetoothAdapter btAdapter;
    static ArrayAdapter<iBeacon> adapter;
    static DataHandler dataHandler;
    static BeaconKeeper beaconKeeper;
    static BluetoothMonitor bluetoothMonitor;
    static PositionUpdater positionUpdater;
    static PointF position = new PointF(0, 0);
    static boolean notLoaded = true;
    static final String PREFS_NAME = "LastMap";
    static SharedPreferences preferences;
    static ArrayList<String> available;
    final int BLUETOOTH_ACTIVITY = 1;
    final int IMAGE_SELECT_ACTIVITY = 2;
    static boolean diag_resolved;

    String newMapName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkPermissions();
    }

    boolean GetLastMap(){
        preferences = getSharedPreferences(PREFS_NAME,0);
        if(preferences.contains("lastMap")){
            String lastMap = String.format("%s/%s", getFilesDir(),preferences.getString("lastMap",null));
            File filename = new File(lastMap);
            if (filename.exists()) {
                dataHandler.open(lastMap);
                setMap(dataHandler.getMap());
                return true;
            }
        }
        return false;
    }

    void SetLastMap(String str){
        preferences = getSharedPreferences(PREFS_NAME,0);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("lastMap",str);
        editor.commit();
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
        switch (requestCode) {
            case BLUETOOTH_ACTIVITY:
                checkBluetooth();
                break;
            case IMAGE_SELECT_ACTIVITY:
                if (data == null) {
                    //Display an error
                    return;
                }
                File file = new File(getRealPathFromURI(data.getData()));
                if (!file.exists()) {
                    Toast.makeText(MainActivity.this,"File does not exist", Toast.LENGTH_LONG).show();
                    LoadMap(dataHandler.is_open);
                } else {
                    dataHandler.newMap(newMapName, getFilesDir(), file);
                    setMap(dataHandler.getMap());
                    SetLastMap(newMapName);
                }
                break;
        }

    }

    void setMap(byte[] bytes){
        map.setImage(ImageSource.bitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.length)));
    }

    void setup() {
        //init database
        newMapName = new String();
        dataHandler = new DataHandler();
        map = (SubsamplingScaleImageView) findViewById(R.id.map);
        //map.setImage(ImageSource.resource(R.mipmap.map));
        map.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                newBeaconMarker.setX(event.getX() - newBeaconMarker.getWidth() / 2);
                newBeaconMarker.setY(event.getY() - newBeaconMarker.getHeight() / 2);
                newBeaconMarker.setVisibility(View.VISIBLE);
                return false;
            }
        });

        if(!GetLastMap())
            LoadMap(true);
        beaconKeeper = new BeaconKeeper(MAXIMUM_QUIET);
        beaconKeeper.start();

        bluetoothMonitor = new BluetoothMonitor();
        bluetoothMonitor.start();

        newBeaconMarker = (ImageView) findViewById(R.id.newBeaconMarker);

        //new beacon list
        beaconListView = (ListView) findViewById(R.id.beaconListView);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, beaconKeeper.cloneUnplaced());
        beaconListView.setAdapter(adapter);
        beaconListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
                beaconKeeper.stop(); // watchdog
                PointF pos = map.viewToSourceCoord(newBeaconMarker.getX() - map.getX() + newBeaconMarker.getWidth() / 2, newBeaconMarker.getY() -map.getY() + newBeaconMarker.getHeight() / 2);
                //Since the ArrayList in the Adapter is constantly changing,
                //we can't trust that it will contain the item at index.
                //We only need the MAC address, so we can pull the string from the TextViews of the ArrayList
                String mac = ((AppCompatTextView) beaconListView.getChildAt(index)).getText().toString();
                dataHandler.addBeacon(mac, pos.x, pos.y);

                //Try to move the beacon from newBeacons to beacons, if it still exists
                //Create a new beacon so we don't have to reference and old one.
                beaconKeeper.placeBeacon(new iBeacon(mac, -1, pos.x, pos.y));

                adapter.notifyDataSetChanged();
                beaconListView.setVisibility(View.INVISIBLE);
                map.invalidate();
                beaconKeeper.start(); // watchdog
            }
        });

        beaconListView.setVisibility(View.INVISIBLE);
        ImageButton imageButton = (ImageButton) findViewById(R.id.AddBeaconButton);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (beaconListView.getVisibility() == View.INVISIBLE) {
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

        imageButton = (ImageButton) findViewById(R.id.RemoveBeaconButton);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PointF pos = map.viewToSourceCoord(newBeaconMarker.getX() + newBeaconMarker.getWidth() / 2, newBeaconMarker.getY() + newBeaconMarker.getHeight() / 2);

                iBeacon nearestBeacon = beaconKeeper.nearestBeacon(pos.x, pos.y);
                if (nearestBeacon != null) {
                    dataHandler.removeBeacon(nearestBeacon);
                    beaconKeeper.removeBeacon(nearestBeacon);
                }
                adapter.notifyDataSetChanged();
                map.invalidate();
            }
        });

        imageButton = (ImageButton) findViewById(R.id.WipeBeaconsButton);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PointF pos = map.viewToSourceCoord(newBeaconMarker.getX() + newBeaconMarker.getWidth() / 2, newBeaconMarker.getY() + newBeaconMarker.getHeight() / 2);

                iBeacon nearestBeacon = beaconKeeper.nearestBeacon(pos.x, pos.y);
                if (nearestBeacon != null) {
                    dataHandler.wipeDB();
                    beaconKeeper.wipeBeacons();
                }
                adapter.notifyDataSetChanged();
                Toast.makeText(MainActivity.this,"Removed All Beacons", Toast.LENGTH_LONG).show();
                map.invalidate();
            }
        });


        imageButton = (ImageButton) findViewById(R.id.OpenMapButton);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CreateNewMap();
            }
        });


        imageButton = (ImageButton) findViewById(R.id.LoadMapButton);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LoadMap(false);
            }
        });

        imageButton = (ImageButton) findViewById(R.id.DeleteMapButton);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DeleteMap();
            }
        });

        positionUpdater = new PositionUpdater(MINIMUM_POSITION_DELAY);
        notLoaded = false;
    }

    void CreateNewMap(){
        NewMapDialog().show();
    }

    public Dialog NewMapDialog() {
        final Dialog dialog = new Dialog(MainActivity.this);
        diag_resolved = false;
        dialog.setContentView(R.layout.newmap);
        dialog.setTitle("Add New Map");
        Button button = (Button) dialog.findViewById(R.id.select_map_button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                EditText edit=(EditText)dialog.findViewById(R.id.mapName);
                String text=edit.getText().toString();



                newMapName=text;
                if (newMapName.length() > 0) {
                    diag_resolved = true;
                    Intent getIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    getIntent.setType("image/*");

                    Intent pickIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    pickIntent.setType("image/*");

                    Intent chooserIntent = Intent.createChooser(getIntent, "Select Image");
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{pickIntent});

                    startActivityForResult(chooserIntent, IMAGE_SELECT_ACTIVITY);
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
                    if (!dataHandler.is_open) {
                        Toast.makeText(MainActivity.this, "You Must Create a Map to Continue", Toast.LENGTH_LONG).show();
                        LoadMap(true);
                    } else {
                        Toast.makeText(MainActivity.this, "Cancelled", Toast.LENGTH_LONG).show();
                    }
                }else{diag_resolved = false;}
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

    private void LoadMap(final boolean ForceSelect){
        available = dataHandler.availibleDBs(getFilesDir().getPath());
        if (available.size() > 0) {
            //pick one
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            CharSequence[] cs = available.toArray(new CharSequence[available.size()]);
            builder.setTitle("Pick a Map")
                    .setItems(cs, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // The 'which' argument contains the index position
                            // of the selected item
                            dataHandler.open(String.format("%s/%s.db", getFilesDir(), available.get(which)));
                            setMap(dataHandler.getMap());
                            SetLastMap(available.get(which));
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            if(!ForceSelect)
                                Toast.makeText(MainActivity.this,"Cancelled", Toast.LENGTH_LONG).show();
                            else
                               CreateNewMap();
                        }
                    });
            Dialog dialog = builder.create();
            dialog.show();
            //last one opened?
        } else {
            //create new one
            if(!ForceSelect)
                Toast.makeText(MainActivity.this,"No Availible Maps", Toast.LENGTH_LONG).show();
            else
                CreateNewMap();
        }
    }

    private void DeleteMap(){
        available = dataHandler.availibleDBs(getFilesDir().getPath());
        if (available.size() > 0) {
            //pick one
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            CharSequence[] cs = available.toArray(new CharSequence[available.size()]);
            builder.setTitle("Delete a Map")
                    .setItems(cs, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // The 'which' argument contains the index position
                            // of the selected item
                            if (dataHandler.getMapName().equals(available.get(which))){
                                dataHandler.close();
                                map.setImage(ImageSource.resource(R.mipmap.black));
                                beaconKeeper.wipeBeacons();
                                File file = new File(String.format("%s/%s.db",getFilesDir(),available.get(which)));
                                if(file.exists())
                                    file.delete();
                                LoadMap(true);
                            }
                            else{
                                File file = new File(String.format("%s/%s.db",getFilesDir(),available.get(which)));
                                if(file.exists())
                                    file.delete();
                            }

                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            Toast.makeText(MainActivity.this,"Cancelled", Toast.LENGTH_LONG).show();
                        }
                    });
            Dialog dialog = builder.create();
            dialog.show();
        } else {
            Toast.makeText(MainActivity.this,"No Availible Maps", Toast.LENGTH_LONG).show();
        }
    }

}
