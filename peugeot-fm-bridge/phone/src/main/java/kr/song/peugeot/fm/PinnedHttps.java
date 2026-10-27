package kr.song.peugeot.fm;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;

final class PinnedHttps {
    /**
     * Opens ONLY a private IPv4 FM endpoint via Wi-Fi when possible.
     * Cellular may remain the phone's default route during wireless Android Auto.
     */
    static HttpURLConnection open(Context ctx,String address)throws Exception {
        URL url=new URL(address);
        if(!"http".equalsIgnoreCase(url.getProtocol()))
            throw new IllegalArgumentException("로컬 http:// 주소만 지원");
        InetAddress addr=InetAddress.getByName(url.getHost());
        byte[] ip=addr.getAddress();
        if(!(addr instanceof Inet4Address))
            throw new IllegalArgumentException("현재는 로컬 IPv4 주소만 지원");
        int a=ip[0]&255,b=ip[1]&255;
        boolean local=a==10||(a==172&&b>=16&&b<=31)||
                      (a==192&&b==168)||(a==169&&b==254);
        if(!local)throw new IllegalArgumentException("공용 IP/VPS 연결 차단");
        if(url.getPort()!=38765)
            throw new IllegalArgumentException("CMZX FM 포트 38765만 허용");
        ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm!=null) {
            for(Network n:cm.getAllNetworks()) {
                try {
                    NetworkCapabilities cap=cm.getNetworkCapabilities(n);
                    if(cap!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return (HttpURLConnection)n.openConnection(url);
                    }
                }catch(Exception ignored){}
            }
        }
        return (HttpURLConnection)url.openConnection();
    }
    private PinnedHttps(){}
}
