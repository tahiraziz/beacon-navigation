package lilium.arubabacon;

import android.bluetooth.BluetoothDevice;

import java.text.DecimalFormat;
import java.util.Arrays;

public class iBeacon{
    public String mac;
    public String name;
    public long lastUpdate;
    public long advertInterval;

    public byte[] prefix = new byte[] {0,0,0,0,0,0,0,0,0};
    public boolean isiBeacon;
    public byte[] uuid = new byte[] {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    public short major;
    public short minor;
    public int tx;
    public int rssi;
    public int cummulativeRssi;
    public int numRssi;
    public double distance;
    public double distance2;
    public double avgDistance;
    public double avgDistance2;
    public byte[] scanResponse;

    public iBeacon(final BluetoothDevice device, int rssi, byte[] scanRecord){
        lastUpdate = System.currentTimeMillis();
        mac = device.getAddress();
        name = device.getName();

        System.arraycopy(scanRecord, 0, prefix, 0, 9);
        isiBeacon = Arrays.equals(prefix, new byte[] {0x02,0x01,0x06,0x1a,(byte)0xff,0x4c,0x00,0x02,0x15});
        System.arraycopy(scanRecord, 9, uuid, 0, 16);
        major = (short)( ((scanRecord[25] & 0xFF) << 8) | (scanRecord[26] & 0xFF) );
        minor = (short)( ((scanRecord[27] & 0xFF) << 8) | (scanRecord[28] & 0xFF) );
        tx = scanRecord[29];
        this.rssi = rssi;
        cummulativeRssi = rssi;
        numRssi = 1;
        distance = calculateDistance(tx, rssi);
        distance2 = calculateDistance2(tx, rssi);
        if(scanRecord.length > 31) {
            scanResponse = new byte[scanRecord.length - 30];
            System.arraycopy(scanRecord, 30, scanResponse, 0, scanRecord.length - 30);
        }
    }

    public double calculateDistance(int tx, double rssi){
        //https://stackoverflow.com/questions/20416218/understanding-ibeacon-distancing/20434019
        if (rssi != 0) return Math.sqrt(Math.pow(10, (tx - rssi) / 10.0));
        return -1;
    }

    public double calculateDistance2(int tx, double rssi) {
        //change these coefficients for different models, manufacturers, and chipsets
        //need some linear regression here
        double A = 0.5;
        double B = 4.2;
        double C = 0.0;

        https://altbeacon.github.io/android-beacon-library/distance-calculations.html
        http://developer.radiusnetworks.com/2014/12/04/fundamentals-of-beacon-ranging.html
        if (rssi == 0) return -1.0;

        if (rssi * 1.0 / tx < 1.0) return Math.pow(rssi * 1.0 / tx, 10);
        return A * Math.pow(rssi * 1.0 / tx, B) + C;
    }

    @Override
    public String toString(){
        avgDistance = calculateDistance(tx, cummulativeRssi * 1.0 / numRssi);
        avgDistance2 = calculateDistance2(tx, cummulativeRssi * 1.0 / numRssi);
        return  "MAC: " + mac + "\n" +
                "TX/RSSI/avgRSSI: " + tx + "/" + rssi + "/" + new DecimalFormat("#.##").format(cummulativeRssi * 1.0 / numRssi) + "\n" +
                "advertInterval: " + advertInterval + "ms   Samples: " + numRssi + "\n" +
                "Distance1: " + Double.toString(distance) + "\n" +
                "averaged1: " + Double.toString(avgDistance) + "\n" +
                "Distance2: " + Double.toString(distance2) + "\n" +
                "averaged2: " + Double.toString(avgDistance2);
                // + "\n" + "scanResponse:\n" + byteArrayToHexString(scanResponse);
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
        //if(Arrays.equals(other.uuid, uuid) && (other.major == major) && (other.minor == minor) && Arrays.equals(other.unknown, unknown)){
        return other.mac.equals(mac);
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