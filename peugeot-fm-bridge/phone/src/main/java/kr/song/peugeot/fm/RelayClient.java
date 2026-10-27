package kr.song.peugeot.fm;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

final class RelayClient {
    static String pair(Context c, String server, String code) throws Exception {
        JSONObject body = new JSONObject().put("role","phone").put("code",code);
        JSONObject out = request(server + "/v1/pair", "", body);
        String token = out.getString("token");
        ConfigStore.save(c, server, token);
        return token;
    }

    static boolean command(Context c, String action, Integer freq) {
        try {
            JSONObject body = new JSONObject().put("action", action);
            if (freq != null) body.put("freq", freq);
            request(ConfigStore.server(c) + "/v1/fm/command", ConfigStore.token(c), body);
            return true;
        } catch (Exception e) { return false; }
    }

    private static JSONObject request(String url, String token, JSONObject body) throws Exception {
        HttpsURLConnection h = PinnedHttps.open(url);
        h.setConnectTimeout(5000); h.setReadTimeout(5000); h.setRequestMethod("POST");
        h.setDoOutput(true); h.setRequestProperty("Content-Type","application/json; charset=utf-8");
        if (token != null && !token.isEmpty()) h.setRequestProperty("X-FM-Token", token);
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        h.setFixedLengthStreamingMode(data.length);
        try (OutputStream o = h.getOutputStream()) { o.write(data); }
        int code = h.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? h.getInputStream() : h.getErrorStream();
        String text = read(in);
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " " + text);
        return new JSONObject(text);
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "{}";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[1024]; int n;
        while ((n=in.read(buf)) > 0) b.write(buf,0,n);
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }
    private RelayClient() {}
}
