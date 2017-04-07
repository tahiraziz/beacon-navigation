package lilium.beaconnavigation.Implementations;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;

import lilium.beaconnavigation.Interfaces.Logger;
import lilium.beaconnavigation.Implementations.StandardBluetoothMonitor;

/**
 * Created by Saber on 4/6/2017.
 */

public class LoggingFunction implements Logger {

    FileOutputStream write;
    File gpxfile;
    FileWriter writer;

    public void openFileStream(Context context)
    {
        try {
            write = context.getApplicationContext().openFileOutput("Log", Context.MODE_PRIVATE);
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }

    public void logPosition(Integer rssi, float x, float y)
    {
        try
        {
            //This is where the actual values will be written into the text file
            write.write(8);
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }

    public void cleanUp()
    {
        try {
            write.flush();
            write.close();
            writer.flush();
            writer.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }

    public void checkFileDirectory(String sFileName,String sBody) {

        try {
            File root = new File(Environment.getDataDirectory(), "Log");
            if (!root.exists()) {
                root.mkdirs();
            }
            File gpxfile = new File(root, sFileName);
            writer = new FileWriter(gpxfile);

        }catch(IOException e) {
            e.printStackTrace();
        }
    }


}
