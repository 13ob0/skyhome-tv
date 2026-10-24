package tv.skyhome;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int WHITE = 0xfff4f2e9, MUTED = 0xffb9c4c0, GREEN = 0xffd2e3cb;
    private FrameLayout root, home, panel;
    private ImageView photo;
    private TextView clock, date, caption;
    private HomePreferences prefs;
    private List<DeviceActions.AppEntry> apps = new ArrayList<>();
    private List<String> favorites = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private int currentWallpaper, photoGeneration;
    private boolean active, buildingHome;
    private String homeFocus = "";
    private View firstHome, priorFocus;
    private View heldCenterView;
    private boolean centerLongPressHandled;
    private final Runnable clockTick = new Runnable() {
        @Override public void run() { updateClock(); handler.postDelayed(this, 60000 - System.currentTimeMillis()%60000); }
    };
    private final Runnable cycle = new Runnable() {
        @Override public void run() {
            if (active && panel == null && prefs.wallpaper() < 5 && prefs.interval() > 0) {
                currentWallpaper = (currentWallpaper+1)%5;
                loadWallpaper();
            }
            scheduleCycle();
        }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            refreshApps();
            if (panel == null) buildHome();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new HomePreferences(getSharedPreferences("home", MODE_PRIVATE));
        homeFocus=prefs.prefs.getString("focus", "");
        currentWallpaper = prefs.wallpaper();
        if (state != null) { currentWallpaper=state.getInt("photo", currentWallpaper); homeFocus=state.getString("focus", ""); }
        root = new FrameLayout(this);
        root.setBackgroundColor(0xff121c1c);
        setContentView(root);
        photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(photo, full());
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xd610191a,0x3012191a,0x5012191a}));
        root.addView(shade, full());
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x0012191a,0x0012191a,0xc412191a}));
        root.addView(bottom, full());
        refreshApps();
        buildHome();
        loadWallpaper();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packages, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(packages, filter);
    }
    @Override protected void onResume() {
        super.onResume(); active=true; immersive();
        handler.removeCallbacks(clockTick); clockTick.run();
        refreshApps();
        if (panel == null) buildHome();
        scheduleCycle();
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if(Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME)) {
            // Pressing Home always returns to the desktop, even while a sheet is open.
            closePanel();
            immersive();
        }
    }
    @Override protected void onPause() {
        prefs.prefs.edit().putString("focus",homeFocus).apply();
        active=false; handler.removeCallbacks(clockTick); handler.removeCallbacks(cycle); super.onPause();
    }
    @Override protected void onDestroy() {
        photoGeneration++; handler.removeCallbacksAndMessages(null); worker.shutdownNow();
        unregisterReceiver(packages); super.onDestroy();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if(hasFocus) immersive(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("photo",currentWallpaper); out.putString("focus",homeFocus); super.onSaveInstanceState(out);
    }
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private FrameLayout.LayoutParams full() { return new FrameLayout.LayoutParams(-1,-1); }
    private LinearLayout vertical() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value, float size, int color) {
        TextView v=new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setFontFeatureSettings("kern"); v.setIncludeFontPadding(false); return v;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private StateListDrawable focusBackground() {
        StateListDrawable s=new StateListDrawable();
        GradientDrawable focused=round(0xefe5eddf,14);
        s.addState(new int[]{android.R.attr.state_focused},focused);
        s.addState(new int[]{android.R.attr.state_pressed},round(0xffd1e1cb,14));
        s.addState(new int[]{},round(0x322b3937,14)); return s;
    }
    private TextView button(String value, Runnable action) {
        TextView b=text(value,17,WHITE); b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(18),dp(12),dp(18),dp(12));
        b.setBackground(focusBackground()); b.setFocusable(true); b.setFocusableInTouchMode(true); b.setClickable(true);
        b.setOnFocusChangeListener((v,focus)-> {
            b.setTextColor(focus?0xff182822:WHITE);
            if(!buildingHome && panel==null && v.getTag()!=null) homeFocus=v.getTag().toString();
        });
        b.setOnClickListener(v->action.run()); return b;
    }
    private void refreshApps() { apps=DeviceActions.apps(this); favorites=prefs.favorites(apps); }
    private String label(String pkg) {
        for(DeviceActions.AppEntry a:apps) if(a.pkg.equals(pkg)) return a.name;
        return pkg;
    }
    private void updateClock() {
        if(clock==null)return;
        Date now=new Date();
        clock.setText(new SimpleDateFormat("HH:mm",Locale.CHINA).format(now));
        date.setText(new SimpleDateFormat("M月d日  EEEE",Locale.CHINA).format(now));
    }
    private void buildHome() {
        buildingHome=true;
        if(home!=null)root.removeView(home);
        home=new FrameLayout(this); root.addView(home,full());
        int margin=40+prefs.inset()*12;
        TextView brand=text("山 岚  /  SKYHOME",14,GREEN); brand.setLetterSpacing(.12f);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
        bp.setMargins(dp(margin),dp(34),0,0); home.addView(brand,bp);
        LinearLayout time=vertical(); time.setGravity(Gravity.RIGHT);
        clock=text("",58,WHITE); clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        clock.setSingleLine(true);
        date=text("",15,WHITE); time.addView(clock,new LinearLayout.LayoutParams(-2,-2)); LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-2,-2);dl.topMargin=dp(8);time.addView(date,dl);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);
        tp.setMargins(0,dp(34),dp(margin),0);home.addView(time,tp); updateClock();

        LinearLayout footer=new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL);
        String[] titles={"全部应用","信号源","投屏","原桌面","设置"};
        Runnable[] actions={()->showApps(false),()->notifyResult(DeviceActions.sources(this),"信号源入口不可用，请使用遥控器信号源键"),
                ()->notifyResult(DeviceActions.cast(this),"未找到可用的投屏应用"),()->notifyResult(DeviceActions.stockHome(this),"原桌面暂时无法打开"),this::showSettings};
        firstHome=null;
        for(int i=0;i<titles.length;i++) {
            TextView b=button(titles[i],actions[i]); b.setTag("tool"+i);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(46)); p.rightMargin=dp(10);footer.addView(b,p);
            if(firstHome==null) firstHome=b;
        }
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);
        fp.setMargins(dp(margin),0,0,dp(48)); home.addView(footer,fp);
        TextView help=text("方向键选择 · 确认打开 · 长按编辑常用 · 菜单键设置",12,MUTED);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);hp.setMargins(dp(margin),0,0,dp(20));home.addView(help,hp);
        caption=text(HomePreferences.NAMES[currentWallpaper],12,MUTED);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);cp.setMargins(0,0,dp(margin),dp(20));home.addView(caption,cp);

        if(prefs.dock()) buildDock(margin); else buildList(margin);
        buildingHome=false;
        if(panel==null) restoreHomeFocus(); else { home.setVisibility(View.INVISIBLE); root.bringChildToFront(panel); }
    }
    private void buildList(int margin) {
        LinearLayout area=vertical();
        TextView eyebrow=text("常 用 应 用",12,GREEN);eyebrow.setPadding(dp(16),0,0,dp(12));area.addView(eyebrow);
        ScrollView scroll=new ScrollView(this); scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false); scroll.setFillViewport(false);
        scroll.setFocusable(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalFadingEdgeEnabled(false);
        LinearLayout list=vertical();
        for(String pkg:favorites) {
            TextView b=button(label(pkg),()->openApp(pkg)); b.setTag(pkg); b.setTextSize(23+prefs.size()*3);
            b.setPadding(dp(16),dp(7),dp(22),dp(7));
            b.setBackground(listBackground());
            b.setOnLongClickListener(v->{showFavoriteActions(pkg);return true;});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));
            p.bottomMargin=list.getChildCount()<favorites.size()-1?dp(3):0;list.addView(b,p);
            if(list.getChildCount()==1)firstHome=b;
        }
        if(favorites.isEmpty()) list.addView(button("添加常用应用  +",()->showApps(true)),new LinearLayout.LayoutParams(-1,dp(48)));
        scroll.addView(list);area.addView(scroll,new LinearLayout.LayoutParams(dp(360),-2));
        FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(dp(370),-2,Gravity.LEFT|Gravity.BOTTOM);
        ap.setMargins(dp(margin),dp(150),0,dp(120));
        int maxHeight= Math.max(dp(180),getResources().getDisplayMetrics().heightPixels-dp(280));
        area.setLayoutParams(ap); home.addView(area);
        // Measure cap keeps all nine favorites scrollable on 720p and 1080p TVs.
        // Sum rounded pixel dimensions, rather than rounding the combined dp value.
        // At density 1.5, three 48dp rows + 3dp margins otherwise overflow by one pixel.
        int contentHeight=Math.max(1,favorites.size())*dp(48)+Math.max(0,favorites.size()-1)*dp(3);
        scroll.getLayoutParams().height=Math.min(contentHeight,maxHeight);
    }
    private StateListDrawable listBackground() {
        StateListDrawable s=new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused},round(0xefe5eddf,12));
        s.addState(new int[]{android.R.attr.state_pressed},round(0xffd1e1cb,12));
        s.addState(new int[]{},round(Color.TRANSPARENT,12));return s;
    }
    private void buildDock(int margin) {
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setClipToPadding(false);
        LinearLayout dock=new LinearLayout(this);dock.setPadding(dp(12),dp(12),dp(12),dp(12));dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setBackground(round(0x991b2928,24));
        for(String pkg:favorites) {
            TextView b=button(label(pkg),()->openApp(pkg));b.setGravity(Gravity.CENTER);b.setTag(pkg);b.setTextSize(15+prefs.size());
            b.setMaxLines(2);b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            try { Drawable icon=getPackageManager().getApplicationIcon(pkg);icon.setBounds(0,0,dp(42),dp(42));b.setCompoundDrawables(null,icon,null,null);b.setCompoundDrawablePadding(dp(13)); } catch(Exception ignored) {}
            b.setOnLongClickListener(v->{showFavoriteActions(pkg);return true;});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(140),dp(116));p.setMargins(dp(3),0,dp(3),0);dock.addView(b,p);
            if(dock.getChildCount()==1)firstHome=b;
        }
        if(favorites.isEmpty())dock.addView(button("添加常用应用  +",()->showApps(true)));
        scroll.addView(dock);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,dp(144),Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM);
        p.setMargins(dp(margin),0,dp(margin),dp(130));home.addView(scroll,p);
    }
    private void restoreHomeFocus() {
        View match=home.findViewWithTag(homeFocus);
        (match!=null?match:firstHome).requestFocus();
    }
    private void openApp(String pkg) { notifyResult(DeviceActions.app(this,pkg),"应用暂时无法打开，可能已被停用或卸载"); }
    private void notifyResult(boolean success,String failure) { if(!success)Toast.makeText(this,failure,Toast.LENGTH_LONG).show(); }
    private void scheduleCycle() {
        handler.removeCallbacks(cycle);
        if(active && panel==null && prefs.wallpaper()<5 && prefs.interval()>0)handler.postDelayed(cycle,prefs.interval()*60000L);
    }
    private void loadWallpaper() {
        final int generation=++photoGeneration, index=currentWallpaper;
        if(caption!=null)caption.setText(HomePreferences.NAMES[index]);
        if(index>=5) { photo.setImageDrawable(null); return; }
        worker.execute(()-> {
            Bitmap bitmap=null;
            try {
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                try(InputStream in=getAssets().open("wallpapers/"+HomePreferences.FILES[index])) { BitmapFactory.decodeStream(in,null,bounds); }
                BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;
                // Limit decode to about 2 megapixels, including on physical 4K panels.
                int sample=1;while((bounds.outWidth/sample)*(long)(bounds.outHeight/sample)>2100000L)sample*=2;
                options.inSampleSize=sample;
                try(InputStream in=getAssets().open("wallpapers/"+HomePreferences.FILES[index])){bitmap=BitmapFactory.decodeStream(in,null,options);}
            } catch(IOException | OutOfMemoryError ignored) { }
            final Bitmap loaded=bitmap;
            handler.post(()-> {
                if(isDestroyed() || generation!=photoGeneration) {if(loaded!=null)loaded.recycle();return;}
                photo.setImageBitmap(loaded);
                if(loaded!=null){photo.setAlpha(.35f);photo.animate().alpha(1f).setDuration(500).start();}
            });
        });
    }

    /** Modal sheets stay inside the same view tree; no focus can leak to the home underneath. */
    private LinearLayout sheet(String title,String subtitle) {
        boolean wasOpen=panel!=null;
        if(!wasOpen)priorFocus=getCurrentFocus();
        if(panel!=null)root.removeView(panel);
        handler.removeCallbacks(cycle);
        home.setVisibility(View.INVISIBLE);
        panel=new FrameLayout(this);panel.setBackgroundColor(0xe9111d1c);root.addView(panel,full());
        LinearLayout outer=vertical();outer.setPadding(dp(58),dp(34),dp(58),dp(26)); panel.addView(outer,full());
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=text(title,30,WHITE);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView back=button("返回",this::closePanel);top.addView(back,new LinearLayout.LayoutParams(dp(90),dp(44)));outer.addView(top);
        TextView hint=text(subtitle,14,MUTED);hint.setPadding(0,dp(14),0,dp(20));outer.addView(hint);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=vertical();content.setPadding(dp(3),dp(3),dp(3),dp(8));scroll.addView(content);
        TextView bottom=text("方向键选择 · 确认操作 · 返回键回到首页",12,MUTED);bottom.setPadding(0,dp(14),0,0);outer.addView(bottom);
        back.requestFocus();
        return content;
    }
    private void closePanel() {
        if(panel==null)return;
        root.removeView(panel);panel=null;home.setVisibility(View.VISIBLE);
        if(priorFocus!=null && priorFocus.isAttachedToWindow())priorFocus.requestFocus();else restoreHomeFocus();
        priorFocus=null;scheduleCycle();
    }
    private TextView row(LinearLayout content,String label,Runnable action) {
        TextView b=button(label,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));p.bottomMargin=dp(9);content.addView(b,p);
        if(content.getChildCount()==1)b.requestFocus();return b;
    }
    private void showApps(boolean edit) {
        refreshApps();
        LinearLayout content=sheet(edit?"编辑常用应用":"全部应用",edit?"选择应用添加或移除 · 最多 9 项 · 已选 "+favorites.size()+" 项":"长按应用可添加到常用；常用应用也可长按调整顺序");
        LinearLayout gridRow=null;View first=null;
        for(int i=0;i<apps.size();i++) {
            DeviceActions.AppEntry app=apps.get(i);
            if(i%5==0) {gridRow=new LinearLayout(this);content.addView(gridRow,new LinearLayout.LayoutParams(-1,-2));}
            boolean selected=favorites.contains(app.pkg);
            TextView b=button((edit && selected?"✓  ":"")+app.name,()-> {
                if(edit){
                    toggleFavorite(app.pkg);showApps(true);
                    View selectedApp=panel.findViewWithTag(app.pkg);
                    if(selectedApp!=null)selectedApp.requestFocus();
                }else openApp(app.pkg);
            });
            b.setTag(app.pkg);b.setGravity(Gravity.CENTER);b.setTextSize(15);b.setMaxLines(2);b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            try { Drawable icon=getPackageManager().getApplicationIcon(app.pkg);icon.setBounds(0,0,dp(36),dp(36));b.setCompoundDrawables(null,icon,null,null);b.setCompoundDrawablePadding(dp(10)); }catch(Exception ignored) {}
            b.setOnLongClickListener(v->{showFavoriteActions(app.pkg);return true;});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(110),1);p.setMargins(0,0,dp(10),dp(10));gridRow.addView(b,p);
            if(first==null)first=b;
        }
        if(gridRow!=null)while(gridRow.getChildCount()<5)gridRow.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        if(first!=null)first.requestFocus();else content.addView(text("未找到可启动的应用",20,WHITE));
    }
    private void toggleFavorite(String pkg) {
        if(favorites.contains(pkg))favorites.remove(pkg);
        else if(favorites.size()<HomePreferences.MAX_FAVORITES)favorites.add(pkg);
        else {Toast.makeText(this,"最多 9 项，请先移除一项",Toast.LENGTH_SHORT).show();return;}
        prefs.saveFavorites(favorites);buildHome();
    }
    private void showFavoriteActions(String pkg) {
        LinearLayout content=sheet(label(pkg),"常用应用管理");
        row(content,"打开应用",()->openApp(pkg));
        boolean selected=favorites.contains(pkg);
        row(content,selected?"从常用移除":"添加到常用",()->{toggleFavorite(pkg);showApps(true);});
        if(selected) {
            int index=favorites.indexOf(pkg);
            if(index>0)row(content,"向前移动",()->moveFavorite(pkg,-1));
            if(index<favorites.size()-1)row(content,"向后移动",()->moveFavorite(pkg,1));
        }
        row(content,"返回常用编辑",()->showApps(true));
    }
    private void moveFavorite(String pkg,int delta) {
        int index=favorites.indexOf(pkg), target=index+delta;
        if(index>=0 && target>=0 && target<favorites.size()) {Collections.swap(favorites,index,target);prefs.saveFavorites(favorites);buildHome();}
        showFavoriteActions(pkg);
    }
    private void showSettings() {
        LinearLayout c=sheet("设置","让电视回到简单 · 所有偏好保存在本机");
        row(c,"背景                              "+HomePreferences.NAMES[prefs.wallpaper()],this::showWallpapers);
        row(c,"首页布局                       "+(prefs.dock()?"横向 Dock":"大字列表"),()-> {
            prefs.dock(!prefs.dock());buildHome();showSettings();focusRow(1);
        });
        row(c,"字号                              "+new String[]{"标准","大","特大"}[prefs.size()],()-> {
            prefs.put("size",(prefs.size()+1)%3);buildHome();showSettings();focusRow(2);
        });
        row(c,"屏幕边距                       "+new String[]{"紧凑","标准","宽"}[prefs.inset()],()-> {
            prefs.put("inset",(prefs.inset()+1)%3);buildHome();showSettings();focusRow(3);
        });
        row(c,"编辑常用应用                 "+favorites.size()+" / 9",()->showApps(true));
        row(c,"Home 键兼容                  "+(HomeKeyService.isEnabled(this)?"已开启":"未开启"),()->
                notifyResult(DeviceActions.launch(this,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),"请在系统辅助功能中管理山岚 Home 键返回服务"));
        row(c,"系统设置",()->notifyResult(DeviceActions.settings(this),"系统设置入口不可用"));
        row(c,"关于与兼容性",this::showAbout);
    }
    private void focusRow(int index) {
        if(panel==null)return;
        // Content is inside the only scroll view in the sheet.
        LinearLayout outer=(LinearLayout)panel.getChildAt(0);
        ScrollView scroll=(ScrollView)outer.getChildAt(2);
        LinearLayout content=(LinearLayout)scroll.getChildAt(0);
        if(index<content.getChildCount())content.getChildAt(index).requestFocus();
    }
    private void showWallpapers() {
        LinearLayout c=sheet("背景","选择即保存 · 轮播仅在首页前台运行");
        row(c,"自动轮播                       "+(prefs.interval()==0?"关闭":prefs.interval()+" 分钟"),()-> {
            int index=0;for(int i=0;i<HomePreferences.INTERVALS.length;i++)if(HomePreferences.INTERVALS[i]==prefs.interval())index=i;
            prefs.put("interval",HomePreferences.INTERVALS[(index+1)%HomePreferences.INTERVALS.length]);showWallpapers();
        });
        for(int i=0;i<HomePreferences.NAMES.length;i++) {
            final int index=i;
            row(c,(prefs.wallpaper()==i?"✓  ":"     ")+HomePreferences.NAMES[i],()-> {
                prefs.put("wallpaper",index);currentWallpaper=index;loadWallpaper();showWallpapers();focusRow(index+1);
            });
        }
        row(c,"返回设置",this::showSettings);
    }
    private void showAbout() {
        LinearLayout c=sheet("山岚桌面  1.0","为创维电视重新设计的轻量桌面");
        TextView body=text("设备："+Build.MANUFACTURER+" / "+Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT
                +"\n\n创维信号源通过系统选择面板打开；端口由系统管理。\n可通过系统将山岚设为默认桌面；Home 键返回首页。开机入口由电视系统决定。"
                +"\n\n离线运行，无账号、无广告。\nHome 兼容可选辅助功能服务，不获取屏幕内容。\n基于归藏电视的产品思路重新编写。参考项目：github.com/op7418/guizang-tv（MIT）。"
                +"\n\n照片：Geio Tischler、Arina Avdzhian、Łukasz Rawa、Pascal Debrunner、Patrick Shaun / Unsplash。\n完整照片署名与参考项目许可随 APK 保存。",18,WHITE);
        body.setLineSpacing(dp(5),1);body.setPadding(dp(8),dp(6),dp(8),dp(20));c.addView(body);
        row(c,"许可与照片署名",this::showLicenses);
        row(c,"返回设置",this::showSettings);
    }
    private void showLicenses() {
        LinearLayout c=sheet("许可与照片署名","参考项目 MIT 许可 · 照片 Unsplash License");
        StringBuilder value=new StringBuilder();
        for(String path:new String[]{"LICENSE.txt","wallpapers/UNSPLASH-NOTICE.txt"}) {
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(getAssets().open(path),"UTF-8"))) {
                String line;while((line=reader.readLine())!=null)value.append(line).append('\n');value.append("\n");
            }catch(IOException ignored){}
        }
        TextView t=text(value.toString(),15,WHITE);t.setLineSpacing(dp(4),1);c.addView(t);row(c,"返回关于",this::showAbout);
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_MENU && event.getRepeatCount()==0){showSettings();return true;}
        return super.onKeyDown(key,event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if(event.getAction()==KeyEvent.ACTION_DOWN) {
                if(event.getRepeatCount()==0) {
                    heldCenterView=getCurrentFocus();centerLongPressHandled=false;
                } else if(heldCenterView!=null && heldCenterView.isLongClickable()) {
                    // Vendor remotes may report a long hold as repeat events instead of a timed touch.
                    if(!centerLongPressHandled && getCurrentFocus()==heldCenterView) {
                        heldCenterView.cancelLongPress();heldCenterView.performLongClick();
                    }
                    centerLongPressHandled=true;return true;
                }
            } else if(event.getAction()==KeyEvent.ACTION_UP) {
                boolean consumed=centerLongPressHandled;
                if(consumed && heldCenterView!=null)heldCenterView.setPressed(false);
                heldCenterView=null;centerLongPressHandled=false;
                if(consumed)return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed() {
        if(panel!=null){closePanel();return;}
        LinearLayout c=sheet("离开山岚","选择要返回的位置");
        row(c,"继续使用山岚",this::closePanel);
        row(c,"打开创维原桌面",()->notifyResult(DeviceActions.stockHome(this),"原桌面暂时无法打开"));
        row(c,"退出应用",this::finish);
    }
}
