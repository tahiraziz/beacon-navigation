package lilium.arubabacon;

import android.bluetooth.BluetoothDevice;
import java.text.DecimalFormat;
import java.util.Arrays;

public class iBeacon{
    //everything is public because of AMERICAN FREEDOM
    public String mac;
    public String name;
    public long lastUpdate;
    public long advertInterval;
    public double pathLoss;

    public byte[] prefix = new byte[] {0,0,0,0,0,0,0,0,0};
    public boolean isiBeacon;
    public byte[] uuid = new byte[] {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    public short major;
    public short minor;
    public int tx;
    public byte[] scanResponse;

    public int rssi;
    public int lowRssi;
    public int cummulativeRssi;
    public int numRssi;
    public int highRssi;

    public double distance;
    public double lowDistance;
    public double avgDistance;
    public double highDistance;

    public double distance2;
    public double lowDistance2;
    public double avgDistance2;
    public double highDistance2;

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
        //these beacons are transmitting 62 bytes, an advert packet and scan response packet
        if(scanRecord.length > 31) {
            scanResponse = new byte[scanRecord.length - 30];
            System.arraycopy(scanRecord, 30, scanResponse, 0, scanRecord.length - 30);
        }

        //begin tracking RSSI
        this.rssi = rssi;
        lowRssi = rssi;
        cummulativeRssi = rssi;
        numRssi = 1;
        highRssi = rssi;
    }

    public void postInit(){
        //calculate distances for this instance
        distance = calculateDistance(tx, rssi);
        distance2 = calculateDistance2(tx, rssi);

        //these calculations need historical values

        lowDistance = calculateDistance(tx, lowRssi);
        avgDistance = calculateDistance(tx, cummulativeRssi * 1.0 / numRssi);
        highDistance = calculateDistance(tx, highRssi);

        lowDistance2 = calculateDistance2(tx, lowRssi);
        avgDistance2 = calculateDistance2(tx, cummulativeRssi * 1.0 / numRssi);
        highDistance2 = calculateDistance2(tx, highRssi);
    }

    public double calculateDistance(int tx, double rssi){
        //https://stackoverflow.com/questions/20416218/understanding-ibeacon-distancing
        //https://electronics.stackexchange.com/questions/83354/calculate-distance-from-rssi
        //path loss in free space is 2.0, this is the same as applying the inverse square law
        if (rssi != 0) return Math.pow(10.0, (tx - rssi) / (10.0 * pathLoss));
        return -1;
    }

    public double calculateDistance2(int tx, double rssi) {
        //https://altbeacon.github.io/android-beacon-library/distance-calculations.html
        //http://developer.radiusnetworks.com/2014/12/04/fundamentals-of-beacon-ranging.html

        //change these coefficients for different models, manufacturers, and chipsets
        //need some power regression here
        double A = 0.42;
        double B = 6.5;
        double C = 0.55;

        if (rssi == 0) return -1.0;
        if (rssi * 1.0 / tx < 1.0) return Math.pow(rssi * 1.0 / tx, 10);
        return A * Math.pow(rssi * 1.0 / tx, B) + C;
    }

    @Override
    public String toString(){
        return  "MAC: " + mac + "         TX: " + tx + "\n" +
                "advertInterval: " + advertInterval + "ms   Samples: " + numRssi + "\n\n" +
                "Now      Low      Avg        High\n" +

                rssi + "        " +
                lowRssi + "        " +
                new DecimalFormat("00.00").format(cummulativeRssi * 1.0 / numRssi) + "     " +
                highRssi + "      pLoss: " + pathLoss + "\n" +

                new DecimalFormat("0.000").format(distance) + "    " +
                new DecimalFormat("0.000").format(lowDistance) + "    " +
                new DecimalFormat("0.000").format(avgDistance) + "    " +
                new DecimalFormat("0.000").format(highDistance) + "\n" +

                new DecimalFormat("0.000").format(distance2) + "    " +
                new DecimalFormat("0.000").format(lowDistance2) + "    " +
                new DecimalFormat("0.000").format(avgDistance2) + "    " +
                new DecimalFormat("0.000").format(highDistance2) + "\n";
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
}