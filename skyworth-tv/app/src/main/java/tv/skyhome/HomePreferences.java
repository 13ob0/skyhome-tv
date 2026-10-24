package tv.skyhome;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.*;

final class HomePreferences {
    static final int MAX_FAVORITES = 9;
    static final String[] FILES = {"hqzyUL3M21k.jpg", "rSYsP5TlEAk.jpg", "4axNezh03UA.jpg", "vwFvhJf6u_I.jpg", "JtF_FvXVQQI.jpg"};
    static final String[] NAMES = {"雨林山路", "雾中雪岭", "热带绿叶", "山间彩虹", "流光花影", "静谧深色"};
    static final int[] INTERVALS = {0, 1, 5, 15, 30};
    final SharedPreferences prefs;
    HomePreferences(SharedPreferences prefs) { this.prefs = prefs; }
    int wallpaper() { return Math.max(0, Math.min(5, prefs.getInt("wallpaper", 0))); }
    int interval() {
        int value = prefs.getInt("interval", 5);
        for (int allowed : INTERVALS) if (allowed == value) return value;
        return 5;
    }
    boolean dock() { return prefs.getBoolean("dock", false); }
    int size() { return Math.max(0, Math.min(2, prefs.getInt("size", 1))); }
    int inset() { return Math.max(0, Math.min(2, prefs.getInt("inset", 1))); }
    void put(String k, int v) { prefs.edit().putInt(k,v).apply(); }
    void dock(boolean b) { prefs.edit().putBoolean("dock", b).apply(); }
    List<String> favorites(List<DeviceActions.AppEntry> apps) {
        Set<String> available = new HashSet<>();
        for (DeviceActions.AppEntry a : apps) available.add(a.pkg);
        List<String> result = new ArrayList<>();
        if (!prefs.contains("favorites")) {
            for (String pkg : new String[]{"com.xiaodianshi.tv.yst", "com.ktcp.video", "com.quark.yun.tv", "com.netease.cloudmusic.tv"})
                if (available.contains(pkg)) result.add(pkg);
            return result;
        }
        try {
            JSONArray a = new JSONArray(prefs.getString("favorites", "[]"));
            for (int i=0; i<a.length() && result.size()<MAX_FAVORITES; i++) {
                String pkg = a.optString(i);
                if (available.contains(pkg) && !result.contains(pkg)) result.add(pkg);
            }
        } catch (JSONException ignored) { }
        return result;
    }
    void saveFavorites(List<String> list) { prefs.edit().putString("favorites", new JSONArray(list).toString()).apply(); }
}
