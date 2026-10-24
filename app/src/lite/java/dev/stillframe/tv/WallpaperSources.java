package dev.stillframe.tv;
import android.app.Activity;
import android.content.Intent;
import android.webkit.WebView;
import android.widget.FrameLayout;
/** Photo-only implementation; no player, provider API or media networking in Lite. */
final class WallpaperSources {
    private final WebView web;
    WallpaperSources(Activity a,FrameLayout root,WebView web){this.web=web;}
    void resume(){publish();} void pause(){} void destroy(){} void home(){}
    void publish(){web.evaluateJavascript("window.wallpaperMediaState && window.wallpaperMediaState({source:'Photos',video:false})",null);}
    void photos(){publish();} void choose(){} void next(){} void providerSettings(){}
    boolean result(int request,int result,Intent data){return false;}
}
