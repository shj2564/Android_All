package kr.song.peugeot.fmbridge;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

final class PairClient {
    static void pair(Context c,String server,String code) throws Exception{
        HttpsURLConnection h=PinnedHttps.open(server+"/v1/pair");
        h.setConnectTimeout(5000); h.setReadTimeout(5000); h.setRequestMethod("POST"); h.setDoOutput(true);
        h.setRequestProperty("Content-Type","application/json; charset=utf-8");
        byte[] data=new JSONObject().put("role","headunit").put("code",code).toString().getBytes(StandardCharsets.UTF_8);
        h.setFixedLengthStreamingMode(data.length); try(OutputStream o=h.getOutputStream()){o.write(data);}
        int rc=h.getResponseCode(); InputStream in=rc>=200&&rc<300?h.getInputStream():h.getErrorStream();
        String text=read(in); if(rc<200||rc>=300)throw new IOException("HTTP "+rc+" "+text);
        String token=new JSONObject(text).getString("token"); ConfigStore.save(c,server,token);
    }
    static String read(InputStream in)throws IOException{
        if(in==null)return "{}"; ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] x=new byte[1024]; int n;
        while((n=in.read(x))>0)b.write(x,0,n); return new String(b.toByteArray(),StandardCharsets.UTF_8);
    }
    private PairClient(){}
}
