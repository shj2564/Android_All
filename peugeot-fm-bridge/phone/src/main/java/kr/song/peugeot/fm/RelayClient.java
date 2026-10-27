package kr.song.peugeot.fm;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class RelayClient {
    static String normalize(String host) {
        String h=host.trim();
        if(h.startsWith("http://")) h=h.substring(7);
        if(h.startsWith("https://"))throw new IllegalArgumentException("VPS/https 주소는 사용하지 않습니다");
        if(h.indexOf('/')>=0)h=h.substring(0,h.indexOf('/'));
        if(!h.contains(":"))h=h+":38765";
        return "http://"+h;
    }
    static String pair(Context c,String enteredHost,String code)throws Exception{
        String server=normalize(enteredHost);
        JSONObject out=post(server+"/v1/pair","",new JSONObject().put("pin",code));
        String token=out.getString("token");
        ConfigStore.save(c,server,token);
        return token;
    }
    static String health(String enteredHost)throws Exception{
        String s=normalize(enteredHost);
        HttpURLConnection h=PinnedHttps.open(s+"/health");
        h.setConnectTimeout(2000);h.setReadTimeout(3000);h.setRequestMethod("GET");
        int rc=h.getResponseCode();
        String reply=read(rc<300?h.getInputStream():h.getErrorStream());
        if(rc!=200)throw new IOException("HTTP "+rc+" "+reply);
        return reply;
    }
    static JSONObject status(Context c)throws Exception {
        HttpURLConnection h=PinnedHttps.open(ConfigStore.server(c)+"/v1/fm/status");
        h.setConnectTimeout(3000);h.setReadTimeout(4000);h.setRequestMethod("GET");
        h.setRequestProperty("X-FM-Token",ConfigStore.token(c));
        int code=h.getResponseCode();
        String text=read(code<300?h.getInputStream():h.getErrorStream());
        if(code!=200)throw new IOException("HTTP "+code+" "+text);
        return new JSONObject(text);
    }
    static JSONObject command(Context c,String action,Integer freq)throws Exception {
        if(!ConfigStore.paired(c))throw new IllegalStateException("먼저 페어링 필요");
        JSONObject body=new JSONObject().put("action",action);
        if(freq!=null)body.put("freq",freq);
        return post(ConfigStore.server(c)+"/v1/fm/command",ConfigStore.token(c),body);
    }
    private static JSONObject post(String path,String token,JSONObject body)throws Exception{
        HttpURLConnection h=PinnedHttps.open(path);
        h.setConnectTimeout(3000);h.setReadTimeout(7000);h.setRequestMethod("POST");
        h.setDoOutput(true);h.setRequestProperty("Content-Type","application/json");
        if(!token.isEmpty())h.setRequestProperty("X-FM-Token",token);
        byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);
        h.setFixedLengthStreamingMode(data.length);
        try(OutputStream o=h.getOutputStream()){o.write(data);}
        int code=h.getResponseCode();
        String text=read(code<300?h.getInputStream():h.getErrorStream());
        if(code<200||code>=300)throw new IOException("HTTP "+code+": "+text);
        JSONObject reply=new JSONObject(text);
        if(!reply.optBoolean("ok"))throw new IOException(text);
        return reply;
    }
    private static String read(InputStream in)throws IOException{
        if(in==null)return "{}";
        try(InputStream src=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){
            byte[] buffer=new byte[1024];int n;
            while((n=src.read(buffer))>0){b.write(buffer,0,n);
                if(b.size()>4096)throw new IOException("response too large");}
            return new String(b.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private RelayClient(){}
}
