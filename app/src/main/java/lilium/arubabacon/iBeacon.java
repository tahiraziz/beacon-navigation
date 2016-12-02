package lilium.arubabacon;

import java.util.LinkedList;
import java.util.Queue;

class iBeacon{
    String mac;
    float x;
    float y;

    long lastUpdate;

    int rssi;
    private Queue<Integer> rssiQueue;
    private final static int QUEUE_MAX_LENGTH = 15;

    iBeacon(String mac, int rssi, float x, float y){
        rssiQueue = new LinkedList<>();
        this.mac = mac;
        lastUpdate = System.currentTimeMillis();
        rssiQueue.add(rssi);
        this.rssi = rssi;
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !iBeacon.class.isAssignableFrom(obj.getClass())) return false;
        return ((iBeacon) obj).mac.equals(mac);
    }

    @Override
    public String toString(){
        return mac;
    }

    public double distance(){
        //we want linear distances, the distance readings don't have to be accurate
        //they just need to be consistent across all beacons
        //because the trilateration function uses them as relative to each other

        //-61 is the callibrated RSSI reported by the beacons
        //ratio_db = calibratedRSSI - RSSI
        //ratio_db = -61 - (beacons.get(i).cummulativeRssi / beacons.get(i).numRssi)
        //Convert to linear Math.pow(10.0, (ratio_db) / 10.0))
        //2 / x inverse because the results were backwards.
        return 10 / Math.pow(10.0, (-61 - Math.min(-55, averageRssi())) / 10.0);
    }

    public synchronized void addRssi(Integer Rssi){
        if (rssiQueue.size() >= QUEUE_MAX_LENGTH ){
            rssiQueue.remove();
        }
        rssiQueue.add(Rssi);
    }

    public synchronized double averageRssi(){
        Integer sum = 0;
        for(Integer i: rssiQueue){
            sum += i;
        }
        return sum / rssiQueue.size();
    }
}