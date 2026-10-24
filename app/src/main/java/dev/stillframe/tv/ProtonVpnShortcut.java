package dev.stillframe.tv;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Delegates connection to Proton's own widget action using Android's widget-host API. */
final class ProtonVpnShortcut {
    private static final String PROTON_PACKAGE="ch.protonvpn.android";
    private String selectedPackage=PROTON_PACKAGE;
    private ComponentName provider=new ComponentName(PROTON_PACKAGE,"com.protonvpn.android.widget.ProtonVpnWidgetReceiver");
    private final Activity activity;
    private final SharedPreferences prefs;
    private final FrameLayout root;
    private final AppWidgetManager manager;
    private final AppWidgetHost host;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final java.util.function.Consumer<String> requested;
    private AppWidgetHostView widget;
    private long deadline;
    private long lastRequest;
    private boolean pending;
    private boolean wantDisconnect;
    private boolean destroyed;
    private int widgetId;
    private final Runnable retry=this::attempt;

    ProtonVpnShortcut(Activity a,FrameLayout root,java.util.function.Consumer<String> result) {
        activity=a;this.root=root;requested=result;
        prefs=a.getPreferences(Context.MODE_PRIVATE);manager=AppWidgetManager.getInstance(a);
        String saved=prefs.getString("vpnWidgetProvider",provider.flattenToString());
        ComponentName configured=ComponentName.unflattenFromString(saved);
        if(configured!=null){provider=configured;selectedPackage=provider.getPackageName();}
        widgetId=prefs.getInt("vpnWidgetId",prefs.getInt("protonWidgetId",-1));
        host=new AppWidgetHost(a,21334){
            @Override protected AppWidgetHostView onCreateView(Context c,int id,AppWidgetProviderInfo info){
                return new AppWidgetHostView(c){
                    @Override public void updateAppWidget(RemoteViews views){
                        super.updateAppWidget(views);
                        if(pending){handler.removeCallbacks(retry);handler.postDelayed(retry,150);}
                    }
                };
            }
        };
        if(widgetId!=-1 && manager.getAppWidgetInfo(widgetId)!=null)attach();
    }

    private boolean isProton(){return PROTON_PACKAGE.equals(selectedPackage);}
    String providerState(){
        try{return new org.json.JSONObject().put("name",VpnWidgetSupport.label(activity,selectedPackage)).put("proton",isProton()).toString();}
        catch(Exception e){return "{}";}
    }
    void chooseProvider(){
        List<AppWidgetProviderInfo> options=VpnWidgetSupport.providers(activity);
        if(options.isEmpty()){
            new android.app.AlertDialog.Builder(activity).setTitle("VPN controls").setMessage("No VPN connection widgets were found. Install a VPN app that provides an Android widget. Proton VPN is the tested integration; other widgets are experimental.").setPositiveButton("OK",null).show();return;
        }
        String[] labels=new String[options.size()];
        for(int i=0;i<options.size();i++){
            AppWidgetProviderInfo option=options.get(i);
            labels[i]=VpnWidgetSupport.label(activity,option.provider.getPackageName())+" — "+option.loadLabel(activity.getPackageManager());
        }
        new android.app.AlertDialog.Builder(activity).setTitle("Choose VPN control widget")
            .setItems(labels,(dialog,index)->{
                AppWidgetProviderInfo option=options.get(index);
                new android.app.AlertDialog.Builder(activity).setTitle(labels[index])
                    .setMessage("Use this app's widget for the Home VPN button? Proton supports Fastest. Other widgets use their app's default connection and must expose one clear Connect and Disconnect control. Changing this selection does not connect or disconnect a VPN.")
                    .setPositiveButton("Use widget",(d,w)->{
                        pending=false;handler.removeCallbacks(retry);
                        if(widget!=null){root.removeView(widget);widget=null;}
                        if(widgetId!=-1)try{host.deleteAppWidgetId(widgetId);}catch(Exception ignored){}
                        widgetId=-1;provider=option.provider;selectedPackage=provider.getPackageName();
                        prefs.edit().putString("vpnWidgetProvider",provider.flattenToString()).putInt("vpnWidgetId",-1).remove("protonWidgetId").apply();
                        requested.accept("provider_changed");message("VPN control widget selected");
                    }).setNegativeButton("Cancel",null).show();
            }).setNegativeButton("Cancel",null).show();
    }

    void resume(){try{host.startListening();}catch(Exception ignored){}}
    void pause(){try{host.stopListening();}catch(Exception ignored){}}
    void destroy(){destroyed=true;pending=false;handler.removeCallbacksAndMessages(null);pause();}
    private void message(String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}

