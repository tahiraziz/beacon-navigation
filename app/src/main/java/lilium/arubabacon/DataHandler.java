package lilium.arubabacon;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import java.util.ArrayList;

class DataHandler {
    SQLiteDatabase db;
    boolean is_open;

    DataHandler(){
        is_open=false;

    }

    public void open(String filename){
        db = SQLiteDatabase.openOrCreateDatabase(filename, null);
        if (db.isOpen())
            is_open = true;

    }

    public void close(){
        db.close();
    }

    public ArrayList<iBeacon> getBeacons(){
        Cursor c = db.query("beacons",new String[] {"mac","x","y"},null,null,null, null, null);
        if (c.getCount() > 0){
            ArrayList<iBeacon> beaconList = new ArrayList<iBeacon>();
            c.moveToFirst();
            for (int i = 0; i < c.getCount(); i++) {
                beaconList.add(new iBeacon(c.getString(c.getColumnIndex("mac")), -999, c.getFloat(c.getColumnIndex("x")), c.getFloat(c.getColumnIndex("y"))));
            }
            c.close();
            return beaconList;
        }
        c.close();
        return null;
    }

    public iBeacon selectBeacon(String mac, int rssi){
        Cursor c = db.query("beacons",new String[] {"mac","x","y"},"mac = ?",new String [] {mac},null, null, null);
        if (c.getCount() > 0){
            c.moveToFirst();
            iBeacon beacon = new iBeacon(mac, rssi, c.getFloat(c.getColumnIndex("x")), c.getFloat(c.getColumnIndex("y")));
            c.close();
            return beacon;
        }
        c.close();
        return null;
    }

    public void initDB(){
        db.execSQL("CREATE TABLE IF NOT EXISTS beacons(mac TEXT PRIMARY KEY, x REAL, y REAL);");
        db.execSQL("CREATE TABLE IF NOT EXISTS map (name TEXT, bytes BLOB);");
    }

    public void wipeDB(){
        db.execSQL("DROP TABLE IF EXISTS beacons;");
        initDB();
    }

    public void addBeacon(String mac, float x, float y){
        SQLiteStatement insertStatement = db.compileStatement("INSERT INTO beacons (mac, x, y) VALUES (?, ?, ?);");
        insertStatement.bindString(1,mac);
        insertStatement.bindDouble(2,x);
        insertStatement.bindDouble(3,y);
        insertStatement.execute();
    }

    public void removeBeacon(iBeacon beacon){
        SQLiteStatement deleteStatement = db.compileStatement("DELETE b FROM beacons b WHERE b.rssi = ?;");
        deleteStatement.bindString(1, beacon.mac);
        deleteStatement.execute();
    }


}
