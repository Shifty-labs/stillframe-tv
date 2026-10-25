package dev.stillframe.tv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/** Read-only local connection details; never scans networks or requests location. */
final class NetworkInfoPanel {
    private static void row(JSONArray rows,String label,String value)throws Exception{
        rows.put(new JSONObject().put("label",label).put("value",value==null||value.isEmpty()?"Unavailable":value));
    }
    static String snapshot(Context context,String requested){
        JSONObject result=new JSONObject();JSONArray rows=new JSONArray();
        try{
            ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
            Network chosen=null;NetworkCapabilities caps=null;
            int type="wifi".equals(requested)?NetworkCapabilities.TRANSPORT_WIFI:NetworkCapabilities.TRANSPORT_ETHERNET;
            for(Network n:cm.getAllNetworks()){
                NetworkCapabilities c=cm.getNetworkCapabilities(n);
                if(c!=null&&!c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&c.hasTransport(type)){chosen=n;caps=c;break;}
            }
            if(chosen==null)for(Network n:cm.getAllNetworks()){
                NetworkCapabilities c=cm.getNetworkCapabilities(n);
                if(c!=null&&!c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))){chosen=n;caps=c;break;}
            }
            boolean wifi=caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            result.put("title",chosen==null?"Connection":wifi?"Wi-Fi connection":"Ethernet connection");
            result.put("hint","Local connection details · No speed test or network changes");
            row(rows,"Connection",chosen==null?"Not connected":wifi?"Wi-Fi":"Ethernet");
            if(chosen!=null){
                row(rows,"Android internet check",caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?"Passed":"Not verified");
                LinkProperties props=cm.getLinkProperties(chosen);
                if(props!=null){
                    row(rows,"Network interface",props.getInterfaceName());List<String> ip=new ArrayList<>(),dns=new ArrayList<>();
                    for(LinkAddress address:props.getLinkAddresses())if(!address.getAddress().isLinkLocalAddress())ip.add(address.getAddress().getHostAddress());
                    for(InetAddress address:props.getDnsServers())dns.add(address.getHostAddress());
                    row(rows,"IP address",String.join("\n",ip));row(rows,"DNS servers",String.join("\n",dns));
                }
                if(wifi){
                    try{
                        WifiManager manager=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                        WifiInfo info=manager==null?null:manager.getConnectionInfo();
                        if(info!=null){
                            String ssid=info.getSSID();
                            if(ssid==null||ssid.equals("<unknown ssid>")||ssid.equals("0x"))ssid="Hidden by Android privacy settings";
                            else if(ssid.startsWith("\"")&&ssid.endsWith("\""))ssid=ssid.substring(1,ssid.length()-1);
                            row(rows,"Wi-Fi network",ssid);
                            if(info.getRssi()>-127&&info.getRssi()<0)row(rows,"Signal strength",new String[]{"Very weak","Weak","Fair","Good","Excellent"}[WifiManager.calculateSignalLevel(info.getRssi(),5)]+" · "+info.getRssi()+" dBm");
                            if(info.getLinkSpeed()>0)row(rows,"Wi-Fi link speed",info.getLinkSpeed()+" Mbps · local link, not download speed");
                            if(info.getFrequency()>0)row(rows,"Frequency",info.getFrequency()+" MHz");
                        }
                    }catch(SecurityException ignored){row(rows,"Wi-Fi details","Open Android network settings for more information");}
                }
            }
            boolean vpn=false;for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);vpn|=c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_VPN);}
            row(rows,"VPN",vpn?"Android reports a VPN connected":"No VPN connection reported");
            result.put("rows",rows);
        }catch(Exception e){try{result.put("title","Connection");row(rows,"Details","Unavailable · open Android network settings");result.put("rows",rows);}catch(Exception ignored){}}
        return result.toString();
    }
}
