package kr.song.peugeot.fm;

import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;

/**
 * Private Wi-Fi local endpoint only. Refuses public Internet/VPS hosts.
 * The class name is retained only to minimize source changes from older drafts.
 */
final class PinnedHttps {
    static HttpURLConnection open(String address) throws Exception {
        URL url=new URL(address);
        if(!"http".equalsIgnoreCase(url.getProtocol()))
            throw new IllegalArgumentException("로컬 http:// 주소만 지원");
        InetAddress addr=InetAddress.getByName(url.getHost());
        byte[] ip=addr.getAddress();
        if(!(addr instanceof Inet4Address))
            throw new IllegalArgumentException("현재는 로컬 IPv4 주소만 지원");
        int a=ip[0]&255,b=ip[1]&255;
        boolean privateIp=a==10||(a==172&&b>=16&&b<=31)||
                          (a==192&&b==168)||(a==169&&b==254);
        if(!privateIp)throw new IllegalArgumentException("공용 IP/VPS 연결은 차단됨");
        if(url.getPort()!=38765)
            throw new IllegalArgumentException("CMZX FM 포트 38765만 허용");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setUseCaches(false);
        return c;
    }
    private PinnedHttps(){}
}
