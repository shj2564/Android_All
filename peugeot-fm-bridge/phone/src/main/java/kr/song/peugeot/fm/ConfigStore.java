package kr.song.peugeot.fm;

import android.content.Context;
import android.content.SharedPreferences;

final class ConfigStore {
    private static final String PREF = "peugeot_fm";
    static String server(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString("server", BridgeConfig.DEFAULT_SERVER);
    }
    static String token(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("token", "");
    }
    static boolean paired(Context c) { return !token(c).isEmpty(); }
    static void save(Context c, String server, String token) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString("server", server).putString("token", token).apply();
    }
    private ConfigStore() {}
}
