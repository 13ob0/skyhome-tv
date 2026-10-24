package tv.skyhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** HOME compatibility: key filtering plus two exact vendor desktop window classes. */
public final class HomeKeyService extends AccessibilityService {
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if(event.getKeyCode()!=KeyEvent.KEYCODE_HOME)return false;
        if(event.getAction()==KeyEvent.ACTION_UP && !event.isCanceled())goHome();
        return true;
    }
    private void goHome() {
        Intent home=new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setClass(this,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {startActivity(home);}
        catch(RuntimeException failure) {performGlobalAction(GLOBAL_ACTION_HOME);}
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)return;
        String pkg=String.valueOf(event.getPackageName()), cls=String.valueOf(event.getClassName());
        boolean office="com.skyworth.officehomepage".equals(pkg)
                && "com.skyworth.officehomepage.view.HomepageActivity".equals(cls);
        boolean stock="com.tianci.movieplatform".equals(pkg)
                && ("com.coocaa.homepage.vast.HomePageActivity".equals(cls)
                || "com.ccos.cooui.movieplatform.MainActivity".equals(cls));
        if(office || stock) {
            long allowedUntil=getSharedPreferences("home",MODE_PRIVATE).getLong("stock_launch_until",0);
            long remaining=allowedUntil-SystemClock.elapsedRealtime();
            // A stock desktop launch creates several initial windows. Allow only that brief transition.
            if(remaining>0 && remaining<=1500)return;
        }
        if(office || stock)goHome();
    }
    @Override public void onInterrupt() { }
    static void allowStockLaunch(Context c) {
        c.getSharedPreferences("home",MODE_PRIVATE).edit().putLong("stock_launch_until",SystemClock.elapsedRealtime()+1500).apply();
    }
    static void cancelStockLaunch(Context c) {
        c.getSharedPreferences("home",MODE_PRIVATE).edit().remove("stock_launch_until").apply();
    }

    static boolean isEnabled(Context context) {
        String enabled=Settings.Secure.getString(context.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(enabled==null)return false;
        ComponentName service=new ComponentName(context,HomeKeyService.class);
        for(String id:enabled.split(":"))if(service.equals(ComponentName.unflattenFromString(id)))return true;
        return false;
    }
}
