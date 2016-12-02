package lilium.arubabacon;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.UiThread;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static lilium.arubabacon.MainActivity.beaconKeeper;
import static lilium.arubabacon.MainActivity.btAdapter;
//import static lilium.arubabacon.MainActivity.flter_level;


/**
 * Created by Cabub on 11/30/2016.
 */

public class BluetoothMonitor {


    private ScanCallback scanCallback;
    private BluetoothAdapter.LeScanCallback depreciated_scanCallback;
    private ScanSettings settings;
    private ArrayList<ScanFilter> filters;
    private byte beacon_filter [];
    private final static int FILTER_MIN = -84;



    public BluetoothMonitor(){
        beacon_filter = new byte[]{0x02, 0x01, 0x06, 0x1a, (byte) 0xff, 0x4c, 0x00, 0x02, 0x15};
        if (Build.VERSION.SDK_INT >= 21) {
            scanCallback = new ScanCallback() {
                @Override
                @TargetApi(21)
                public void onScanResult(int callbackType, final ScanResult result) {
                    //filter out anything that is not an Aruba
                    if (is_iBeacon(result.getScanRecord().getBytes()) && result.getRssi() > FILTER_MIN) {
                        beaconKeeper.async_updateBeacon(result.getDevice().getAddress().replace(":", ""),result.getRssi());
                    }
                }
            };
            settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            filters = new ArrayList<>();
        }
        else{
            //device discovery for API level 18-20, this is very slow
            depreciated_scanCallback = new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
                    //filter out anything that is not an Aruba
                    if (is_iBeacon(scanRecord) && rssi > FILTER_MIN) {
                        //update beacons
                        beaconKeeper.async_updateBeacon(device.getAddress().replace(":", ""),rssi);
                    }
                }
            };
        }
    }


    private boolean is_iBeacon(byte[] scanRecord){
        byte[] prefix = new byte[9];
        System.arraycopy(scanRecord, 0, prefix, 0, 9);
        if (Arrays.equals(prefix, beacon_filter))  {
            return true;
        }
        return false;
    }

    public void start(){
        if (Build.VERSION.SDK_INT >= 21) {
            btAdapter.getBluetoothLeScanner().startScan(filters, settings, scanCallback);
        } else {
            //device discovery for API level 18-20, this is very slow
            btAdapter.startLeScan(depreciated_scanCallback);
        }
    }


}
