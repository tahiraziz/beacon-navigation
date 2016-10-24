package lilium.arubabacon;

class iBeacon{
    //everything is public because of AMERICAN FREEDOM
    String mac;
    double x;
    double y;

    long lastUpdate;
    long advertInterval;

    int rssi;
    int lowRssi;
    int cummulativeRssi;
    int numRssi;
    int highRssi;

    iBeacon(String mac, int rssi, double x, double y){
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
}