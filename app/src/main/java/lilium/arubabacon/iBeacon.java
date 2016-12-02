package lilium.arubabacon;

import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

class iBeacon{
    //everything is public because of AMERICAN FREEDOM
    String mac;
    float x;
    float y;

    long lastUpdate;
    //long advertInterval;

    int rssi;
    private Queue<Integer> rssiQueue;
    private final static int QUEUE_MAX_LENGTH = 15;

    TextView text;

    iBeacon(String mac, int rssi, float x, float y){
        rssiQueue = new LinkedList<>();
        this.mac = mac;
        lastUpdate = System.currentTimeMillis();
        rssiQueue.add(rssi);
        this.rssi = rssi;
        this.x = x;
        this.y = y;
    }
/*
    public synchronized void set_queue_length(int arg){
        QUEUE_MAX_LENGTH = arg;
        while(rssiQueue.size() >= QUEUE_MAX_LENGTH){
            rssiQueue.remove();
        }
    }*/

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
        double linearDist = 10 / Math.pow(10.0, (-61 - Math.min(averageRssi(), -55)) / (10.0));;
        return linearDist;
    }

    public synchronized void addRssi(Integer Rssi){
        if (rssiQueue.size() >= QUEUE_MAX_LENGTH ){
            rssiQueue.remove();
        }
        rssiQueue.add(Rssi);
    }

    public synchronized double  averageRssi(){
        Integer sum = 0;
        Double avg;
        for(Integer i: rssiQueue){
            sum += i;
        }
        return sum / rssiQueue.size();
    }
}