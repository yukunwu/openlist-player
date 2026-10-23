package site.openlist.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PairingActivity extends Activity {
    private PairingServer server;
    private ImageView qr;
    private TextView info;
    private boolean delivered;
    private int generation;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout root = new LinearLayout(this);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(36), dp(24), dp(36), dp(24)); root.setBackgroundColor(TvStyle.INK);
        setContentView(root);
        qr = new ImageView(this);
        qr.setBackgroundColor(Color.WHITE);
        root.addView(qr, new LinearLayout.LayoutParams(dp(240), dp(240)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL); text.setPadding(dp(32), 0, 0, 0);
        root.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        text.addView(TvStyle.text(this, "手机扫码配置", 30, TvStyle.TEXT));
        info = TvStyle.text(this, "正在生成一次性二维码…", 16, TvStyle.MUTED);
        info.setPadding(0, dp(18), 0, dp(20)); text.addView(info);
        text.addView(TvStyle.button(this, "重新生成", v -> begin()));
        text.addView(TvStyle.button(this, "返回手动输入", v -> finish()));
        text.addView(TvStyle.text(this, "仅在可信局域网使用；HTTP 配对页面不防中间人篡改。\n二维码是临时密钥，请勿拍照分享。", 12, TvStyle.MUTED));
        begin();
    }

    private int dp(int n) { return TvStyle.dp(this, n); }

    private byte[] asset(String file) throws Exception {
        try (InputStream in = getAssets().open(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            return out.toByteArray();
        }
    }

    private void begin() {
        stop(); delivered = false;
        int request = ++generation;
        try {
            InetAddress address = address();
            if (address == null) throw new Exception("未找到局域网 IPv4 地址，请连接 Wi-Fi 或有线网络后重试。");
            server = new PairingServer(address, asset("pairing.html"), asset("forge.min.js"),
                    text -> runOnUiThread(() -> { if (request == generation) receive(text); }),
                    () -> runOnUiThread(() -> { if (request == generation && !isDestroyed() && !delivered) { qr.setImageDrawable(null); info.setText("配对已过期，请重新生成。"); } }));
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix m = new MultiFormatWriter().encode(server.url(), BarcodeFormat.QR_CODE, 640, 640, hints);
            Bitmap bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[640 * 640];
            for (int y = 0; y < 640; y++) for (int x = 0; x < 640; x++) pixels[y * 640 + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
            bitmap.setPixels(pixels, 0, 640, 0, 0, 640, 640); qr.setImageBitmap(bitmap);
            info.setText("1. 手机和电视连接同一局域网\n2. 用浏览器扫码填写地址与账号\n3. 发送后回到电视确认\n\n配对标识：" + server.code() + "   ·   3 分钟有效");
            server.start();
        } catch (Exception e) {
            stop(); qr.setImageDrawable(null);
            info.setText(e.getMessage() == null ? "无法开启配对，请返回手动输入。" : e.getMessage());
        }
    }

    private static InetAddress address() throws Exception {
        java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) return null;
        for (NetworkInterface net : Collections.list(interfaces)) {
            if (!net.isUp() || net.isLoopback() || net.isVirtual()
                    || net.getName().startsWith("tun") || net.getName().startsWith("rmnet")) continue;
            for (InetAddress a : Collections.list(net.getInetAddresses())) {
                if (a instanceof Inet4Address && a.isSiteLocalAddress() && !a.isLoopbackAddress()) return a;
            }
        }
        return null;
    }

    private void receive(String plain) {
        if (isFinishing() || isDestroyed() || delivered) return;
        delivered = true; stop(); qr.setImageDrawable(null);
        try {
            JSONObject data = new JSONObject(plain);
            String site = Policy.server(data.getString("server"));
            String user = data.getString("username").trim();
            String password = data.getString("password"); String otp = data.optString("otp");
            if (site.length() > 2048 || user.isEmpty() || user.length() > 256 || password.length() > 2048 || otp.length() > 16) throw new Exception();
            info.setText("已收到配置，配对通道已关闭。\n请核对服务器与用户名。");
            new AlertDialog.Builder(this).setTitle("使用手机发送的配置？")
                    .setMessage("服务器：" + site + "\n用户名：" + user + "\n密码不会显示或保存。")
                    .setNegativeButton("拒绝", (a, b) -> finish())
                    .setPositiveButton("确认并填写", (a, b) -> {
                        Intent result = new Intent().putExtra("server", site).putExtra("username", user)
                                .putExtra("password", password).putExtra("otp", otp);
                        setResult(RESULT_OK, result); finish();
                    }).setOnCancelListener(x -> finish()).show();
        } catch (Exception e) { info.setText("收到的配置无效，请重新生成二维码。"); }
    }

    private void stop() { if (server != null) { server.close(); server = null; } }
    @Override protected void onRestart() { super.onRestart(); begin(); }
    @Override protected void onStop() { generation++; stop(); super.onStop(); }
    @Override protected void onDestroy() { stop(); super.onDestroy(); }
}
