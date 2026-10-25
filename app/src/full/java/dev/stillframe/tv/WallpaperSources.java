package dev.stillframe.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.*;
import java.util.concurrent.*;
import tv.projectivy.plugin.wallpaperprovider.api.*;

/** Photos/local files and opt-in API-v1 video providers. Never requires the Projectivy launcher. */
final class WallpaperSources {
    static final int PICK_VIDEO=43;
    private static final String ACTION="tv.projectivy.plugin.WALLPAPER_PROVIDER";
    private final Activity activity;
    private final WebView web;
    private final SharedPreferences prefs;
    private final VideoWallpaper player;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"StillFrameWallpaperProvider");t.setDaemon(true);return t;});
    private final List<Wallpaper> playlist=new ArrayList<>();
    private String source,title="",label="Photos";
    private ServiceConnection connection;
    private boolean active,visible,fetching,destroyed;
    private volatile boolean workerBusy;
    private int generation,index,errors;
    private AlertDialog dialog;
    private Runnable bindTimeout;
    private final Runnable releaseAfterPause=this::releaseBackground;
    private void releaseBackground(){active=false;generation++;player.pause();unbind();}

    WallpaperSources(Activity a,FrameLayout root,WebView web){
        activity=a;this.web=web;prefs=a.getPreferences(Context.MODE_PRIVATE);
        source=prefs.getString("wallpaperSource","photos");
        player=new VideoWallpaper(a,root,show->{visible=show;publish();},this::next,this::failed);
    }
    private void message(String s){Toast.makeText(activity,s,Toast.LENGTH_LONG).show();}
    String state(){
        try{return new JSONObject().put("source",label).put("video",visible).put("title",title).put("videos",playlist.size()).toString();}
        catch(Exception e){return "{}";}
    }
    void publish(){if(!destroyed)web.evaluateJavascript("window.wallpaperMediaState && window.wallpaperMediaState("+state()+")",null);}
    void resume(){
        handler.removeCallbacks(releaseAfterPause);
        // HOME can cause a short pause/new-intent/resume cycle on the same activity.
        if(active){player.resume();publish();return;}
        active=true;
        if(source.equals("local")){
            label="Local video";
            if(!visible)playLocal();
        }else if(!source.equals("photos")){
            ComponentName component=ComponentName.unflattenFromString(source);
            if(component!=null){
                ResolveInfo info=find(component);label=info==null?"Unavailable provider":providerLabel(info);
                if(playlist.isEmpty())load(component);else if(!visible)playCurrent();
            }
        }
        player.resume();publish();
    }
    void home(){if(dialog!=null)dialog.dismiss();}
    void pause(){handler.removeCallbacks(releaseAfterPause);handler.postDelayed(releaseAfterPause,250);}
    void destroy(){destroyed=true;handler.removeCallbacks(releaseAfterPause);active=false;generation++;unbind();player.destroy();worker.shutdownNow();if(dialog!=null)dialog.dismiss();}
    private List<ResolveInfo> providers(){
        List<ResolveInfo> result=new ArrayList<>();
        for(ResolveInfo info:activity.getPackageManager().queryIntentServices(new Intent(ACTION),PackageManager.GET_META_DATA)){
            ServiceInfo s=info.serviceInfo;
            if(s!=null&&s.exported&&s.metaData!=null&&s.metaData.getInt("apiVersion",0)==1&&(s.permission==null||activity.checkSelfPermission(s.permission)==PackageManager.PERMISSION_GRANTED))result.add(info);
        }
        result.sort(Comparator.comparing(i->i.loadLabel(activity.getPackageManager()).toString()));return result;
    }
    private String providerLabel(ResolveInfo info){
        if("tv.projectivy.plugin.wallpaperprovider.overflight".equals(info.serviceInfo.packageName))return "Stillframe: Overflight";
        return info.loadLabel(activity.getPackageManager()).toString();
    }
    private ResolveInfo find(ComponentName component){
        for(ResolveInfo info:providers())if(component.equals(new ComponentName(info.serviceInfo.packageName,info.serviceInfo.name)))return info;
        return null;
    }
    void choose(){
        List<ResolveInfo> providers=providers();
        String[] options=new String[providers.size()+2];options[0]="Photos — wallpaper gallery";options[1]="Local video — choose a file";
        for(int i=0;i<providers.size();i++)options[i+2]=providerLabel(providers.get(i));
        dialog=new AlertDialog.Builder(activity).setTitle("Wallpaper source")
            .setItems(options,(d,n)->{
                if(n==0){photos();return;}
                if(n==1){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);try{activity.startActivityForResult(intent,PICK_VIDEO);}catch(Exception e){message("No video file picker is available");}return;}
                ResolveInfo info=providers.get(n-2);ServiceInfo service=info.serviceInfo;
                dialog=new AlertDialog.Builder(activity).setTitle("Enable video wallpapers?")
                    .setMessage("Streams muted videos from "+options[n]+". Streaming uses internet data. The selected photo stays as a fallback.")
                    .setPositiveButton("Enable",(x,w)->select(new ComponentName(service.packageName,service.name),options[n]))
                    .setNegativeButton("Cancel",null).show();
            }).setNegativeButton("Cancel",null).create();dialog.show();
    }
    void photos(){
        generation++;source="photos";label="Photos";title="";prefs.edit().putString("wallpaperSource",source).apply();playlist.clear();unbind();player.clear();publish();
    }
    boolean result(int request,int result,Intent data){
        if(request!=PICK_VIDEO)return false;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return true;
        Uri uri=data.getData();
        if(!"content".equals(uri.getScheme())){message("Choose a video from device storage or USB");return true;}
        try{
            activity.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            generation++;unbind();playlist.clear();source="local";label="Local video";
            prefs.edit().putString("wallpaperSource",source).putString("wallpaperVideoUri",uri.toString()).apply();playLocal();
        }catch(Exception e){message("Could not keep access to this video. Try a different storage location.");}
        return true;
    }
    private void playLocal(){
        String saved=prefs.getString("wallpaperVideoUri","");if(saved.isEmpty())return;
        title="Local video";player.set(Uri.parse(saved),true);publish();
    }
    private void select(ComponentName component,String name){
        generation++;unbind();player.clear();playlist.clear();source=component.flattenToString();label=name;errors=0;
        prefs.edit().putString("wallpaperSource",source).apply();load(component);publish();
    }
    private void load(ComponentName component){
        if(!active||fetching||workerBusy||destroyed)return;
        if(find(component)==null){message("Wallpaper provider unavailable; showing your photo");return;}
        int request=++generation;fetching=true;
        connection=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                if(request!=generation||!active)return;
                workerBusy=true;
                worker.execute(()->{
                    List<Wallpaper> result=new ArrayList<>();String error=null;
                    try{
                        List<Wallpaper> supplied=IWallpaperProviderService.Stub.asInterface(binder).getWallpapers(new Event());
                        if(supplied!=null)for(Wallpaper w:supplied){
                            if(w==null||w.type!=4||w.uri==null)continue;
                            Uri uri=Uri.parse(w.uri);
                            if("http".equals(uri.getScheme())&&"sylvan.apple.com".equalsIgnoreCase(uri.getHost()))uri=uri.buildUpon().scheme("https").build();
                            if("https".equals(uri.getScheme())||"content".equals(uri.getScheme()))result.add(w.withUri(uri.toString()));
                            if(result.size()>=500)break;
                        }
                    }catch(Exception e){android.util.Log.w("StillFrameWallpaper","Provider request failed",e);error="Could not fetch provider videos; showing your photo";}
                    workerBusy=false;
                    final String failure=error;
                    handler.post(()->{
                        fetching=false;if(request!=generation||!active||destroyed)return;
                        unbind();playlist.clear();playlist.addAll(result);Collections.shuffle(playlist);index=0;
                        if(failure!=null||playlist.isEmpty()){message(failure==null?"No supported HTTPS videos returned. Check the provider settings.":failure);return;}
                        errors=0;playCurrent();message("Loaded "+playlist.size()+" video wallpapers");
                    });
                });
            }
            @Override public void onServiceDisconnected(ComponentName name){if(request==generation){fetching=false;message("Wallpaper provider disconnected; showing your photo");}}
        };
        try{
            if(!activity.bindService(new Intent(ACTION).setComponent(component),connection,Context.BIND_AUTO_CREATE)){fetching=false;unbind();message("Could not open wallpaper provider");return;}
            bindTimeout=()->{if(request==generation&&active){generation++;unbind();message("Wallpaper provider timed out; showing your photo");}};
            handler.postDelayed(bindTimeout,30000);
        }catch(Exception e){fetching=false;unbind();message("Could not bind wallpaper provider");}
    }
    private void unbind(){
        fetching=false;
        if(bindTimeout!=null){handler.removeCallbacks(bindTimeout);bindTimeout=null;}
        if(connection!=null){try{activity.unbindService(connection);}catch(Exception ignored){}connection=null;}
    }
    void next(){
        if(!active)return;
        if(source.equals("local")){playLocal();return;}
        if(playlist.isEmpty()){message("Select a video wallpaper source first");return;}
        index=(index+1)%playlist.size();errors=0;playCurrent();
    }
    private void playCurrent(){
        if(!active||playlist.isEmpty())return;
        Wallpaper wallpaper=playlist.get(index);title=wallpaper.title==null?"Aerial video":wallpaper.title;
        player.set(Uri.parse(wallpaper.uri),playlist.size()==1);publish();
    }
    private void failed(){
        if(!active||destroyed)return;
        if(!playlist.isEmpty()&&++errors<3){index=(index+1)%playlist.size();handler.postDelayed(()->{if(active&&!source.equals("photos"))playCurrent();},800);}
        else message("Video unavailable; showing your selected photo");
    }
    void providerSettings(){
        ComponentName component=ComponentName.unflattenFromString(source);
        ResolveInfo info=component==null?null:find(component);
        if(info==null){message("Choose an installed wallpaper provider first");return;}
        String name=info.serviceInfo.metaData.getString("settingsActivity","");
        if(name.startsWith("."))name=info.serviceInfo.packageName+name;
        try{activity.startActivity(new Intent().setComponent(new ComponentName(info.serviceInfo.packageName,name)));playlist.clear();}
        catch(Exception e){message("Provider settings could not be opened");}
    }
}
