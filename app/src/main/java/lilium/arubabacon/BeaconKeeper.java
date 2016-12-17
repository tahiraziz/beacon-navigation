package lilium.arubabacon;

import android.os.AsyncTask;
import android.util.Log;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.pow;
import static lilium.arubabacon.MainActivity.adapter;
import static lilium.arubabacon.MainActivity.dataHandler;

class BeaconKeeper {

    private long destroyBeaconTime;
    private ArrayList<iBeacon> placedBeacons;
    private ArrayList<iBeacon> unplacedBeacons;
    private Thread beaconWatchdog;
    private AtomicBoolean stop;


    void stop() {
        stop.set(true);
    }

    void start() {
        stop.set(false);
        //beaconWatchdog = new Thread(new Watchdog(), "BeaconWatchdog");
        //beaconWatchdog.start();
    }

    ArrayList<iBeacon> clonePlaced() {
        synchronized (placedBeacons) {
            return new ArrayList<>(placedBeacons);
        }
    }

    ArrayList<iBeacon> cloneUnplaced() {
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

    public void removeBeacon(iBeacon beacon){
        synchronized (placedBeacons){
            if (placedBeacons.contains(beacon)){
                placedBeacons.remove(beacon);
            }
        }
    }

    public void wipeBeacons(){
        synchronized (placedBeacons){
            placedBeacons.clear();
        }
    }

    BeaconKeeper(long DestroyBeaconTime) {
        stop = new AtomicBoolean();
        placedBeacons = new ArrayList<>();
        unplacedBeacons = new ArrayList<>();
        destroyBeaconTime = DestroyBeaconTime;
        beaconWatchdog = new Thread(new Watchdog(),"Watchdog");
        beaconWatchdog.start(); //TODO Turn back on
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

    void async_updateBeacon(String mac, int rssi) {
        new BeaconUpdate().execute(new BeaconUpdateArgs(mac, rssi));
    }


    private void updateBeacon(String mac, int rssi) {
        iBeacon beacon = null;
        try {beacon = dataHandler.selectBeacon(mac, rssi);
        }catch (Exception e){}
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
                beacon.rssi = rssi;
                beacon.lastUpdate = System.currentTimeMillis();
                beacon.addRssi(rssi);
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
                beacon.rssi = rssi;
                beacon.lastUpdate = System.currentTimeMillis();
                beacon.addRssi(rssi);
                synchronized (unplacedBeacons) {
                    if(unplacedBeacons.contains(beacon)) {
                        b = unplacedBeacons.indexOf(beacon);
                        unplacedBeacons.set(b, beacon);
                    }
                }
            }
        }
    }

    public iBeacon nearestBeacon(float x, float y){
        iBeacon beacon = null;
        double distanceTo = Double.MAX_VALUE;
        synchronized (placedBeacons){
            for(iBeacon b: placedBeacons){
                if(distanceTo > pow(b.x - x,2)+ pow(b.y - y,2)) {
                    distanceTo = pow(b.x - x,2) + pow(b.y - y,2);
                    beacon = b;
                }
            }
        }
        return beacon;
    }

    class BeaconUpdateArgs {
        int rssi;
        String mac;
        BeaconUpdateArgs(String Mac, int Rssi) {
            mac = Mac;
            rssi = Rssi;
        }
    }

    class BeaconUpdate extends AsyncTask<BeaconUpdateArgs, Void, Void> {

        protected Void doInBackground(BeaconUpdateArgs... args) {
            updateBeacon(args[0].mac, args[0].rssi);
            return null;
        }

        protected void onPostExecute(Void result) {
            adapter.notifyDataSetChanged();
        }
    }
}
