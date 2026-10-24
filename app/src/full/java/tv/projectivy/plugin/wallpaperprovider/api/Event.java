// API v1 TimeElapsed event. Apache-2.0 API compatibility; see THIRD-PARTY-WALLPAPER-API.txt.
package tv.projectivy.plugin.wallpaperprovider.api;
import android.os.Parcel;
import android.os.Parcelable;
public final class Event implements Parcelable {
    public Event(){}
    public void writeToParcel(Parcel p,int flags){p.writeInt(1);}
    public int describeContents(){return 0;}
    public static final Creator<Event> CREATOR=new Creator<Event>(){
        public Event createFromParcel(Parcel p){if(p.readInt()!=1)throw new IllegalArgumentException("Unsupported event");return new Event();}
        public Event[] newArray(int n){return new Event[n];}
    };
}
