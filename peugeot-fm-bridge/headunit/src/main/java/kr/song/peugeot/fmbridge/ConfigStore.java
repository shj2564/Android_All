package kr.song.peugeot.fmbridge;

import android.content.Context;

final class ConfigStore {
    private static final String PREF="peugeot_fm_bridge";
    static String server(Context c){ return c.getSharedPreferences(PREF,0).getString("server",BridgeConfig.DEFAULT_SERVER); }
    static String token(Context c){ return c.getSharedPreferences(PREF,0).getString("token",""); }
    static boolean paired(Context c){ return !token(c).isEmpty(); }
    static int lastSeq(Context c){ return c.getSharedPreferences(PREF,0).getInt("seq",0); }
    static void seq(Context c,int v){ c.getSharedPreferences(PREF,0).edit().putInt("seq",v).apply(); }
    static void save(Context c,String server,String token){
        c.getSharedPreferences(PREF,0).edit().putString("server",server).putString("token",token).apply();
    }
    private ConfigStore(){}
}
