package dev.stillframe.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;
import java.io.InputStream;
import java.util.*;

/** Reads conventional icon-pack resources. Never starts the pack's activities. */
final class IconPackSupport {
    static final String[] ACTIONS = {"org.adw.launcher.THEMES", "org.adw.ActivityStarter.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme"};
    private final Context context;
    private String selected = "";
    private Resources resources;
    private final Map<String,String> components = new HashMap<>();
    private final Map<String,String> packages = new HashMap<>();
    IconPackSupport(Context context) { this.context = context; }

    JSONArray discover() {
        Map<String,String> found = new TreeMap<>();
        PackageManager pm = context.getPackageManager();
        for (String action : ACTIONS) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(action), 0)) {
                if (info.activityInfo == null) continue;
                found.put(info.activityInfo.packageName, info.loadLabel(pm).toString());
            }
        }
        List<Map.Entry<String,String>> entries = new ArrayList<>(found.entrySet());
        entries.sort(Comparator.comparing(e -> e.getValue().toLowerCase(Locale.ROOT)));
        JSONArray result = new JSONArray();
        for (Map.Entry<String,String> entry : entries) {
            try { result.put(new JSONObject().put("id",entry.getKey()).put("name",entry.getValue())); }
            catch (Exception ignored) { }
        }
        return result;
    }

    static String normalize(String component) {
        if (component == null) return "";
        component = component.replace("ComponentInfo{", "").replace("}", "");
        int slash = component.indexOf('/');
        if (slash < 1) return "";
        String pkg = component.substring(0,slash), cls = component.substring(slash+1);
        if (cls.startsWith(".")) cls = pkg + cls;
        return pkg + "/" + cls;
    }

    private void read(XmlPullParser parser, Map<String,String> exact, Map<String,String> fallback) throws Exception {
        int count=0;
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (++count > 300000) throw new IllegalArgumentException("Icon pack is too large");
            if (parser.getEventType() != XmlPullParser.START_TAG || !"item".equals(parser.getName())) continue;
            String component = normalize(parser.getAttributeValue(null,"component"));
            String drawable = parser.getAttributeValue(null,"drawable");
            if (component.isEmpty() || drawable == null || !drawable.matches("[a-zA-Z0-9_]+")) continue;
            exact.put(component,drawable);
            fallback.putIfAbsent(component.substring(0,component.indexOf('/')),drawable);
        }
    }

    synchronized boolean select(String pkg) {
        if (pkg == null) return false;
        if (pkg.isEmpty()) { selected="";resources=null;components.clear();packages.clear();return true; }
        boolean known=false;
        JSONArray available=discover();
        for(int i=0;i<available.length();i++) if(pkg.equals(available.optJSONObject(i).optString("id"))) known=true;
        if(!known) return false;
        try {
            Resources res=context.getPackageManager().getResourcesForApplication(pkg);
            Map<String,String> exact=new HashMap<>(), fallback=new HashMap<>();
            int id=res.getIdentifier("appfilter","xml",pkg);
            if(id!=0) {
                try(XmlResourceParser parser=res.getXml(id)) { read(parser,exact,fallback); }
            } else {
                id=res.getIdentifier("appfilter","raw",pkg);
                try(InputStream in=id!=0?res.openRawResource(id):res.getAssets().open("appfilter.xml")) {
                    XmlPullParser parser=Xml.newPullParser();parser.setInput(in,"UTF-8");read(parser,exact,fallback);
                }
            }
            if(exact.isEmpty()) return false;
            selected=pkg;resources=res;components.clear();components.putAll(exact);packages.clear();packages.putAll(fallback);
            return true;
        } catch(Exception | OutOfMemoryError ignored) { return false; }
    }

    synchronized Drawable icon(String pkg, String activity) {
        if(resources==null || selected.isEmpty()) return null;
        try {
            String name=components.get(normalize(pkg+"/"+activity));
            if(name==null) name=packages.get(pkg);
            if(name==null) return null;
            int id=resources.getIdentifier(name,"drawable",selected);
            if(id==0) id=resources.getIdentifier(name,"mipmap",selected);
            return id==0?null:resources.getDrawable(id,null);
        } catch(Exception | OutOfMemoryError ignored) { return null; }
    }
    Drawable namedIcon(String pkg,String name) {
        if(pkg==null || name==null || !name.matches("[a-zA-Z0-9_]+"))return null;
        try {
            Resources res=context.getPackageManager().getResourcesForApplication(pkg);
            int id=res.getIdentifier(name,"drawable",pkg);
            if(id==0)id=res.getIdentifier(name,"mipmap",pkg);
            return id==0?null:res.getDrawable(id,null);
        }catch(Exception | OutOfMemoryError ignored){return null;}
    }

    List<String> listIcons(String pkg) {
        Set<String> names=new TreeSet<>();
        try {
            Resources res=context.getPackageManager().getResourcesForApplication(pkg);
            int id=res.getIdentifier("drawable","xml",pkg);
            if(id!=0) {
                try(XmlResourceParser parser=res.getXml(id)) {
                    int count=0;
                    while(parser.next()!=XmlPullParser.END_DOCUMENT && ++count<300000) {
                        if(parser.getEventType()==XmlPullParser.START_TAG && "item".equals(parser.getName())) {
                            String name=parser.getAttributeValue(null,"drawable");
                            if(name!=null && name.matches("[a-zA-Z0-9_]+"))names.add(name);
                        }
                    }
                }
            }
            // Include appfilter entries as a fallback and to catch icons absent from drawable.xml.
            Map<String,String> exact=new HashMap<>(),fallback=new HashMap<>();
            id=res.getIdentifier("appfilter","xml",pkg);
            if(id!=0) {try(XmlResourceParser parser=res.getXml(id)){read(parser,exact,fallback);}}
            else {
                id=res.getIdentifier("appfilter","raw",pkg);
                try(InputStream in=id!=0?res.openRawResource(id):res.getAssets().open("appfilter.xml")) {
                    XmlPullParser parser=Xml.newPullParser();parser.setInput(in,"UTF-8");read(parser,exact,fallback);
                }
            }
            names.addAll(exact.values());
        }catch(Exception | OutOfMemoryError ignored){}
        return new ArrayList<>(names);
    }

}
