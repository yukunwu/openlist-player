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
        text.addView(TvStyle.text(this, getString(R.string.pair_title), 30, TvStyle.TEXT));
        info = TvStyle.text(this, getString(R.string.pair_generating), 16, TvStyle.MUTED);
        info.setPadding(0, dp(18), 0, dp(20)); text.addView(info);
        text.addView(TvStyle.button(this, getString(R.string.pair_regenerate), v -> begin()));
        text.addView(TvStyle.button(this, getString(R.string.pair_manual), v -> finish()));
        text.addView(TvStyle.text(this, getString(R.string.pair_notice), 12, TvStyle.MUTED));
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
            if (address == null) throw new Exception(getString(R.string.pair_no_network));
            server = new PairingServer(address, asset("pairing.html"), asset("forge.min.js"),
                    text -> runOnUiThread(() -> { if (request == generation) receive(text); }),
                    () -> runOnUiThread(() -> { if (request == generation && !isDestroyed() && !delivered) { qr.setImageDrawable(null); info.setText(getString(R.string.pair_expired)); } }));
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix m = new MultiFormatWriter().encode(server.url(), BarcodeFormat.QR_CODE, 640, 640, hints);
            Bitmap bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[640 * 640];
            for (int y = 0; y < 640; y++) for (int x = 0; x < 640; x++) pixels[y * 640 + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
            bitmap.setPixels(pixels, 0, 640, 0, 0, 640, 640); qr.setImageBitmap(bitmap);
            info.setText(getString(R.string.pair_steps, server.code()));
            server.start();
        } catch (Exception e) {
            stop(); qr.setImageDrawable(null);
            info.setText(e.getMessage() == null ? getString(R.string.pair_error) : e.getMessage());
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
            info.setText(getString(R.string.pair_received));
            new AlertDialog.Builder(this).setTitle(getString(R.string.pair_confirm))
                    .setMessage(getString(R.string.pair_details, site, user))
                    .setNegativeButton(getString(R.string.reject), (a, b) -> finish())
                    .setPositiveButton(getString(R.string.confirm_fill), (a, b) -> {
                        Intent result = new Intent().putExtra("server", site).putExtra("username", user)
                                .putExtra("password", password).putExtra("otp", otp);
                        setResult(RESULT_OK, result); finish();
                    }).setOnCancelListener(x -> finish()).show();
        } catch (Exception e) { info.setText(getString(R.string.pair_invalid)); }
    }

    private void stop() { if (server != null) { server.close(); server = null; } }
    @Override protected void onRestart() { super.onRestart(); begin(); }
    @Override protected void onStop() { generation++; stop(); super.onStop(); }
    @Override protected void onDestroy() { stop(); super.onDestroy(); }
}
