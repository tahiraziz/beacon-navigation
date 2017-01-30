package lilium.arubabacon.Implementations;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import java.util.ArrayList;
import java.util.Arrays;

import lilium.arubabacon.AppConfig;
import lilium.arubabacon.Interfaces.BluetoothMonitor;

import static lilium.arubabacon.MainActivity.beaconKeeper;
import static lilium.arubabacon.MainActivity.btAdapter;

public class StandardBluetoothMonitor implements BluetoothMonitor{

    private ScanCallback scanCallback;
    private BluetoothAdapter.LeScanCallback deprecated_scanCallback;
    private ScanSettings settings;
    private ArrayList<ScanFilter> filters;
    private byte beacon_filter [];

    //The minimum RSSI that we care about (we won't treat any other scans as viable beacons unless their RSSIs are greater than this number
    private final static int FILTER_MIN = AppConfig.get_bt_mon_filter_min();

    public StandardBluetoothMonitor()
    {
        beacon_filter = new byte[]{0x02, 0x01, 0x06, 0x1a, (byte) 0xff, 0x4c, 0x00, 0x02, 0x15};

        //If our Android version is greater than 21 then device discovery is better
        if (Build.VERSION.SDK_INT >= 21) {
            scanCallback = new ScanCallback() {
                @Override
                @TargetApi(21)
                public void onScanResult(int callbackType, final ScanResult result) {
                    //filter outlier beacons
                    int thisScansRssi = result.getRssi();
                    if (is_Beacon(result.getScanRecord().getBytes()) && thisScansRssi > FILTER_MIN) {
                        //Do an idempotent update of beacons in beacon keeper (whether "placed" or "unplaced" within the beacon keeper)
                        beaconKeeper.async_updateBeacon(result.getDevice().getAddress().replace(":", ""),thisScansRssi);
                    }
                }
            };
            settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            filters = new ArrayList<>();
        }
        else{
            //device discovery for API level 18-20, this is very slow
            deprecated_scanCallback = new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
                    //filter outlier beacons
                    if (is_Beacon(scanRecord) && rssi > FILTER_MIN) {
                        //update beacons
                        beaconKeeper.async_updateBeacon(device.getAddress().replace(":", ""),rssi);
                    }
                }
            };
        }
    }


    //Compares the scanned unique identifier to the beacon_filter we have defined to check if it the beacon type we are looking for
    private boolean is_Beacon(byte[] scanRecord){
        byte[] prefix = new byte[9];
        System.arraycopy(scanRecord, 0, prefix, 0, 9);
        if (Arrays.equals(prefix, beacon_filter))  {
            return true;
        }
        return false;
    }

    //This is called to start the monitoring
    public void start(){
        if (Build.VERSION.SDK_INT >= 21) {
            btAdapter.getBluetoothLeScanner().startScan(filters, settings, scanCallback);
        } else {
            //device discovery for API level 18-20, this is very slow
            btAdapter.startLeScan(deprecated_scanCallback);
        }
    }
}
