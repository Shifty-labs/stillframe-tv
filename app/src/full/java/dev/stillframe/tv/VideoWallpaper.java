package dev.stillframe.tv;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.function.Consumer;

/** A single muted video decoder, released whenever Home is not in the foreground. */
final class VideoWallpaper implements TextureView.SurfaceTextureListener {
    private final Activity activity;
    private final TextureView view;
    private final ImageView poster;
    private Bitmap lastFrame;
    private boolean renderedFrame;
    private int resumePosition;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Consumer<Boolean> visibility;
    private final Runnable completed;
    private final Runnable failed;
    private MediaPlayer player;
    private Surface surface;
    private Uri uri;
    private boolean foreground;
    private boolean loop=true;
    private int width,height;
    private final Runnable timeout=()->error();

    VideoWallpaper(Activity a,FrameLayout root,Consumer<Boolean> visibility,Runnable completed,Runnable failed){
        activity=a;this.visibility=visibility;this.completed=completed;this.failed=failed;
        view=new TextureView(a);view.setOpaque(false);view.setSurfaceTextureListener(this);
        root.addView(view,0,new FrameLayout.LayoutParams(-1,-1));
        poster=new ImageView(a);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setVisibility(View.GONE);
        poster.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(poster,1,new FrameLayout.LayoutParams(-1,-1));
    }
    void set(Uri uri,boolean loop){release(true);this.uri=uri;this.loop=loop;resumePosition=0;start();}
    void clear(){uri=null;resumePosition=0;release(false);}
    void resume(){foreground=true;start();}
    void pause(){foreground=false;release(true);}
    void destroy(){foreground=false;uri=null;release(false);handler.removeCallbacksAndMessages(null);}
    private void start(){
        if(!foreground||uri==null||!view.isAvailable()||player!=null)return;
        try{
            MediaPlayer p=new MediaPlayer();player=p;
            surface=new Surface(view.getSurfaceTexture());p.setSurface(surface);
            p.setVolume(0f,0f);p.setLooping(loop);
            p.setOnVideoSizeChangedListener((mp,w,h)->{if(player==p){width=w;height=h;crop();}});
            p.setOnPreparedListener(mp->{if(player==p&&foreground){
                if(resumePosition>0){mp.setOnSeekCompleteListener(seeked->{if(player==p&&foreground)seeked.start();});mp.seekTo(resumePosition);}
                else mp.start();
            }});
            p.setOnInfoListener((mp,what,extra)->{
                if(player==p&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){handler.removeCallbacks(timeout);android.util.Log.i("StillFrameWallpaper","Video rendering "+width+"x"+height);renderedFrame=true;clearPoster();visibility.accept(true);}
                return false;
            });
            p.setOnCompletionListener(mp->{if(player==p&&!loop)completed.run();});
            p.setOnErrorListener((mp,what,extra)->{if(player==p){android.util.Log.w("StillFrameWallpaper","Video decoder error "+what+"/"+extra);error();}return true;});
            p.setDataSource(activity,uri);p.prepareAsync();handler.postDelayed(timeout,25000);
        }catch(Exception e){android.util.Log.w("StillFrameWallpaper","Video setup failed",e);error();}
    }
    private void error(){release(false);failed.run();}
    private void clearPoster(){
        poster.setImageDrawable(null);poster.setVisibility(View.GONE);
        if(lastFrame!=null){lastFrame.recycle();lastFrame=null;}
    }
    private void holdLastFrame(){
        if(!renderedFrame||!view.isAvailable()||view.getWidth()<=0||view.getHeight()<=0)return;
        try{
            float scale=Math.min(1f,Math.min(960f/view.getWidth(),540f/view.getHeight()));
            Bitmap image=view.getBitmap(Math.max(1,Math.round(view.getWidth()*scale)),Math.max(1,Math.round(view.getHeight()*scale)));
            if(image!=null){clearPoster();lastFrame=image;poster.setImageBitmap(image);poster.setVisibility(View.VISIBLE);android.util.Log.i("StillFrameWallpaper","Holding last video frame");}
        }catch(RuntimeException|OutOfMemoryError unavailable){android.util.Log.w("StillFrameWallpaper","Could not retain video frame");}
    }
    private void release(boolean keepFrame){
        handler.removeCallbacks(timeout);
        if(keepFrame)holdLastFrame();else clearPoster();
        if(player!=null&&renderedFrame)try{resumePosition=Math.max(0,player.getCurrentPosition());}catch(IllegalStateException ignored){}
        renderedFrame=false;visibility.accept(keepFrame&&lastFrame!=null);
        if(player!=null){android.util.Log.i("StillFrameWallpaper","Video player released");MediaPlayer old=player;player=null;old.setOnErrorListener(null);old.setOnCompletionListener(null);old.setOnInfoListener(null);old.setOnPreparedListener(null);old.setOnSeekCompleteListener(null);old.release();}
        if(surface!=null){surface.release();surface=null;}
    }
    private void crop(){
        if(width<=0||height<=0||view.getWidth()==0||view.getHeight()==0)return;
        float ratio=(float)width/height,screen=(float)view.getWidth()/view.getHeight();
        Matrix matrix=new Matrix();matrix.setScale(ratio>screen?ratio/screen:1,ratio<screen?screen/ratio:1,view.getWidth()/2f,view.getHeight()/2f);view.setTransform(matrix);
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){start();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){crop();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){release(true);return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture t){}
}
