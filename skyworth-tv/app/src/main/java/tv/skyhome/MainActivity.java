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
    private static final int WHITE = 0xffffffff, MUTED = 0xff9ca7aa, GREEN = 0xffb5c0bf;
    private FrameLayout root, home, homeBody, panel;
    private final List<MenuPage> pages = new ArrayList<>();
    private final Map<String,MenuPosition> menuPositions = new HashMap<>();
    private static final class MenuPosition {
        String focusTag;int scrollY;
        MenuPosition(MenuPage page){focusTag=page.focusTag;scrollY=page.scrollY;}
    }
    private TextView breadcrumb, menuHint;
    private ParentMenuView parentEcho;
    private static final class MenuPage {
        String key, title, subtitle, focusTag;
        LinearLayout view, content;
        ScrollView scroll;
        Runnable rebuild;
        boolean grid, reading;
        int scrollY;
        final List<TextView> actions = new ArrayList<>();
    }
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
            closeAllPanels();
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
    private int screenMargin() { return Math.round(getResources().getDisplayMetrics().widthPixels*.065f)+dp((prefs.inset()-1)*12); }
    private int contentTop() { return Math.round(getResources().getDisplayMetrics().heightPixels*.43f); }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private FrameLayout.LayoutParams full() { return new FrameLayout.LayoutParams(-1,-1); }
    private LinearLayout vertical() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView text(String value, float size, int color) {
        TextView v=new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setFontFeatureSettings("kern"); v.setIncludeFontPadding(false); return v;
    }
    private TextView button(String value, Runnable action) {
        TextActionView b=new TextActionView(this);
        b.setText(value); b.setTextSize(19); b.setTextColor(MUTED);
        b.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        b.setIncludeFontPadding(false); b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(3),dp(7),dp(12),dp(7));
        b.setSingleLine(true); b.setEllipsize(android.text.TextUtils.TruncateAt.END);
        b.setId(View.generateViewId());
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setFocusable(true); b.setFocusableInTouchMode(true); b.setClickable(true);
        b.setOnFocusChangeListener((v,focus)-> {
            if(focus && !buildingHome && panel==null && v.getTag()!=null) homeFocus=v.getTag().toString();
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
        int marginPx=screenMargin();
        int margin=Math.round(marginPx/getResources().getDisplayMetrics().density);
        TextView brand=text("山 岚  /  SKYHOME",14,GREEN); brand.setLetterSpacing(.12f);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
        bp.setMargins(dp(margin),dp(34),0,0); home.addView(brand,bp);
        LinearLayout time=vertical(); time.setGravity(Gravity.RIGHT);
        clock=text("",58,WHITE); clock.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
        clock.setSingleLine(true);
        date=text("",15,WHITE); time.addView(clock,new LinearLayout.LayoutParams(-2,-2)); LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-2,-2);dl.topMargin=dp(8);time.addView(date,dl);
        FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);
        tp.setMargins(0,dp(34),dp(margin),0);home.addView(time,tp); updateClock();

        homeBody=new FrameLayout(this);home.addView(homeBody,full());
        LinearLayout footer=new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL);
        String[] titles={"全部应用","信号源","投屏","原桌面","设置"};
        Runnable[] actions={()->showApps(false),()->notifyResult(DeviceActions.sources(this),"信号源入口不可用，请使用遥控器信号源键"),
                ()->notifyResult(DeviceActions.cast(this),"未找到可用的投屏应用"),()->notifyResult(DeviceActions.stockHome(this),"原桌面暂时无法打开"),this::showSettings};
        firstHome=null;
        for(int i=0;i<titles.length;i++) {
            TextView b=button(titles[i],actions[i]); b.setTag("tool"+i);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(Math.round(b.getPaint().measureText(titles[i])*1.25f)+dp(12),dp(46)); p.rightMargin=dp(23);footer.addView(b,p);
            if(firstHome==null) firstHome=b;
        }
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);
        fp.setMargins(dp(margin),0,0,dp(48)); homeBody.addView(footer,fp);
        TextView help=text("方向键选择 · 确认打开 · 长按编辑常用 · 菜单键设置",12,MUTED);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);hp.setMargins(dp(margin),0,0,dp(20));help.setTag("homeHelp");homeBody.addView(help,hp);
        caption=text(HomePreferences.NAMES[currentWallpaper],12,MUTED);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);cp.setMargins(0,0,dp(margin),dp(20));home.addView(caption,cp);

        buildList();
        buildingHome=false;
        if(panel==null) restoreHomeFocus(); else { layoutMenus(false); root.bringChildToFront(panel); }
    }
    private void buildList() {
        LinearLayout area=vertical();
        TextView eyebrow=text("常 用 应 用",13,GREEN);eyebrow.setPadding(dp(3),0,0,0);
        area.addView(eyebrow,new LinearLayout.LayoutParams(-1,dp(34)));
        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);scroll.setFillViewport(false);
        LinearLayout list=vertical();list.setPadding(dp(3),dp(6),dp(3),dp(6));
        for(String pkg:favorites) {
            TextView b=button(label(pkg),()->openApp(pkg));b.setTag(pkg);b.setTextSize(23+prefs.size()*3);
            b.setOnLongClickListener(v->{showFavoriteActions(pkg);return true;});
            LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,dp(48));
            row.bottomMargin=list.getChildCount()<favorites.size()-1?dp(3):0;list.addView(b,row);
            if(list.getChildCount()==1)firstHome=b;
        }
        if(favorites.isEmpty()) {
            TextView add=button("添加常用应用  +",()->showApps(true));add.setTag("add-favorite");
            list.addView(add,new LinearLayout.LayoutParams(-1,dp(48)));firstHome=add;
        }
        // Leave the favorites list through its explicit entry, regardless of row width.
        View allApps=home.findViewWithTag("tool0");
        list.getChildAt(list.getChildCount()-1).setNextFocusDownId(allApps.getId());
        int count=Math.max(1,favorites.size()), row=dp(48), gap=dp(3), padding=dp(6)*2;
        int available=getResources().getDisplayMetrics().heightPixels-contentTop()-dp(120)-dp(34);
        int visible=Math.min(count,Math.max(1,(available-padding+gap)/(row+gap)));
        int viewport=visible*row+(visible-1)*gap+padding;
        scroll.addView(list);area.addView(scroll,new LinearLayout.LayoutParams(-1,viewport));
        FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(dp(320),-2,Gravity.TOP|Gravity.LEFT);
        ap.leftMargin=screenMargin();ap.topMargin=contentTop();homeBody.addView(area,ap);
    }
    private void restoreHomeFocus() {
        View match=home.findViewWithTag(homeFocus);
        if(match!=null)match.requestFocus();else if(firstHome!=null)firstHome.requestFocus();
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

    private MenuPage topPage() { return pages.isEmpty()?null:pages.get(pages.size()-1); }
    private void rememberPage(MenuPage page) {
        if(page==null || !page.view.hasFocus())return;
        View focus=page.view.findFocus();
        if(focus!=null && focus.getTag()!=null)page.focusTag=focus.getTag().toString();
        page.scrollY=page.scroll.getScrollY();
    }
    private void rememberHome() {
        priorFocus=getCurrentFocus();
        if(priorFocus!=null && priorFocus.getTag()!=null)homeFocus=priorFocus.getTag().toString();
    }
    private LinearLayout sheet(String key,String title,String subtitle,boolean grid,Runnable rebuild) {
        rememberPage(topPage());
        if(panel==null) {
            rememberHome();handler.removeCallbacks(cycle);
            panel=new FrameLayout(this);panel.setClickable(true);panel.setClipChildren(true);root.addView(panel,full());
            breadcrumb=text("",12,MUTED);panel.addView(breadcrumb);
            menuHint=text("",12,MUTED);panel.addView(menuHint);
            parentEcho=new ParentMenuView(this);panel.addView(parentEcho);
        }
        MenuPage page=null;
        for(int i=0;i<pages.size();i++)if(pages.get(i).key.equals(key)) {
            while(pages.size()>i+1)panel.removeView(pages.remove(pages.size()-1).view);
            page=pages.get(i);break;
        }
        boolean added=page==null;
        if(added) {
            page=new MenuPage();page.key=key;page.view=vertical();page.view.setClipChildren(true);
            MenuPosition position=menuPositions.get(key);
            if(position!=null){page.focusTag=position.focusTag;page.scrollY=position.scrollY;}
            page.scroll=new ScrollView(this);page.scroll.setVerticalScrollBarEnabled(false);
            page.scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);page.scroll.setFillViewport(false);
            page.scroll.setFocusable(false);page.scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            page.content=vertical();page.content.setPadding(dp(3),dp(6),dp(3),dp(6));
            page.scroll.addView(page.content);pages.add(page);panel.addView(page.view);
        }
        page.title=title;page.subtitle=subtitle;page.grid=grid;page.reading=key.equals("licenses")||key.equals("info");page.rebuild=rebuild;
        page.content.removeAllViews();page.actions.clear();page.view.removeAllViews();
        page.scroll.setContentDescription("菜单："+title);
        page.view.addView(page.scroll,new LinearLayout.LayoutParams(-1,0,1));
        page.view.setVisibility(View.INVISIBLE);
        final MenuPage ready=page;
        // The caller fills the list synchronously. Measure only after all rows exist.
        page.view.post(()->{if(topPage()==ready){layoutMenus(added);restorePage(ready);}});
        return page.content;
    }
    private void highlightHome(View view, boolean showingMenu) {
        if(view instanceof TextView && view.getTag()!=null)view.setSelected(showingMenu && view.getTag().equals(homeFocus));
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)highlightHome(group.getChildAt(i),showingMenu);}
    }
    private void updateParentEcho(int width,int height,int margin) {
        List<String> labels=new ArrayList<>();int selected=-1,offset=0;String title;float size;
        if(pages.size()==1) {
            title="首页";size=23+prefs.size()*3;
            for(String pkg:favorites)labels.add(label(pkg));
            selected=favorites.indexOf(homeFocus);
            if(labels.isEmpty())labels.add("添加常用应用");
        } else {
            MenuPage previous=pages.get(pages.size()-2);title=previous.title;size=22+prefs.size();
            for(int i=0;i<previous.actions.size();i++) {
                TextView action=previous.actions.get(i);
                String value=action.getText().toString();
                if(!previous.grid){int split=value.indexOf(" · ");if(split>=0)value=value.substring(0,split);}
                labels.add(value);
                if(action.getTag()!=null&&action.getTag().equals(previous.focusTag))selected=i;
            }
            if(previous.grid) {
                int start=Math.max(0,selected-2),end=Math.min(labels.size(),start+6);
                labels=new ArrayList<>(labels.subList(start,end));selected-=start;
            } else offset=previous.scrollY;
        }
        int y=Math.round(height*.39f);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(Math.max(dp(20),margin-dp(12)),Math.max(dp(80),height-y-dp(112)));
        lp.topMargin=y;parentEcho.setLayoutParams(lp);
        parentEcho.showMenu(title,labels,selected,offset,size);parentEcho.setAlpha(.23f);
    }
    /** Center the actual list (or its capped scroll viewport); retain the left edge parent echo. */
    private void layoutMenus(boolean entering) {
        if(panel==null)return;
        int width=getResources().getDisplayMetrics().widthPixels;
        int height=getResources().getDisplayMetrics().heightPixels;
        int margin=screenMargin(),depth=pages.size();
        homeBody.setAlpha(0f);
        homeBody.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        homeBody.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        for(int i=0;i<depth;i++) {
            MenuPage page=pages.get(i);boolean current=i==depth-1;
            page.view.setVisibility(current?View.VISIBLE:View.INVISIBLE);
            page.view.setDescendantFocusability(current?ViewGroup.FOCUS_AFTER_DESCENDANTS:ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            page.view.setImportantForAccessibility(current?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            for(TextView b:page.actions){b.setEnabled(current);b.setSelected(false);}
            page.scroll.setOnTouchListener((v,event)->!current);
            page.scroll.setFocusable(current&&page.reading);page.scroll.setFocusableInTouchMode(current&&page.reading);
            if(!current)continue;
            int columnWidth=width-margin*2,padding=dp(6)*2;
            int maximum=Math.max(dp(94),Math.min(Math.round(height*.64f),height-dp(320)));
            page.content.measure(View.MeasureSpec.makeMeasureSpec(columnWidth,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            int needed=page.content.getMeasuredHeight(),columnHeight=Math.min(needed,maximum);
            if(!page.reading&&needed>maximum) {
                int stride=dp(page.grid?82:54);
                columnHeight=padding+Math.max(1,(maximum-padding)/stride)*stride;
            }
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(columnWidth,columnHeight);
            lp.leftMargin=margin;lp.topMargin=(height-columnHeight)/2;page.view.setLayoutParams(lp);
            page.view.animate().cancel();page.view.setTranslationY(0);page.view.setAlpha(1f);
            if(entering){page.view.setTranslationY(dp(8));page.view.setAlpha(0f);page.view.animate().translationY(0).alpha(1).setDuration(180).start();}
        }
        updateParentEcho(width,height,margin);
        StringBuilder path=new StringBuilder("首页");
        for(MenuPage page:pages)path.append(" / ").append(page.title);
        breadcrumb.setText(path);breadcrumb.setSingleLine(true);breadcrumb.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(width-margin*2,dp(25));
        bp.leftMargin=margin;bp.topMargin=dp(120);breadcrumb.setLayoutParams(bp);
        menuHint.setText(topPage().subtitle+"  ·  返回键回到上一级");menuHint.setSingleLine(true);menuHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(Math.round(width*.73f)-margin,dp(24),Gravity.BOTTOM|Gravity.LEFT);
        hp.leftMargin=margin;hp.bottomMargin=dp(25);menuHint.setLayoutParams(hp);
        root.bringChildToFront(panel);
    }
    private void wirePage(MenuPage page) {
        List<TextView> items=page.actions;
        int columns=page.grid?4:1;
        for(int i=0;i<items.size();i++) {
            TextView b=items.get(i);
            b.setNextFocusUpId(items.get(i>=columns?i-columns:i).getId());
            b.setNextFocusDownId(items.get(i+columns<items.size()?i+columns:i).getId());
            b.setNextFocusLeftId(items.get(page.grid&&i%columns>0?i-1:i).getId());
            b.setNextFocusRightId(items.get(page.grid&&i%columns<columns-1&&i+1<items.size()?i+1:i).getId());
        }
    }
    private void restorePage(MenuPage page) {
        if(topPage()!=page)return;
        wirePage(page);
        if(page.reading){page.scroll.requestFocus();page.scroll.scrollTo(0,page.scrollY);return;}
        View target=page.focusTag==null?null:page.content.findViewWithTag(page.focusTag);
        if(target==null&&!page.actions.isEmpty())target=page.actions.get(0);
        if(target!=null)target.requestFocus();
        // Restore exact scroll after focus's automatic reveal, without introducing trailing blank space.
        page.scroll.scrollTo(0,page.scrollY);
        if(target!=null && page.scrollY==0)page.scroll.requestChildFocus(page.content,target);
    }
    private void closePanel() {
        if(panel==null)return;
        MenuPage removed=topPage();rememberPage(removed);menuPositions.put(removed.key,new MenuPosition(removed));
        pages.remove(pages.size()-1);panel.removeView(removed.view);
        if(pages.isEmpty()){closeAllPanels();return;}
        MenuPage previous=topPage();
        previous.rebuild.run();
    }
    private void closeAllPanels() {
        if(panel!=null)root.removeView(panel);
        panel=null;parentEcho=null;pages.clear();menuPositions.clear();
        highlightHome(homeBody,false);homeBody.setAlpha(1f);homeBody.findViewWithTag("homeHelp").setVisibility(View.VISIBLE);homeBody.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        homeBody.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        restoreHomeFocus();priorFocus=null;scheduleCycle();
    }
    private TextView row(LinearLayout content,String label,Runnable action) {
        TextView b=button(label,action);b.setTextSize(22+prefs.size());
        MenuPage page=topPage();b.setTag("row"+page.actions.size());page.actions.add(b);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));content.addView(b,p);return b;
    }
    private void showApps(boolean edit) {
        refreshApps();
        LinearLayout content=sheet(edit?"edit-apps":"apps",edit?"编辑常用应用":"全部应用",edit?"确认添加或移除 · 最多 9 项 · 已选 "+favorites.size()+" 项":"长按应用编辑常用与顺序",true,()->showApps(edit));
        MenuPage page=topPage();LinearLayout gridRow=null;
        for(int i=0;i<apps.size();i++) {
            DeviceActions.AppEntry app=apps.get(i);
            if(i%4==0) {gridRow=new LinearLayout(this);content.addView(gridRow,new LinearLayout.LayoutParams(-1,-2));}
            boolean selected=favorites.contains(app.pkg);
            TextView b=button((edit&&selected?"✓  ":"")+app.name,()-> {
                if(edit){toggleFavorite(app.pkg);showApps(true);}else openApp(app.pkg);
            });
            b.setTag(app.pkg);b.setTextSize(18+prefs.size());b.setSingleLine(false);b.setMaxLines(2);
            b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.setOnLongClickListener(v->{showFavoriteActions(app.pkg);return true;});page.actions.add(b);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(82),1);p.rightMargin=dp(8);gridRow.addView(b,p);
        }
        if(gridRow!=null)while(gridRow.getChildCount()<4)gridRow.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        if(apps.isEmpty())row(content,"未找到应用 · 返回上级",this::closePanel);
    }
    private void toggleFavorite(String pkg) {
        if(favorites.contains(pkg))favorites.remove(pkg);
        else if(favorites.size()<HomePreferences.MAX_FAVORITES)favorites.add(pkg);
        else {Toast.makeText(this,"最多 9 项，请先移除一项",Toast.LENGTH_SHORT).show();return;}
        prefs.saveFavorites(favorites);buildHome();
    }
    private void showFavoriteActions(String pkg) {
        LinearLayout content=sheet("favorite:"+pkg,label(pkg),"常用应用管理",false,()->showFavoriteActions(pkg));
        row(content,"打开应用",()->openApp(pkg));
        boolean selected=favorites.contains(pkg);
        row(content,selected?"从常用移除":"添加到常用",()->{toggleFavorite(pkg);closePanel();});
        if(selected) {
            int index=favorites.indexOf(pkg);
            if(index>0)row(content,"向前移动",()->moveFavorite(pkg,-1));
            if(index<favorites.size()-1)row(content,"向后移动",()->moveFavorite(pkg,1));
        }
        row(content,"返回上一级",this::closePanel);
    }
    private void moveFavorite(String pkg,int delta) {
        int index=favorites.indexOf(pkg), target=index+delta;
        if(index>=0 && target>=0 && target<favorites.size()) {Collections.swap(favorites,index,target);prefs.saveFavorites(favorites);buildHome();}
        showFavoriteActions(pkg);
    }
    private void showSettings() {
        LinearLayout c=sheet("settings","设置","所有偏好保存在本机",false,this::showSettings);
        row(c,"背景 · "+HomePreferences.NAMES[prefs.wallpaper()],this::showWallpapers);
        row(c,"字号 · "+new String[]{"标准","大","特大"}[prefs.size()],()-> {
            prefs.put("size",(prefs.size()+1)%3);buildHome();showSettings();focusRow(1);
        });
        row(c,"屏幕边距 · "+new String[]{"紧凑","标准","宽"}[prefs.inset()],()-> {
            prefs.put("inset",(prefs.inset()+1)%3);buildHome();showSettings();focusRow(2);
        });
        row(c,"编辑常用 · "+favorites.size()+" / 9",()->showApps(true));
        row(c,"Home 键兼容 · "+(HomeKeyService.isEnabled(this)?"已开启":"未开启"),()->
                notifyResult(DeviceActions.launch(this,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),"请在系统辅助功能中管理山岚 Home 键返回服务"));
        row(c,"系统设置",()->notifyResult(DeviceActions.settings(this),"系统设置入口不可用"));
        row(c,"关于与兼容性",this::showAbout);
    }
    private void focusRow(int index) {
        MenuPage page=topPage();if(page==null)return;
        page.focusTag="row"+index;
        page.view.post(()-> {if(topPage()==page){View target=page.content.findViewWithTag(page.focusTag);if(target!=null)target.requestFocus();}});
    }
    private void showWallpapers() {
        LinearLayout c=sheet("background","背景","选择即保存 · 轮播仅在首页前台运行",false,this::showWallpapers);
        row(c,"自动轮播 · "+(prefs.interval()==0?"关闭":prefs.interval()+" 分钟"),()-> {
            int index=0;for(int i=0;i<HomePreferences.INTERVALS.length;i++)if(HomePreferences.INTERVALS[i]==prefs.interval())index=i;
            prefs.put("interval",HomePreferences.INTERVALS[(index+1)%HomePreferences.INTERVALS.length]);showWallpapers();
        });
        for(int i=0;i<HomePreferences.NAMES.length;i++) {
            final int index=i;
            row(c,HomePreferences.NAMES[i]+(prefs.wallpaper()==i?" · 当前":""),()-> {
                prefs.put("wallpaper",index);currentWallpaper=index;loadWallpaper();showWallpapers();focusRow(index+1);
            });
        }
        row(c,"返回上一级",this::closePanel);
    }
    private void showAbout() {
        LinearLayout c=sheet("about","关于山岚  1.2","版本 1.2.3 · 原生文字桌面",false,this::showAbout);
        row(c,"设备信息与使用说明",this::showInfo);
        row(c,"许可与照片署名",this::showLicenses);
        row(c,"返回上一级",this::closePanel);
    }
    private void showInfo() {
        LinearLayout c=sheet("info","设备信息与使用说明","上下键滚动阅读",false,this::showInfo);
        TextView body=text("设备："+Build.MANUFACTURER+" / "+Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" · API "+Build.VERSION.SDK_INT
                +"\n\n创维信号源通过系统选择面板打开；端口由系统管理。\n可通过系统将山岚设为默认桌面；Home 键返回首页。开机入口由电视系统决定。"
                +"\n\n离线运行，无账号、无广告。\nHome 兼容可选辅助功能服务，不获取屏幕内容。\n基于归藏电视的产品思路重新编写。参考项目：github.com/op7418/guizang-tv（MIT）。"
                +"\n\n照片：Geio Tischler、Arina Avdzhian、Łukasz Rawa、Pascal Debrunner、Patrick Shaun / Unsplash。\n完整照片署名与参考项目许可随 APK 保存。",18,WHITE);
        body.setLineSpacing(dp(5),1);body.setPadding(dp(8),dp(6),dp(8),dp(20));c.addView(body);
    }
    private void showLicenses() {
        LinearLayout c=sheet("licenses","许可与照片署名","上下键滚动阅读 · MIT 与 Unsplash 许可",false,this::showLicenses);
        StringBuilder value=new StringBuilder();
        for(String path:new String[]{"LICENSE.txt","wallpapers/UNSPLASH-NOTICE.txt"}) {
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(getAssets().open(path),"UTF-8"))) {
                String line;while((line=reader.readLine())!=null)value.append(line).append('\n');value.append("\n");
            }catch(IOException ignored){}
        }
        TextView t=text(value.toString(),15,WHITE);t.setLineSpacing(dp(4),1);c.addView(t);
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(key==KeyEvent.KEYCODE_MENU && event.getRepeatCount()==0){showSettings();return true;}
        if(topPage()!=null&&topPage().reading&&(key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_UP)) {
            topPage().scroll.smoothScrollBy(0,dp(key==KeyEvent.KEYCODE_DPAD_DOWN?80:-80));return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_LEFT && topPage()!=null) {
            MenuPage page=topPage();int index=page.actions.indexOf(getCurrentFocus());
            if(!page.grid || index<0 || index%4==0){closePanel();return true;}
        }
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
        LinearLayout c=sheet("exit","离开山岚","选择要返回的位置",false,this::onBackPressed);
        row(c,"继续使用山岚",this::closePanel);
        row(c,"打开创维原桌面",()->notifyResult(DeviceActions.stockHome(this),"原桌面暂时无法打开"));
        row(c,"退出应用",this::finish);
    }
}
