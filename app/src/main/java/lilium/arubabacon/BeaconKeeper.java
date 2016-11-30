package lilium.arubabacon;

import android.os.AsyncTask;


import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static lilium.arubabacon.MainActivity.dataHandler;
import static lilium.arubabacon.MainActivity.map;

/**
 * Created by Cabub on 11/30/2016.
 */


public class BeaconKeeper {

    private long destroyBeaconTime;
    private ArrayList<iBeacon> placedBeacons;
    private ArrayList<iBeacon> unplacedBeacons;
    private Thread beaconWatchdog;
    private AtomicInteger asyncCount;
    private AtomicBoolean stop;


    public void stop(){
        stop.set(true);
    }

    public void start(){
        stop.set(false);
        beaconWatchdog.start();
    }

    public ArrayList<iBeacon> clonePlaced(){
        synchronized (placedBeacons){
            return new ArrayList<iBeacon>(placedBeacons);
        }
    }

    public ArrayList<iBeacon> cloneUnplaced(){
        synchronized (placedBeacons){
            return new ArrayList<iBeacon>(unplacedBeacons) ;
        }
    }

    public void placeBeacon(iBeacon beacon){
        if (unplacedBeacons.contains(beacon)) {
            synchronized (placedBeacons ) {
                placedBeacons.add(beacon);
            }
             synchronized (unplacedBeacons) { //.remove relies on the iBeacon.equals, so if the iBeacon doesn't exist it shouldn't crash
                unplacedBeacons.remove(beacon);
            }
        }
    }

    public BeaconKeeper(long DestroyBeaconTime){
        asyncCount = new AtomicInteger(0);
        placedBeacons = new ArrayList<iBeacon>();
        unplacedBeacons = new ArrayList<iBeacon>();
        destroyBeaconTime = DestroyBeaconTime;
        stop = new AtomicBoolean();

        beaconWatchdog = new Thread(new Runnable(){
            public void run() {
                //remove beacons which have not been updated in a while
                while (!stop.get()) {
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
                                unplacedBeacons.remove(i);
                                i--;
                            }
                        }
                    }
                }
            }
        }, "BeaconWatchdog");

    }

    public void updateBeacons(String mac, int rssi){
        if(asyncCount.compareAndSet(0,1) ){
            AsyncTask asyncTask = new AsyncBeaconUpdater().execute(new BeaconUpdateArgs(mac,rssi));
        }
    }

    private void _updateBeacons(String mac, int rssi) {
        iBeacon beacon = dataHandler.selectBeacon(mac,rssi);

        if (beacon != null) {
            synchronized (placedBeacons) {
                if (placedBeacons.contains(beacon)) { // it's on the map
                    int b = placedBeacons.indexOf(beacon);
                    beacon = placedBeacons.get(b);

                    long now = System.currentTimeMillis();
                    beacon.advertInterval = now - beacon.lastUpdate;
                    beacon.lastUpdate = now;

                    //lower in this sense means closer to 0 from the negative side
                    beacon.lowRssi = Math.max(rssi, beacon.lowRssi);

                    //lower in this sense means further from 0 from the negative side
                    beacon.highRssi = Math.min(rssi, beacon.highRssi);

                    beacon.cummulativeRssi = beacon.cummulativeRssi + rssi;
                    beacon.numRssi = beacon.numRssi + 1;

                    placedBeacons.set(b, beacon);
                } else {
                    placedBeacons.add(beacon);
                }
            }
        } else{
            beacon = new iBeacon(mac, rssi, -1, -1);
            synchronized (unplacedBeacons) {
                if (unplacedBeacons.contains(beacon)) { //
                    int b = unplacedBeacons.indexOf(beacon);
                    beacon = unplacedBeacons.get(b);

                    long now = System.currentTimeMillis();
                    beacon.advertInterval = now - beacon.lastUpdate;
                    beacon.lastUpdate = now;

                    //lower in this sense means closer to 0 from the negative side
                    beacon.lowRssi = Math.max(rssi, beacon.lowRssi);

                    //lower in this sense means further from 0 from the negative side
                    beacon.highRssi = Math.min(rssi, beacon.highRssi);

                    beacon.cummulativeRssi = beacon.cummulativeRssi + rssi;
                    beacon.numRssi = beacon.numRssi + 1;

                    unplacedBeacons.set(b, beacon);
                } else {
                    unplacedBeacons.add(beacon);
                }
            }
        }
    }

    private class BeaconUpdateArgs{
        String mac;
        int rssi;
        BeaconUpdateArgs(String s, int r){
            mac = s;
            rssi = r;
        }
    }

    private class AsyncBeaconUpdater extends AsyncTask<BeaconUpdateArgs,Void,Void>{

        protected Void doInBackground(BeaconUpdateArgs ... args) {
            _updateBeacons(args[0].mac,args[1].rssi);
            return null;
        }


        protected void onPostExecute(){
            map.invalidate();
            asyncCount.decrementAndGet();
        }
    }

}
