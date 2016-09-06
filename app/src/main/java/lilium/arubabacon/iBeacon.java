package lilium.arubabacon;

import android.bluetooth.BluetoothDevice;
import android.widget.ArrayAdapter;

import java.util.Arrays;

public class iBeacon{
    String mac;
    String name;

    public byte[] prefix = new byte[] {0,0,0,0,0,0,0,0,0};
    public boolean isiBeacon;
    public byte[] uuid = new byte[] {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    public short major;
    public short minor;
    public int tx;
    public double rssi;
    public double distance;
    public byte[] scanResponse;

    public iBeacon(final BluetoothDevice device, double rssi, byte[] scanRecord){
        mac = device.getAddress();
        name = device.getName();

        System.arraycopy(scanRecord, 0, prefix, 0, 9);
        isiBeacon = Arrays.equals(prefix, new byte[] {0x02,0x01,0x06,0x1a,(byte)0xff,0x4c,0x00,0x02,0x15});
        System.arraycopy(scanRecord, 9, uuid, 0, 16);
        major = (short)( ((scanRecord[25] & 0xFF) << 8) | (scanRecord[26] & 0xFF) );
        minor = (short)( ((scanRecord[27] & 0xFF) << 8) | (scanRecord[28] & 0xFF) );
        tx = scanRecord[29];
        this.rssi = rssi;
        if (rssi != 0) {
            //https://stackoverflow.com/questions/20416218/understanding-ibeacon-distancing/20434019
            distance = Math.sqrt(Math.pow(10, (tx - rssi) / (10.0)));
        }else{
            distance = -1;
        }
        if(scanRecord.length > 31) {
            scanResponse = new byte[scanRecord.length - 30];
            System.arraycopy(scanRecord, 30, scanResponse, 0, scanRecord.length - 30);
        }
    }

    @Override
    public String toString(){
        return  "MAC: " + mac + "\n" +
                "TX: " + tx + " RSSI: " + rssi + "\n" +
                "Distance: " + Double.toString(distance) + "\n" +
                "scanResponse:\n" + byteArrayToHexString(scanResponse);
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