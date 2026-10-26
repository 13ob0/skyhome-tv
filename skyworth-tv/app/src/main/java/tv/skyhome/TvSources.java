package tv.skyhome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;

/** Small adapter for the source protocol used by the Skyworth system chooser. */
final class TvSources {
    private static final Uri BASE = Uri.parse("content://com.skyworth.tv.sdk.provider");
    // This firmware returns null for a null/empty projection, even for known paths.
    private static final String[] COLUMNS = {"sourceName", "sourceIndex", "displayName"};

    static final class Source {
        final String name, label;
        final int index;
        Source(String name, int index, String label) {
            this.name=name; this.index=index;
            this.label=label==null || label.trim().isEmpty()?name:label;
        }
        String key() { return "source:"+name+":"+index; }
        boolean same(Source other) {
            return other!=null && name.equals(other.name) && index==other.index;
        }
    }
    static final class Snapshot {
        final List<Source> sources;
        final Source current;
        Snapshot(List<Source> sources, Source current) { this.sources=sources; this.current=current; }
    }
    private static List<Source> query(Context context, String path) {
        List<Source> result=new ArrayList<>();
        try(Cursor cursor=context.getContentResolver().query(
                Uri.withAppendedPath(BASE,path),COLUMNS,null,null,null)) {
            if(cursor==null)throw new IllegalStateException("Source provider returned no cursor");
            int name=cursor.getColumnIndexOrThrow("sourceName");
            int index=cursor.getColumnIndexOrThrow("sourceIndex");
            int label=cursor.getColumnIndexOrThrow("displayName");
            while(cursor.moveToNext()) {
                String value=cursor.getString(name);
                if(value==null || value.isEmpty() || cursor.isNull(index))continue;
                Source source=new Source(value,cursor.getInt(index),cursor.getString(label));
                boolean duplicate=false;
                for(Source existing:result)if(existing.same(source)){duplicate=true;break;}
                if(!duplicate)result.add(source);
            }
        }
        return result;
    }
    private static Source current(Context context) {
        List<Source> sources=query(context,"system/current_source");
        return sources.isEmpty()?null:sources.get(0);
    }
    static Snapshot read(Context context) {
        List<Source> sources=query(context,"system/source_list");
        Source selected=null;
        try { selected=current(context); } catch(RuntimeException ignored) { }
        return new Snapshot(sources,selected);
    }
    static boolean select(Context context, Source target) {
        // Recheck availability; never send a stale or fabricated port mapping.
        boolean available=false;
        for(Source source:query(context,"system/source_list"))if(source.same(target)){available=true;break;}
        if(!available)return false;
        Bundle args=new Bundle();
        args.putString("name",target.name); args.putInt("index",target.index); args.putInt("flag",0);
        Bundle result=context.getContentResolver().call(Uri.withAppendedPath(BASE,"method"),
                "METHOD_GOTO_SOURCE",null,args);
        if(result==null || !result.getBoolean("ret",false))return false;
        // The method starts an asynchronous transition. Its return value alone is insufficient.
        for(int attempt=0;attempt<12;attempt++) {
            SystemClock.sleep(200);
            if(target.same(current(context)))return true;
        }
        return false;
    }
}