    private boolean hasVpn(){
        ConnectivityManager cm=activity.getSystemService(ConnectivityManager.class);
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities c=cm.getNetworkCapabilities(n);
            if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return true;
        }
        return false;
    }

    private void attach(){
        AppWidgetProviderInfo info=manager.getAppWidgetInfo(widgetId);
        if(info==null || !provider.equals(info.provider))return;
        if(widget!=null)root.removeView(widget);
        widget=host.createView(activity,widgetId,info);
        // Keep Proton's RemoteViews and its delegated action alive without putting a second widget on Home.
        widget.setVisibility(View.INVISIBLE);
        root.addView(widget,new FrameLayout.LayoutParams(1,1));
        widget.updateAppWidgetSize(new Bundle(),240,160,240,160);
    }

    void toggle(){
        if(destroyed||pending)return;
        if(SystemClock.elapsedRealtime()-lastRequest<3000)return;
        try{
            wantDisconnect=hasVpn();
            if(widget==null){
                if(widgetId==-1){widgetId=host.allocateAppWidgetId();prefs.edit().putInt("vpnWidgetId",widgetId).apply();}
                if(manager.getAppWidgetInfo(widgetId)==null && !manager.bindAppWidgetIdIfAllowed(widgetId,provider)){
                    android.content.Intent approve=new android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,provider);
                    if(approve.resolveActivity(activity.getPackageManager())!=null)activity.startActivity(approve);
                    else new android.app.AlertDialog.Builder(activity).setTitle("One-time VPN control setup")
                        .setMessage("This TV needs ADB approval to host the selected VPN widget:\n\nadb shell appwidget grantbind --package "+activity.getPackageName()+" --user 0\n\nAfter setup, press the VPN button again.")
                        .setPositiveButton("OK",null).show();
                    requested.accept("error");return;
                }
                attach();
            }
            if(widget==null){message("The selected VPN widget is unavailable");requested.accept("error");return;}
            resume();pending=true;deadline=SystemClock.elapsedRealtime()+15000;
            message(wantDisconnect?"Requesting VPN disconnect…":"Preparing VPN connection…");handler.postDelayed(retry,250);
        }catch(Exception e){pending=false;message("Could not prepare the VPN shortcut");requested.accept("error");}
    }

    private void collect(View v,List<TextView> texts){
        if(v instanceof TextView)texts.add((TextView)v);
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),texts);}
    }
    private String protonString(Resources r,String name){
        int id=r.getIdentifier(name,"string",selectedPackage);return id==0?"":r.getString(id);
    }
    private void attempt(){
        if(!pending||destroyed)return;
        try{
            List<TextView> texts=new ArrayList<>();collect(widget,texts);
            Resources res=activity.getPackageManager().getResourcesForApplication(selectedPackage);
            String connect=protonString(res,wantDisconnect?"disconnect":"connect");
            String fastest=protonString(res,"fastest_country"),free=protonString(res,"fastest_free_server");
            String excluding=protonString(res,"fastest_country_excluding_my_country");
            TextView connectText=null;boolean isFastest=false;
            for(TextView t:texts){
                String text=t.getText().toString().trim();
                if(!connect.isEmpty()&&text.equals(connect))connectText=t;
                if((!fastest.isEmpty()&&text.equals(fastest))||(!free.isEmpty()&&text.equals(free))||(!excluding.isEmpty()&&text.equals(excluding)))isFastest=true;
            }
            View matched=VpnWidgetSupport.uniqueButton(res,selectedPackage,texts,wantDisconnect,widget);
            if(matched!=null && (wantDisconnect||!isProton()||isFastest)){
                View target=matched;
                while(!target.isClickable() && target.getParent() instanceof View && target!=widget)target=(View)target.getParent();
                pending=false;
                if(target!=widget && target.isClickable() && target.performClick()){
                    lastRequest=SystemClock.elapsedRealtime();requested.accept(wantDisconnect?"disconnect":"connect");return;
                }
                message("The VPN widget button is unavailable");requested.accept("error");return;
            }
            if(SystemClock.elapsedRealtime()<deadline){handler.postDelayed(retry,500);return;}
            pending=false;requested.accept("error");
            message(wantDisconnect?"The selected VPN widget did not offer an unambiguous Disconnect action. Check the active VPN in its app.":isProton()&&connectText!=null?"Set Proton's Quick Connect default to Fastest, then try again":"No unambiguous VPN widget action was found. Open the VPN app to check sign-in and permissions, or choose another widget.");
        }catch(Exception e){pending=false;requested.accept("error");message("The VPN widget could not provide an action");}
    }
}
