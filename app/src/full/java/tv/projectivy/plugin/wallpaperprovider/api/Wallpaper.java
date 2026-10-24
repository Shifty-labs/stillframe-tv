// Wire-compatible Java implementation of Projectivy Wallpaper Provider API v1.
// Based on spocky/projectivy-plugin-wallpaper-provider, Apache-2.0. See THIRD-PARTY-WALLPAPER-API.txt.
package tv.projectivy.plugin.wallpaperprovider.api;
import android.os.Parcel;
import android.os.Parcelable;
public final class Wallpaper implements Parcelable {
    public final String uri,title,source,author,actionUri;
    public final int type,displayMode;
    private Wallpaper(Parcel p){uri=p.readString();type=p.readInt();displayMode=p.readInt();title=p.readString();source=p.readString();author=p.readString();actionUri=p.readString();}
    private Wallpaper(String uri,Wallpaper old){this.uri=uri;type=old.type;displayMode=old.displayMode;title=old.title;source=old.source;author=old.author;actionUri=old.actionUri;}
    public Wallpaper withUri(String uri){return new Wallpaper(uri,this);}
    public void writeToParcel(Parcel p,int flags){p.writeString(uri);p.writeInt(type);p.writeInt(displayMode);p.writeString(title);p.writeString(source);p.writeString(author);p.writeString(actionUri);}
    public int describeContents(){return 0;}
    public static final Creator<Wallpaper> CREATOR=new Creator<Wallpaper>(){
        public Wallpaper createFromParcel(Parcel p){return new Wallpaper(p);}
        public Wallpaper[] newArray(int n){return new Wallpaper[n];}
    };
}
