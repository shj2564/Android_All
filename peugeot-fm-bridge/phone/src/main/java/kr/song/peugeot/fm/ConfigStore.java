package kr.song.peugeot.fm;

import android.content.Context;

final class ConfigStore {
    private static final String PREF="peugeot_fm_local";
    static String server(Context c){
        return c.getSharedPreferences(PREF,0).getString("server","");
    }
    static String token(Context c){
        return c.getSharedPreferences(PREF,0).getString("token","");
    }
    static boolean paired(Context c){return !token(c).isEmpty()&&!server(c).isEmpty();}
    static void save(Context c,String server,String token){
        c.getSharedPreferences(PREF,0).edit().putString("server",server)
            .putString("token",token).apply();
    }
    private ConfigStore(){}
}
