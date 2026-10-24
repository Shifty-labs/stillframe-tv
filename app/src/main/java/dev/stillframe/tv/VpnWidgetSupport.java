package dev.stillframe.tv;

import android.appwidget.AppWidgetProviderInfo;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.view.View;
import android.widget.TextView;
import java.util.*;

/** Discovers widget controls belonging to apps declaring an Android VPN service. */
final class VpnWidgetSupport {
    static List<AppWidgetProviderInfo> providers(Context context){
        Set<String> packages=new HashSet<>();
        for(ResolveInfo info:context.getPackageManager().queryIntentServices(new Intent("android.net.VpnService"),0)){
            if(info.serviceInfo!=null)packages.add(info.serviceInfo.packageName);
        }
        packages.add("ch.protonvpn.android");
        List<AppWidgetProviderInfo> result=new ArrayList<>();
        for(AppWidgetProviderInfo info:AppWidgetManager.getInstance(context).getInstalledProviders()){
            if(packages.contains(info.provider.getPackageName()))result.add(info);
        }
        result.sort(Comparator.comparing(i->i.loadLabel(context.getPackageManager())));
        return result;
    }
    static String label(Context context,String pkg){
        try{return context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(pkg,0)).toString();}
        catch(Exception ignored){return "ch.protonvpn.android".equals(pkg)?"Proton VPN":pkg;}
    }
    static View uniqueButton(Resources resources,String pkg,List<TextView> texts,boolean disconnect,View boundary){
        Set<String> labels=new HashSet<>();
        String[] names=disconnect?new String[]{"disconnect","action_disconnect","button_disconnect","disconnect_vpn"}:new String[]{"connect","quick_connect","quickConnect","action_connect","button_connect","connect_vpn"};
        for(String name:names){int id=resources.getIdentifier(name,"string",pkg);if(id!=0)try{labels.add(resources.getString(id).trim());}catch(Exception ignored){}}
        labels.add(disconnect?"Disconnect":"Connect");
        if(!disconnect)labels.add("Quick connect");
        Set<View> targets=Collections.newSetFromMap(new IdentityHashMap<>());
        for(TextView text:texts){
            if(!labels.contains(text.getText().toString().trim()))continue;
            View target=text;
            while(!target.isClickable()&&target.getParent() instanceof View&&target!=boundary)target=(View)target.getParent();
            if(target!=boundary&&target.isClickable()&&target.isEnabled())targets.add(target);
        }
        return targets.size()==1?targets.iterator().next():null;
    }
}
