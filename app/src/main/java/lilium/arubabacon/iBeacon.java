package lilium.arubabacon;

import android.widget.TextView;

class iBeacon{
    //everything is public because of AMERICAN FREEDOM
    String mac;
    float x;
    float y;

    long lastUpdate;
    long advertInterval;

    int rssi;
    int lowRssi;
    int cummulativeRssi;
    int numRssi;
    int highRssi;
    TextView text;

    iBeacon(String mac, int rssi, float x, float y){
        this.mac = mac;
        lastUpdate = System.currentTimeMillis();
        this.rssi = rssi;
        lowRssi = rssi;
        highRssi = rssi;
        cummulativeRssi = rssi;
        numRssi = 1;
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
        return 10 / Math.pow(10.0, (-61 - (cummulativeRssi / numRssi)) / (10.0));
    }
}