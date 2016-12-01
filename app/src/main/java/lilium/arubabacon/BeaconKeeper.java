package lilium.arubabacon;

import android.bluetooth.BluetoothAdapter;
import android.os.AsyncTask;
import android.util.Log;


import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static lilium.arubabacon.MainActivity.adapter;
import static lilium.arubabacon.MainActivity.dataHandler;
import static lilium.arubabacon.MainActivity.map;

/**
 * Created by Cabub on 11/30/2016.
 */


public class BeaconKeeper {

    private long destroyBeaconTime;
    private ArrayList<iBeacon> placedBeacons;
    private Semaphore placedSem;
    private ArrayList<iBeacon> unplacedBeacons;
    private Thread beaconWatchdog;
    private Thread beaconUpdator;
    private AtomicInteger asyncCount;
    private AtomicBoolean stop;


    public void stop() {
        stop.set(true);
    }

    public void start() {
        stop.set(false);/*
        beaconWatchdog = new Thread(new Watchdog(), "BeaconWatchdog");
        beaconWatchdog.start();*/
    }

    public ArrayList<iBeacon> clonePlaced() {
        synchronized (placedBeacons) {
            return new ArrayList<iBeacon>(placedBeacons);
        }
    }

    public ArrayList<iBeacon> cloneUnplaced() {
        synchronized (placedBeacons) {
            return unplacedBeacons;
        }
    }

    public void placeBeacon(iBeacon beacon) {
        if (unplacedBeacons.contains(beacon)) {
                placedBeacons.add(beacon);//.remove relies on the iBeacon.equals, so if the iBeacon doesn't exist it shouldn't crash
                unplacedBeacons.remove(beacon);
        }
    }

    public BeaconKeeper(long DestroyBeaconTime) {
        //asyncCount = new AtomicInteger(0);

        stop = new AtomicBoolean();
        placedBeacons = new ArrayList<iBeacon>();
        unplacedBeacons = new ArrayList<iBeacon>();
        destroyBeaconTime = DestroyBeaconTime;
    }

    private class Watchdog implements Runnable {
        public void run() {
            //remove beacons which have not been updated in a while
            while (true) {
                if(!stop.get()) {
                    synchronized (placedBeacons) {
                        for (int i = 0; i < placedBeacons.size(); i++) {
                            if (System.currentTimeMillis() - placedBeacons.get(i).lastUpdate > destroyBeaconTime) {
                                placedBeacons.remove(i);
                                i--;
                            }
                        }
                    }
                    synchronized (unplacedBeacons) {
                        for (int i = 0; i < unplacedBeacons.size(); i++) {
                            if (System.currentTimeMillis() - unplacedBeacons.get(i).lastUpdate > destroyBeaconTime) {
                                //Log.e("cabub","removing from unplaced ".concat(unplacedBeacons.get(i).mac));
                                unplacedBeacons.remove(i);
                                i--;
                            }
                        }
                    }
                }
                try {
                    Thread.sleep(destroyBeaconTime + 100);
                } catch (InterruptedException e) {
                    Log.e("Thread: ".concat(Thread.currentThread().getName()), "Interrupted Exception");
                    e.printStackTrace();
                }
            }
        }
    }

    public void async_updateBeacon(String mac, int rssi) {
        new BeaconUpdate().execute(new BeaconUpdateArgs(mac, rssi));
    }


    public void updateBeacon(String mac, int rssi) {
        iBeacon beacon = dataHandler.selectBeacon(mac, rssi);
        int b = -1;
        boolean found = false;
        if (beacon != null) {
            synchronized (placedBeacons) {
                if (placedBeacons.contains(beacon)) { // it's on the map
                    b = placedBeacons.indexOf(beacon);
                    beacon = placedBeacons.get(b);
                    found = true;
                }
                else {
                    placedBeacons.add(beacon);
                }
            }
            if (found) {
                long now = System.currentTimeMillis();
                beacon.advertInterval = now - beacon.lastUpdate;
                beacon.lastUpdate = now;
                beacon.lowRssi = Math.max(rssi, beacon.lowRssi);//lower in this sense means closer to 0 from the negative side
                beacon.highRssi = Math.min(rssi, beacon.highRssi);//lower in this sense means further from 0 from the negative side
                beacon.cummulativeRssi = beacon.cummulativeRssi + rssi;
                beacon.numRssi = beacon.numRssi + 1;
                synchronized (placedBeacons) {
                    if (placedBeacons.contains(beacon)){
                        b = placedBeacons.indexOf(beacon);
                        placedBeacons.set(b, beacon);
                    }
                }
            }
        } else {
            beacon = new iBeacon(mac, rssi, -1, -1);
            synchronized (unplacedBeacons) {
                if (unplacedBeacons.contains(beacon)) {
                    b = unplacedBeacons.indexOf(beacon);
                    beacon = unplacedBeacons.get(b);
                    found = true;
                }
                else{
                    unplacedBeacons.add(beacon);
                }
            }
            if (found) {
                long now = System.currentTimeMillis();
                beacon.advertInterval = now - beacon.lastUpdate;
                beacon.lastUpdate = now;
                beacon.lowRssi = Math.max(rssi, beacon.lowRssi);//lower in this sense means closer to 0 from the negative side
                beacon.highRssi = Math.min(rssi, beacon.highRssi);//lower in this sense means further from 0 from the negative side
                beacon.cummulativeRssi = beacon.cummulativeRssi + rssi;
                beacon.numRssi = beacon.numRssi + 1;
                synchronized (unplacedBeacons) {
                    if(unplacedBeacons.contains(beacon)) {
                        b = unplacedBeacons.indexOf(beacon);
                        unplacedBeacons.set(b, beacon);
                    }
                }
            }
        }
    }

    public class BeaconUpdateArgs {
        int rssi;
        String mac;
        public BeaconUpdateArgs(String Mac, int Rssi) {
            mac = Mac;
            rssi = Rssi;
        }
    }

    public class BeaconUpdate extends AsyncTask<BeaconUpdateArgs, Void, Void> {

        protected Void doInBackground(BeaconUpdateArgs... args) {
            updateBeacon(args[0].mac, args[0].rssi);
            return null;
        }

        protected void onPostExecute(Void result) {
            adapter.notifyDataSetChanged();
        }
    }
}
