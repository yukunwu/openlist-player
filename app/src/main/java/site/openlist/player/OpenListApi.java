package site.openlist.player;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class OpenListApi {
    final String server;
    final String token;

    OpenListApi(String server, String token) { this.server = Policy.server(server); this.token = token; }

    JSONObject post(String route, JSONObject body) throws Exception {
        URL url = new URL(server + route);
        if (!Policy.sameOrigin(server, url.toString())) throw new Exception("拒绝跨域 API 请求");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "application/json");
            if (!token.isEmpty()) c.setRequestProperty("Authorization", token);
            c.setDoOutput(true);
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(data.length);
            try (java.io.OutputStream out = c.getOutputStream()) { out.write(data); }
            int status = c.getResponseCode();
            if (status >= 300 && status < 400) throw new Exception("API 地址发生跳转，请填写最终服务器地址");
            if (status < 200 || status >= 300) throw new Exception("服务器 HTTP " + status);
            byte[] bytes;
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > 8 * 1024 * 1024) throw new Exception("目录响应过大，请减少分页大小");
                    out.write(buffer, 0, n);
                }
                bytes = out.toByteArray();
            }
            JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            int code = json.optInt("code", -1);
            if (code != 200) {
                if (code == 401 || code == 403) throw new Exception("没有访问权限或登录已过期，请重新登录（" + code + "）");
                // Do not echo backend messages that may include signed URLs or credentials.
                throw new Exception("OpenList 返回错误 " + code + "，请检查账号、目录权限或存储状态");
            }
            return json;
        } finally { c.disconnect(); }
    }
}
