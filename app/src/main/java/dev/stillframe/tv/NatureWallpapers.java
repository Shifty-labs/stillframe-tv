package dev.stillframe.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Small opt-in photo catalogue. Images stay outside the APK and are hash-checked. */
final class NatureWallpapers {
    private final Activity activity;
    private final Supplier<JSONArray> imports;
    private final BiConsumer<File,JSONObject> install;
    private final Consumer<JSONObject> select;
    private boolean busy;
    NatureWallpapers(Activity activity,Supplier<JSONArray> imports,BiConsumer<File,JSONObject> install,Consumer<JSONObject> select){
        this.activity=activity;this.imports=imports;this.install=install;this.select=select;
    }
    private void message(String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    void choose(){
        if(busy){message("A nature wallpaper is downloading");return;}
        try(InputStream in=activity.getAssets().open("nature-wallpapers.json")){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[4096];int n;
            while((n=in.read(chunk))!=-1)bytes.write(chunk,0,n);
            JSONArray catalogue=new JSONArray(bytes.toString("UTF-8"));String[] labels=new String[catalogue.length()];
            for(int i=0;i<labels.length;i++)labels[i]=catalogue.getJSONObject(i).getString("name");
            new AlertDialog.Builder(activity).setTitle("Nature wallpapers")
                .setItems(labels,(dialog,index)->details(catalogue.optJSONObject(index)))
                .setNegativeButton("Back",null).show();
        }catch(Exception e){message("The nature catalogue could not be opened");}
    }
    private JSONObject existing(String id){
        JSONArray list=imports.get();for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);if(item!=null&&id.equals(item.optString("natureId")))return item;}return null;
    }
    private void details(JSONObject item){
        if(item==null)return;
        JSONObject saved=existing(item.optString("id"));
        String credit=item.optString("name")+" — "+item.optString("author")+"\n"+item.optString("license")+"\n"+item.optString("licenseUrl")+"\n\nSource: "+item.optString("source")+"\n\nImages are resized for the TV and cropped on screen. The image retains its stated licence.\n\n";
        new AlertDialog.Builder(activity).setTitle(item.optString("name"))
            .setMessage(credit+(saved!=null?"Already saved. Use it without downloading again.":"Download about "+((item.optLong("bytes")+1023)/1024)+" KB from StillFrame's GitHub release? It will be saved on this TV for offline use. No Overflight plugin is needed."))
            .setPositiveButton(saved!=null?"Use wallpaper":"Download",(dialog,which)->{
                JSONObject current=existing(item.optString("id"));
                if(current!=null){select.accept(current);return;}
                if(imports.get().length()>=20){message("Gallery full. Remove an imported photo first.");return;}
                download(item);
            }).setNegativeButton("Cancel",null).show();
    }
    private void download(JSONObject item){
        if(busy)return;busy=true;AtomicBoolean cancelled=new AtomicBoolean();
        AlertDialog progress=new AlertDialog.Builder(activity).setTitle("Downloading "+item.optString("name"))
            .setMessage("Saving a photo for offline use…")
            .setNegativeButton("Cancel",(dialog,which)->cancelled.set(true)).create();
        progress.setOnCancelListener(dialog->cancelled.set(true));progress.show();
        new Thread(()->{
            File temp=null;HttpURLConnection connection=null;
            try{
                temp=File.createTempFile("nature-",".jpg",activity.getCacheDir());
                URL url=new URL(item.getString("url"));
                for(int redirect=0;redirect<6;redirect++){
                    String host=url.getHost();
                    if(!"https".equals(url.getProtocol())||!(host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com")))throw new IOException("Unsupported download host");
                    connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(15000);connection.setRequestProperty("User-Agent","StillFrameTV/0.28");
                    int status=connection.getResponseCode();
                    if(status>=300&&status<400){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Invalid redirect");url=new URL(url,location);connection.disconnect();connection=null;continue;}
                    if(status!=200)throw new IOException("Server returned "+status);break;
                }
                if(connection==null||connection.getResponseCode()!=200)throw new IOException("Too many redirects");
                long expected=item.getLong("bytes");if(expected<=0||expected>5000000)throw new IOException("Invalid photo size");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;
                try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(temp)){
                    byte[] chunk=new byte[8192];int n;
                    while((n=in.read(chunk))!=-1){if(cancelled.get())throw new IOException("Cancelled");total+=n;if(total>expected)throw new IOException("Photo too large");digest.update(chunk,0,n);out.write(chunk,0,n);}
                }
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(total!=expected||!hash.toString().equals(item.getString("sha256")))throw new IOException("Photo verification failed");
                if(cancelled.get())throw new IOException("Cancelled");
                File completed=temp;temp=null;
                activity.runOnUiThread(()->{busy=false;progress.dismiss();if(activity.isFinishing()||activity.isDestroyed()||cancelled.get())completed.delete();else install.accept(completed,item);});
            }catch(Exception failure){
                activity.runOnUiThread(()->{busy=false;progress.dismiss();if(!activity.isFinishing()&&!activity.isDestroyed()&&!cancelled.get())message("Download failed. Check your connection and try again. Your wallpapers are unchanged.");});
            }finally{if(connection!=null)connection.disconnect();if(temp!=null)temp.delete();}
        },"StillFrameNatureDownload").start();
    }
}
