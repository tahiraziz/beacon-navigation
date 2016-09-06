package lilium.arubabacon;

import android.widget.ArrayAdapter;

import java.util.Arrays;

public class iBeacon{
    //http://www.warski.org/blog/2014/01/how-ibeacons-work/
    //https://developer.mbed.org/blog/entry/BLE-Beacons-URIBeacon-AltBeacons-iBeacon/
    public byte[] prefix = new byte[] {0,0,0,0,0,0,0,0,0};
    public byte[] uuid = new byte[] {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    public int major;
    public int minor;
    public byte tx;
    private double calibration = 2.42;
    public double distance;

    public iBeacon(byte[] scanRecord, double rssi){
        System.arraycopy(scanRecord, 0, prefix, 0, 9);
        System.arraycopy(scanRecord, 9, uuid, 0, 16);
        major = scanRecord[25] << 0xFF + scanRecord[26];
        minor = scanRecord[27] << 0xFF + scanRecord[28];
        tx = scanRecord[29];
        if (rssi != 0) {
            distance = Math.pow(10, (tx - rssi) / (10.0 * calibration));
        }else{
            distance = -1;
        }
    }

    @Override
    public String toString(){
        return byteArrayToHexString(prefix) + "\n" + byteArrayToHexString(uuid) + "." + major + "." + minor + "\n" + Double.toString(distance);
    }

    @Override
    public boolean equals(Object obj){
        if (obj == null) {
            return false;
        }
        if (!iBeacon.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final iBeacon other = (iBeacon) obj;
        if(Arrays.equals(other.uuid, uuid) && (other.major == major) && (other.minor == minor)){
            return true;
        }else{
            return false;
        }
    }

    final protected static char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
    public static String byteArrayToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length*2];
        int v;

        for(int j=0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j*2] = hexArray[v>>>4];
            hexChars[j*2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }
}