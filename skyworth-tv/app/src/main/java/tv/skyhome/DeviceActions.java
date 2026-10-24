package tv.skyhome;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import android.provider.Settings;
import java.util.*;

/** Public, permission-checked entry points. Never writes vendor system settings. */
final class DeviceActions {
    static boolean isSkyworth() {
        String brand = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT);
        return brand.contains("skyworth") || brand.contains("coocaa");
    }
    static boolean launch(Context c, Intent intent) {
        if (intent == null) return false;
        PackageManager pm = c.getPackageManager();
        ResolveInfo resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved == null) resolved = pm.resolveActivity(intent, 0);
        if (resolved == null || resolved.activityInfo == null) return false;
        ActivityInfo a = resolved.activityInfo;
        if (!a.enabled || !a.applicationInfo.enabled || !a.exported) return false;
        if (a.permission != null && c.checkSelfPermission(a.permission) != PackageManager.PERMISSION_GRANTED) return false;
        try {
            c.startActivity(new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) { return false; }
    }
    static boolean app(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        return launch(c, i);
    }
    static boolean sources(Context c) {
        // SWITCH_SOURCE in softsettings selects a broadcast licence, not an HDMI input.
        if (isSkyworth() && launch(c, new Intent("coocaa.intent.action.SOURCE_DIALOG")
                .setPackage("com.tianci.movieplatform"))) return true;
        return launch(c, new Intent("android.settings.TV_INPUT_SETTINGS"));
    }
    static boolean settings(Context c) {
        return launch(c, new Intent(Settings.ACTION_SETTINGS))
                || (isSkyworth() && app(c, "com.skyworth.setting"));
    }
    static boolean stockHome(Context c) {
        HomeKeyService.allowStockLaunch(c);
        if (isSkyworth() && launch(c, new Intent("coocaa.intent.action.HOME_MAIN").setPackage("com.tianci.movieplatform"))) return true;
        if (isSkyworth() && app(c, "com.tianci.movieplatform")) return true;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = c.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        boolean opened=ri != null && ri.activityInfo != null && !c.getPackageName().equals(ri.activityInfo.packageName)
                && launch(c, home);
        if(!opened)HomeKeyService.cancelStockLaunch(c);
        return opened;
    }
    static boolean cast(Context c) {
        for (String pkg : new String[]{"com.hpplay.happyplay.aw", "com.skyworth.smartcast", "com.dangbei.screencast"})
            if (app(c, pkg)) return true;
        return isSkyworth() && launch(c, new Intent("coocaa.skycast.upgrade.tool.main").setPackage("com.coocaa.skycast.tool"));
    }
    static List<AppEntry> apps(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, AppEntry> map = new HashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo r : pm.queryIntentActivities(intent, 0)) {
                ActivityInfo a = r.activityInfo;
                if (a == null || !a.exported || !a.enabled || !a.applicationInfo.enabled || a.packageName.equals(c.getPackageName())) continue;
                if (a.permission != null && c.checkSelfPermission(a.permission) != PackageManager.PERMISSION_GRANTED) continue;
                map.put(a.packageName, new AppEntry(a.packageName, r.loadLabel(pm).toString()));
            }
        }
        List<AppEntry> list = new ArrayList<>(map.values());
        java.text.Collator collator = java.text.Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        Collections.sort(list, (a,b) -> collator.compare(a.name, b.name));
        return list;
    }
    static final class AppEntry {
        final String pkg, name;
        AppEntry(String pkg, String name) { this.pkg = pkg; this.name = name; }
    }
}
