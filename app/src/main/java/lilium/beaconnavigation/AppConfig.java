package lilium.beaconnavigation;

import android.content.SharedPreferences;

import java.util.Set;

/**
 * Created by boylec on 1/29/17.
 */

public class AppConfig {
    private static SharedPreferences _sharedPrefs;

    public static void SetupConfig(SharedPreferences sharedPrefs)
    {
        _sharedPrefs = sharedPrefs;
    }

    public static double get_trilateration_epsilon() { return _sharedPrefs.getFloat("TRILAT_EPSILON",(float)1E-7);}

    public static int get_bt_mon_filter_min() { return _sharedPrefs.getInt("BT_MON_FILTER_MIN",-84);}

    public static int get_beacon_advert_queue_max_length() { return _sharedPrefs.getInt("ADVERT_QUEUE_MAX_LENGTH", 15);}

    public static int get_solver_max_iterations() { return _sharedPrefs.getInt("MAX_ITERATIONS",10000);}

    public static int get_maximum_spawn_wait(){
        return _sharedPrefs.getInt("MAX_SPAWN_WAIT",100);
    }

    public static int get_maximum_quiet(){
        return _sharedPrefs.getInt("MAXIMUM_QUIET",1300);
    }

    public static int get_minimium_position_delay(){
        return _sharedPrefs.getInt("MINIMUM_POSITION_DELAY",200);
    }



    public static String get_last_map_filename() {
        return _sharedPrefs.getString("LAST_MAP",null);
    }

    public static void set_trilateration_epsilon(double epsilon)
    {
        PutFloat("TRILAT_EPSILON",(float)epsilon);
    }

    public static void set_bt_mon_filter_min(int filterMin)
    {
        PutInt("BT_MON_FILTER_MIN",filterMin);
    }

    public static void set_beacon_advert_queue_max_length(int maxQueueLength)
    {
     PutInt("ADVERT_QUEUE_MAX_LENGTH",maxQueueLength);
    }
    public static void set_solver_max_iterations(int solverMaxIterations)
    {
        PutInt("MAX_ITERATIONS",solverMaxIterations);
    }

    public static void set_max_spawn_wait(int maxSpawnWait)
    {
        PutInt("MAX_SPAWN_WAIT",maxSpawnWait);
    }

    public static void set_maximum_quiet(int maxQuiet)
    {
        PutInt("MAXIMUM_QUIET",maxQuiet);
    }

    public static void set_minimum_position_delay(int minPosDelay)
    {
        PutInt("MINIMUM_POSITION_DELAY",minPosDelay);
    }

    public static void set_last_map_filename(String lastMapFileName)
    {
        PutString("LAST_MAP",lastMapFileName);
    }

    public static void PutBool(String key, boolean value)
    {
        SharedPreferences.Editor editor = _sharedPrefs.edit();
        editor.putBoolean(key,value);
        editor.commit();
    }

    public static void PutFloat(String key, float value)
    {
        SharedPreferences.Editor editor = _sharedPrefs.edit();
        editor.putFloat(key,value);
        editor.commit();
    }

    public static void PutInt(String key, int value)
    {
        SharedPreferences.Editor editor = _sharedPrefs.edit();
        editor.putInt(key,value);
        editor.commit();
    }

    public static void PutLong(String key, long value)
    {
        SharedPreferences.Editor editor = _sharedPrefs.edit();
        editor.putLong(key,value);
        editor.commit();
    }

    public static void PutString(String key, String value)
    {
        SharedPreferences.Editor editor = _sharedPrefs.edit();
        editor.putString(key,value);
        editor.commit();
    }
    public static void PutStringSet(String key, Set<String> value)
    {
        SharedPreferences.Editor editor = _sharedPrefs.edit();
        editor.putStringSet(key,value);
        editor.commit();
    }
}
