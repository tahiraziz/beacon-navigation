package lilium.arubabacon.Implementations;

import android.os.AsyncTask;
import android.util.Log;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import lilium.arubabacon.Interfaces.Beacon;
import lilium.arubabacon.Interfaces.BeaconKeeper;

import static java.lang.Math.pow;
import static lilium.arubabacon.MainActivity.beaconArrayAdapter;
import static lilium.arubabacon.MainActivity.dataHandler;

//Keeps track of beacon RSSIs that have been "placed" on a thread that runs the "Watchdog" private class
public class MultiThreadedBeaconKeeper implements BeaconKeeper {

    private long destroyBeaconTime;
    private ArrayList<Beacon> placedBeacons;
    private ArrayList<Beacon> unplacedBeacons;
    private Thread beaconWatchdog;
    private AtomicBoolean stop;


    public void stop() {
        stop.set(true);
    }

    public void start() {
        stop.set(false);
        //beaconWatchdog = new Thread(new Watchdog(), "BeaconWatchdog");
        //beaconWatchdog.start();
    }

    public ArrayList<Beacon> clonePlaced() {
        synchronized (placedBeacons) {
            return new ArrayList<>(placedBeacons);
        }
    }

    public ArrayList<Beacon> cloneUnplaced() {
        synchronized (placedBeacons) {
            return unplacedBeacons;
        }
    }

    public void placeBeacon(Beacon beacon) {
        if (unplacedBeacons.contains(beacon)) {
                placedBeacons.add(beacon);//.remove relies on the iBeacon.equals, so if the iBeacon doesn't exist it shouldn't crash
                unplacedBeacons.remove(beacon);
        }
    }

    public void removeBeacon(Beacon beacon){
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

    //Constructor for beacon keeper... starts a new thread that runs the Watchdog class
    public MultiThreadedBeaconKeeper(long DestroyBeaconTime) {
        stop = new AtomicBoolean();
        placedBeacons = new ArrayList<>();
        unplacedBeacons = new ArrayList<>();
        destroyBeaconTime = DestroyBeaconTime;
        beaconWatchdog = new Thread(new Watchdog(),"Watchdog");
        beaconWatchdog.start(); //TODO Turn back on
    }

    private class Watchdog implements Runnable {
        public void run() {
            //This thread runs an infinite loop until the thread is killed
            while (true) {
                //If the watchdog has not been stopped
                if(!stop.get()) {
                    //Lock on placedBeacons so they can't be altered during this branch
                    synchronized (placedBeacons) {
                        long currentTimeMs = System.currentTimeMillis();
                        for (int i = 0; i < placedBeacons.size(); i++) {
                            if (currentTimeMs - placedBeacons.get(i).getLastUpdate() > destroyBeaconTime) {
                                placedBeacons.remove(i);
                                i--;
                            }
                        }
                    }
                    synchronized (unplacedBeacons) {
                        for (int i = 0; i < unplacedBeacons.size(); i++) {
                            long currentTimeMs = System.currentTimeMillis();
                            if (currentTimeMs - unplacedBeacons.get(i).getLastUpdate() > destroyBeaconTime) {
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


    private void updateBeacon(String mac, int rssi) {
        Beacon beacon = null;
        try
        {
            beacon = dataHandler.selectBeacon(mac, rssi);
        }
        catch (Exception e)
        {

        }
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
                beacon.setRssi(rssi);
                beacon.setLastUpdate(System.currentTimeMillis());
                beacon.addRssi(rssi);
                synchronized (placedBeacons) {
                    if (placedBeacons.contains(beacon)){
                        b = placedBeacons.indexOf(beacon);
                        placedBeacons.set(b, beacon);
                    }
                }
            }
        } else {
            beacon = new RssiAveragingBeacon(mac, rssi, -1, -1);
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
                beacon.setRssi(rssi);
                beacon.setLastUpdate(System.currentTimeMillis());
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

    public Beacon nearestBeacon(float x, float y){
        Beacon beacon = null;
        double distanceTo = Double.MAX_VALUE;
        synchronized (placedBeacons){
            for(Beacon b: placedBeacons){
                if(distanceTo > pow(b.getX() - x,2)+ pow(b.getY() - y,2)) {
                    distanceTo = pow(b.getX() - x,2) + pow(b.getY() - y,2);
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
            beaconArrayAdapter.notifyDataSetChanged();
        }
    }
}
