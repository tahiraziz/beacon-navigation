package lilium.beaconnavigation.Interfaces;

import java.io.File;
import java.util.ArrayList;

import lilium.beaconnavigation.Interfaces.Beacon;

/**
 * Created by boylec on 1/29/17.
 */

public interface DataHandler {
    ArrayList<String> availableDbs(String path);
    void open(String filename);
    boolean newMap(String filename, File path, File image);
    boolean insertMap(String name, File file);
    byte[] getMap();
    String getMapName();
    void close();
    ArrayList<Beacon> getBeacons();
    Beacon selectBeacon(String mac, int rssi);
    void initDB();
    void wipeDB();
    void addBeacon(String mac, float x, float y);
    void removeBeacon(Beacon beacon);
    void removeBeacon(String mac);
    boolean getIsOpen();
    void setIsOpen(boolean isOpen);
}
