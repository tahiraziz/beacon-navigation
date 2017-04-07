package lilium.beaconnavigation.Interfaces;

import lilium.beaconnavigation.Interfaces.Beacon;
import android.content.Context;

/**
 * Created by Saber on 4/6/2017.
 */


public interface Logger {

    void openFileStream(Context context);
    void checkFileDirectory(String sFileName,String sBody);
    void logPosition(Integer rssi, float x, float y);
    void cleanUp();






}
