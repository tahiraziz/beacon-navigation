package lilium.arubabacon.Implementations;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import lilium.arubabacon.Interfaces.Beacon;
import lilium.arubabacon.Interfaces.DataHandler;

public class SqlLiteDataHandler implements DataHandler {
    SQLiteDatabase db;

    private boolean is_open;


    public SqlLiteDataHandler(){
        is_open=false;
    }

    public ArrayList<String> availableDbs(String path){
        File file = new File(path);
        ArrayList<String> filenames = new ArrayList<>();
        for (File f:file.listFiles()){
            if(f.getName().endsWith(".db")){
                filenames.add(f.getName().replace(".db",""));
            }
        }
        return filenames;
    }

    public void open(String filename){
        db = SQLiteDatabase.openOrCreateDatabase(filename, null);
        if (db.isOpen())
            is_open = true;
    }

    public boolean newMap(String filename, File path, File image){
        close();
        if (!filename.endsWith(".db"))
            filename = filename.concat(".db");
        open(String.format("%s/%s",path.getAbsolutePath(),(filename)));
        initDB();
        return insertMap(filename.replace(".db",""), image);
    }

    public boolean insertMap(String name, File file){
        if (!is_open) return false;
        SQLiteStatement insertStatement = db.compileStatement("INSERT INTO map (name, bytes) VALUES (?, ?);");
        if(file.exists()) {
            try {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                Bitmap bitmap = decodeFile(file);
                if(bitmap.getWidth() > bitmap.getHeight())
                    bitmap = RotateBitmap(bitmap, 90);
                bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);
                byte[] bytes = stream.toByteArray();
                insertStatement.bindString(1,name);
                insertStatement.bindBlob(2,bytes);
                insertStatement.execute();
            }catch (Exception e){
                Log.e("DataHandler","Exception at insertMap");
            }
        } else {
            return false;
        }
        return true;
    }

    public byte[] getMap(){
        if (!is_open) return null;
        Cursor c = db.query("map",new String[] {"name","bytes"},null,null,null, null, null);
        if (c.getCount() > 0){
            c.moveToFirst();
            byte[] map = c.getBlob(c.getColumnIndex("bytes"));
            c.close();
            return map;
        }
        c.close();
        return null;
    }

    public String getMapName(){
        if (!is_open) return null;
        Cursor c = db.query("map",new String[] {"name"},null,null,null, null, null);
        if (c.getCount() > 0){
            c.moveToFirst();
            String name = c.getString(c.getColumnIndex("name"));
            c.close();
            return name;
        }
        c.close();
        return null;
    }

    public void close(){

            if (is_open)
                db.close();
            is_open = false;
    }

    public ArrayList<Beacon> getBeacons(){
        if (!is_open) return null;
        Cursor c = db.query("beacons",new String[] {"mac","x","y"},null,null,null, null, null);
        if (c.getCount() > 0){
            ArrayList<Beacon> beaconList = new ArrayList<Beacon>();
            c.moveToFirst();
            for (int i = 0; i < c.getCount(); i++) {
                beaconList.add(new RssiAveragingBeacon(c.getString(c.getColumnIndex("mac")), -999, c.getFloat(c.getColumnIndex("x")), c.getFloat(c.getColumnIndex("y"))));
            }
            c.close();
            return beaconList;
        }
        c.close();
        return null;
    }

    public Beacon selectBeacon(String mac, int rssi){
        if (!is_open) return null;
        Cursor c = db.query("beacons",new String[] {"mac","x","y"},"mac = ?",new String [] {mac},null, null, null);
        if (c.getCount() > 0){
            c.moveToFirst();
            Beacon beacon = new RssiAveragingBeacon(mac, rssi, c.getFloat(c.getColumnIndex("x")), c.getFloat(c.getColumnIndex("y")));
            c.close();
            return beacon;
        }
        c.close();
        return null;
    }

    public void initDB(){
        if (!is_open) return;
        db.execSQL("CREATE TABLE IF NOT EXISTS beacons(mac TEXT PRIMARY KEY, x REAL, y REAL);");
        db.execSQL("CREATE TABLE IF NOT EXISTS map (name TEXT, bytes BLOB);");
    }

    public void wipeDB(){
        if (!is_open) return;
        db.execSQL("DROP TABLE IF EXISTS beacons;");
        initDB();
    }

    public void addBeacon(String mac, float x, float y){
        if (!is_open) return;
        SQLiteStatement insertStatement = db.compileStatement("INSERT INTO beacons (mac, x, y) VALUES (?, ?, ?);");
        insertStatement.bindString(1,mac);
        insertStatement.bindDouble(2,x);
        insertStatement.bindDouble(3,y);
        insertStatement.execute();
    }


    public void removeBeacon(Beacon beacon){
        if (!is_open) return;
        int i = db.delete("beacons","mac = ?",new String[] {beacon.getMac()});
        Log.d("dataHandler",String.valueOf(i));
    }

    public void removeBeacon(String mac){
        if (!is_open) return;
        db.delete("beacons","mac = ?",new String[] {mac});
    }

    @Override
    public boolean getIsOpen() {
        return is_open;
    }

    @Override
    public void setIsOpen(boolean isOpen) {
        is_open = isOpen;
    }


    private Bitmap decodeFile(File f){
        Bitmap b = null;

        //Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try {
            FileInputStream fis = new FileInputStream(f);
            BitmapFactory.decodeStream(fis, null, o);
            fis.close();

            int scale = 1;
            if (o.outHeight > 2000 || o.outWidth > 2000) {
                scale = (int) Math.pow(2, (int) Math.ceil(Math.log(2000 /
                        (double) Math.max(o.outHeight, o.outWidth)) / Math.log(0.5)));
            }

            //Decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            fis = new FileInputStream(f);
            b = BitmapFactory.decodeStream(fis, null, o2);
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return b;
    }

    public static Bitmap RotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
}
