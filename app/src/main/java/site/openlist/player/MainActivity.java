package site.openlist.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SessionStore store;
    private OpenListApi api;
    private LinearLayout body;
    private TextView location, status, connection, folderTitle, summary;
    private String path = "/";
    private int page = 1;
    private int generation;
    private boolean busy;
    private AlertDialog loginDialog;
    private EditText loginUrl, loginUser, loginPass, loginOtp;
    private static final int PAIR_REQUEST = 101;
    private static final int PAGE_SIZE = 80;
    private final List<Entry> entries = new ArrayList<>();

    private static final class Entry {
        final String name;
        final boolean dir;
        final long size;
        Entry(JSONObject o) { name = o.optString("name"); dir = o.optBoolean("is_dir"); size = o.optLong("size"); }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        store = new SessionStore(this);
        try {
            String server = Policy.server(store.server());
            api = new OpenListApi(server, store.token(server));
        } catch (IllegalArgumentException e) {
            // No configured server: remain offline until the user supplies one.
            api = null;
        }
        if (state != null) path = state.getString("path", "/");
        build();
        if (api == null || api.token.isEmpty()) showLogin(); else load(path, 1);
    }

    private int d(float n) { return TvStyle.dp(this, n); }
    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width, height); }

    private void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackground(new BrowserArt.Backdrop());
        setContentView(root);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(d(12), d(18), d(12), d(16));
        rail.setBackgroundColor(0x70000000);
        root.addView(rail, lp(d(84), -1));
        ImageView logo = icon(BrowserArt.MARK, TvStyle.ACCENT);
        LinearLayout.LayoutParams logoParams = lp(d(38), d(38));
        logoParams.bottomMargin = d(18);
        rail.addView(logo, logoParams);
        addRail(rail, BrowserArt.LIBRARY, "文件库", true, v -> load("/", 1));
        addRail(rail, BrowserArt.UP, "上一级", false, v -> up());
        addRail(rail, BrowserArt.REFRESH, "刷新", false, v -> load(path, 1));
        View spacer = new View(this);
        rail.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        addRail(rail, BrowserArt.SERVER, "服务器", false, v -> new AlertDialog.Builder(this)
                .setTitle("服务器与账号")
                .setItems(new String[]{"连接服务器 / 登录", "退出账号"}, (dialog, which) -> {
                    if (which == 0) showLogin(); else confirmLogout();
                }).setNegativeButton("取消", null).show());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(d(22), d(12), d(22), d(8));
        root.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout masthead = new LinearLayout(this);
        masthead.setGravity(Gravity.CENTER_VERTICAL);
        folderTitle = TvStyle.text(this, "文件库", 20, TvStyle.BROWSER_TEXT);
        folderTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        folderTitle.setSingleLine(true); folderTitle.setEllipsize(TextUtils.TruncateAt.END);
        masthead.addView(folderTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView wordmark = TvStyle.text(this, "OpenList TV", 11, TvStyle.BROWSER_MUTED);
        wordmark.setPadding(d(12), 0, d(16), 0);
        masthead.addView(wordmark);
        connection = TvStyle.text(this, "", 11, TvStyle.MUTED);
        connection.setSingleLine(true); connection.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        connection.setMaxWidth(d(210));
        masthead.addView(connection);
        content.addView(masthead, lp(-1, d(32)));

        LinearLayout breadcrumb = new LinearLayout(this);
        breadcrumb.setGravity(Gravity.CENTER_VERTICAL);
        location = TvStyle.text(this, "所有文件  /", 12, TvStyle.BROWSER_MUTED);
        location.setSingleLine(true);
        location.setEllipsize(TextUtils.TruncateAt.START);
        breadcrumb.addView(location, new LinearLayout.LayoutParams(0, -2, 1));
        summary = TvStyle.text(this, "云端文件，随心播放", 11, TvStyle.BROWSER_MUTED);
        summary.setPadding(d(12), 0, 0, 0);
        breadcrumb.addView(summary);
        LinearLayout.LayoutParams breadcrumbParams = lp(-1, d(26));
        breadcrumbParams.bottomMargin = d(6); content.addView(breadcrumb, breadcrumbParams);

        LinearLayout columns = new LinearLayout(this);
        columns.setPadding(d(18), 0, d(20), d(5));
        TextView columnName = TvStyle.text(this, "名称", 11, TvStyle.BROWSER_MUTED);
        columns.addView(columnName, new LinearLayout.LayoutParams(0, -2, 1));
        TextView columnKind = TvStyle.text(this, "大小", 11, TvStyle.BROWSER_MUTED);
        columns.addView(columnKind);
        content.addView(columns);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(d(2), d(2), d(2), d(8));
        scroll.addView(body);
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        status = TvStyle.text(this, "连接服务器，开始浏览", 11, TvStyle.BROWSER_MUTED);
        status.setSingleLine(true); status.setEllipsize(TextUtils.TruncateAt.END);
        footer.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        TextView hint = TvStyle.text(this, "方向键 移动    OK 打开    返回 上一级", 10, TvStyle.BROWSER_MUTED);
        hint.setPadding(d(12), 0, 0, 0); footer.addView(hint);
        LinearLayout.LayoutParams footerParams = lp(-1, d(22));
        footerParams.topMargin = d(3); content.addView(footer, footerParams);
        updateConnection();
        root.setAlpha(0);
        root.animate().alpha(1).setDuration(300).start();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this).setTitle("退出账号？")
                .setMessage("清除此设备保存的登录令牌，不删除云盘文件。")
                .setNegativeButton("取消", null).setPositiveButton("退出", (a, b) -> {
                    generation++;
                    store.clear();
                    if (api != null) api = new OpenListApi(api.server, "");
                    entries.clear(); body.removeAllViews(); updateConnection(); showLogin();
                }).show();
    }

    private ImageView icon(int kind, int color) {
        ImageView view = new ImageView(this);
        view.setImageDrawable(new BrowserArt(kind, color));
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    private void addRail(LinearLayout rail, int kind, String label, boolean selected, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL); item.setGravity(Gravity.CENTER);
        item.setFocusable(true); item.setClickable(true); item.setContentDescription(label);
        item.setBackground(TvStyle.browserFocus(true, selected));
        item.setOnClickListener(listener);
        item.addView(icon(kind, selected ? TvStyle.TEXT : TvStyle.BROWSER_MUTED), lp(d(22), d(22)));
        TextView caption = TvStyle.text(this, label, 11, selected ? TvStyle.TEXT : TvStyle.BROWSER_MUTED);
        caption.setPadding(0, d(5), 0, 0); item.addView(caption);
        LinearLayout.LayoutParams p = lp(-1, d(58)); p.bottomMargin = d(7);
        rail.addView(item, p);
    }

    private void updateConnection() {
        if (api == null) { connection.setText("未连接"); return; }
        connection.setText((api.token.isEmpty() ? "访客  ·  " : "已登录  ·  ") + Uri.parse(api.server).getHost());
    }

    private EditText field(LinearLayout form, String hint, String value, int type) {
        TextView label = TvStyle.text(this, hint, 14, TvStyle.MUTED);
        form.addView(label);
        EditText e = new EditText(this);
        e.setSaveEnabled(false);
        e.setTextColor(TvStyle.TEXT); e.setTextSize(17); e.setSingleLine(true);
        e.setInputType(type); e.setText(value);
        e.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams p = lp(d(440), d(48)); p.bottomMargin = d(12);
        form.addView(e, p);
        return e;
    }

    private void showLogin() {
        if (loginDialog != null && loginDialog.isShowing()) loginDialog.dismiss();
        generation++; busy = false;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL); form.setPadding(d(24), d(12), d(24), 0);
        EditText url = field(form, "OpenList 服务器", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText user = field(form, "用户名（推荐只读影视账号）", "", InputType.TYPE_CLASS_TEXT);
        EditText pass = field(form, "密码（不保存）", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText otp = field(form, "两步验证码（未开启则留空）", "", InputType.TYPE_CLASS_NUMBER);
        loginUrl = url; loginUser = user; loginPass = pass; loginOtp = otp;
        form.addView(TvStyle.button(this, "手机扫码填写配置", v -> {
            if (loginDialog != null) loginDialog.dismiss();
            startActivityForResult(new Intent(this, PairingActivity.class), PAIR_REQUEST);
        }));
        TextView feedback = TvStyle.text(this, "登录令牌由 Android Keystore 加密保存。HTTP 会明文传输，请优先使用 HTTPS。", 12, TvStyle.MUTED);
        form.addView(feedback);
        ScrollView scroller = new ScrollView(this); scroller.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("连接你的片库")
                .setView(scroller).setPositiveButton("登录", null)
                .setNeutralButton("访客浏览", null).setNegativeButton("取消", null).create();
        loginDialog = dialog;
        dialog.setOnDismissListener(x -> { pass.setText(""); otp.setText(""); });
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                try {
                    String server = Policy.server(url.getText().toString());
                    generation++; store.clear(); store.server(server); api = new OpenListApi(server, "");
                    pass.setText(""); dialog.dismiss(); updateConnection(); load("/", 1);
                } catch (RuntimeException e) { feedback.setText(e.getMessage()); }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                final String server;
                try { server = Policy.server(url.getText().toString()); }
                catch (RuntimeException e) { feedback.setText(e.getMessage()); return; }
                if (user.getText().toString().trim().isEmpty()) { feedback.setText("请填写用户名，或选择访客浏览。"); return; }
                String username = user.getText().toString().trim();
                String password = pass.getText().toString(); String code = otp.getText().toString();
                pass.setText(""); otp.setText("");
                Button login = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                login.setEnabled(false); feedback.setText("正在连接…");
                int request = ++generation;
                io.execute(() -> {
                    try {
                        JSONObject data = new JSONObject().put("username", username).put("password", password);
                        if (!code.isEmpty()) data.put("otp_code", code);
                        JSONObject response = new OpenListApi(server, "").post("/api/auth/login", data);
                        String token = response.getJSONObject("data").getString("token");
                        if (token.isEmpty()) throw new Exception("服务器未返回登录令牌");
                        runOnUiThread(() -> {
                            if (isDestroyed() || request != generation || !dialog.isShowing()) return;
                            api = new OpenListApi(server, token); store.server(server);
                            try { store.save(server, token); }
                            catch (Exception e) { store.clear(); toast("设备密钥不可用：本次会话不会保存"); }
                            dialog.dismiss(); updateConnection(); load("/", 1);
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            if (isDestroyed() || request != generation || !dialog.isShowing()) return;
                            login.setEnabled(true); feedback.setText(message(e));
                        });
                    }
                });
            });
        });
        dialog.show();
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PAIR_REQUEST) return;
        showLogin();
        if (resultCode == RESULT_OK && data != null) {
            loginUrl.setText(data.getStringExtra("server")); loginUser.setText(data.getStringExtra("username"));
            loginPass.setText(data.getStringExtra("password")); loginOtp.setText(data.getStringExtra("otp"));
            data.removeExtra("password"); data.removeExtra("otp");
        }
    }

    private void load(String destination, int nextPage) {
        if (api == null) { showLogin(); return; }
        final int request = ++generation;
        final OpenListApi client = api;
        busy = true; status.setText("正在读取目录…");
        io.execute(() -> {
            try {
                JSONObject result = client.post("/api/fs/list", new JSONObject().put("path", destination)
                        .put("page", nextPage).put("per_page", PAGE_SIZE).put("refresh", false).put("password", ""));
                JSONObject data = result.getJSONObject("data");
                JSONArray array = data.optJSONArray("content");
                List<Entry> list = new ArrayList<>();
                if (array != null) for (int i = 0; i < array.length(); i++) list.add(new Entry(array.getJSONObject(i)));
                int total = data.optInt("total", list.size());
                runOnUiThread(() -> {
                    if (isDestroyed() || request != generation) return;
                    busy = false; path = destination; page = nextPage; entries.clear(); entries.addAll(list);
                    location.setText(path.equals("/") ? "所有文件  /" : "所有文件  /  " + path.substring(1).replace("/", "  /  "));
                    folderTitle.setText(FilePresentation.title(path)); render(total);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || request != generation) return;
                    busy = false; status.setText(message(e));
                    new AlertDialog.Builder(this).setTitle("暂时无法读取目录").setMessage(message(e))
                            .setPositiveButton("重试", (a, b) -> load(destination, nextPage))
                            .setNeutralButton("登录", (a, b) -> showLogin()).setNegativeButton("返回", null).show();
                });
            }
        });
    }

    private void render(int total) {
        body.removeAllViews();
        if (!path.equals("/")) addRow(BrowserArt.UP, "上一级目录", "返回父目录", "", v -> up());
        for (Entry entry : entries) {
            boolean video = Policy.video(entry.name);
            int kind = entry.dir ? BrowserArt.FOLDER : video ? BrowserArt.VIDEO : BrowserArt.FILE;
            View row = addRow(kind, entry.name, entry.dir ? "文件夹" : FilePresentation.extension(entry.name)
                    + (video ? "  ·  视频" : "  ·  文件"), entry.dir ? "打开目录" : FilePresentation.size(entry.size), v -> {
                if (busy) return;
                if (entry.dir) load(Policy.child(path, entry.name), 1);
                else if (Policy.video(entry.name)) resolve(entry, false);
                else toast("当前版本仅打开视频文件");
            });
            if (video && !entry.dir) row.setOnLongClickListener(v -> {
                if (!busy) choose(entry, v);
                return true;
            });
        }
        if (entries.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL); empty.setGravity(Gravity.CENTER);
            empty.setPadding(d(20), d(32), d(20), d(32));
            empty.addView(icon(BrowserArt.FOLDER, TvStyle.TEXT), lp(d(40), d(40)));
            TextView label = TvStyle.text(this, "这里还没有文件", 20, TvStyle.BROWSER_TEXT);
            label.setPadding(0, d(14), 0, d(6)); empty.addView(label);
            empty.addView(TvStyle.text(this, "切换其他目录，或使用左侧刷新", 12, TvStyle.BROWSER_MUTED));
            body.addView(empty);
        }
        LinearLayout nav = new LinearLayout(this);
        if (page > 1) nav.addView(TvStyle.button(this, "上一页", v -> load(path, page - 1)));
        if ((long) page * PAGE_SIZE < total) nav.addView(TvStyle.button(this, "下一页", v -> load(path, page + 1)));
        body.addView(nav);
        int folders = 0, videos = 0;
        for (Entry entry : entries) { if (entry.dir) folders++; else if (Policy.video(entry.name)) videos++; }
        summary.setText(entries.isEmpty() ? "空目录" : (total > PAGE_SIZE ? "本页  ·  " : "")
                + folders + " 文件夹  ·  " + videos + " 视频");
        status.setText("共 " + total + " 项    /    第 " + page + " 页");
        body.post(() -> {
            for (int i = 0; i < body.getChildCount(); i++) {
                if (body.getChildAt(i).requestFocus()) break;
            }
        });
    }

    private View addRow(int kind, String label, String subtitle, String detail, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(d(16), d(10), d(14), d(10));
        row.setDescendantFocusability(LinearLayout.FOCUS_BLOCK_DESCENDANTS);
        row.setFocusable(true); row.setClickable(true); row.setBackground(TvStyle.browserFocus(false, false));
        row.setOnClickListener(listener);
        int color = kind == BrowserArt.FOLDER || kind == BrowserArt.VIDEO ? TvStyle.TEXT : TvStyle.BROWSER_MUTED;
        ImageView badge = icon(kind, color);
        badge.setPadding(d(9), d(9), d(9), d(9));
        badge.setBackground(TvStyle.browserBox(0x0affffff, 0));
        LinearLayout.LayoutParams badgeParams = lp(d(42), d(42)); badgeParams.rightMargin = d(14);
        row.addView(badge, badgeParams);
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL);
        TextView name = TvStyle.text(this, label, 16, TvStyle.BROWSER_TEXT);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        name.setMarqueeRepeatLimit(-1);
        row.setOnFocusChangeListener((v, focused) -> name.setSelected(focused));
        names.addView(name);
        TextView meta = TvStyle.text(this, subtitle, 11, TvStyle.BROWSER_MUTED);
        meta.setPadding(0, d(4), 0, 0); names.addView(meta);
        row.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        TextView trailing = TvStyle.text(this, detail, 12, TvStyle.BROWSER_MUTED);
        trailing.setGravity(Gravity.END); trailing.setPadding(d(12), 0, d(10), 0);
        row.addView(trailing);
        row.addView(icon(BrowserArt.CHEVRON, TvStyle.BROWSER_MUTED), lp(d(16), d(16)));
        row.setContentDescription(label + ", " + subtitle + (detail.isEmpty() ? "" : ", " + detail));
        LinearLayout.LayoutParams p = lp(-1, -2); p.bottomMargin = d(7);
        row.setMinimumHeight(d(66)); body.addView(row, p);
        return row;
    }

    private void choose(Entry entry, View anchor) {
        AnchoredMenu.show(this, anchor, new String[]{"内置播放器", "外部播放器"}, 0,
                which -> resolve(entry, which == 1), null);
    }

    private void resolve(Entry entry, boolean external) {
        if (api == null) { showLogin(); return; }
        if (busy) return;
        int request = ++generation;
        OpenListApi client = api; String file = Policy.child(path, entry.name);
        busy = true; status.setText("正在获取播放地址…");
        io.execute(() -> {
            try {
                JSONObject data = client.post("/api/fs/get", new JSONObject().put("path", file).put("password", "")).getJSONObject("data");
                String url = data.optString("raw_url");
                if (!Policy.mediaUrl(url)) throw new Exception("该文件没有可用的 HTTP(S) 播放地址");
                HashMap<String, String> headers = new HashMap<>();
                // fs/get.header is page metadata, not media credentials. Drivers needing headers
                // must expose a playable proxy URL through OpenList's existing Web Proxy setting.
                runOnUiThread(() -> {
                    if (isDestroyed() || request != generation) return;
                    busy = false;
                    status.setText("播放来源：" + Uri.parse(url).getHost() + "  ·  最终流量路径取决于存储策略");
                    if (external) {
                        if (!headers.isEmpty()) { toast("此源需要额外请求头，请使用内置播放器"); return; }
                        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*");
                        intent.putExtra(Intent.EXTRA_TITLE, entry.name);
                        try { startActivity(Intent.createChooser(intent, "选择播放器（会向所选应用提供播放链接）")); }
                        catch (ActivityNotFoundException e) { toast("请先安装 Just Player、VLC 等播放器"); }
                    } else {
                        Intent intent = new Intent(this, PlayerActivity.class);
                        intent.putExtra("url", url); intent.putExtra("title", entry.name);
                        intent.putExtra("headers", headers); startActivity(intent);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || request != generation) return;
                    busy = false; status.setText(message(e));
                    new AlertDialog.Builder(this).setTitle("无法获取播放地址").setMessage(message(e)).setPositiveButton("知道了", null).show();
                });
            }
        });
    }

    private String message(Exception e) {
        if (e instanceof java.net.SocketTimeoutException) return "连接超时，请检查网络与 OpenList 服务";
        if (e instanceof javax.net.ssl.SSLException) return "HTTPS 证书验证失败，请检查服务器证书";
        if (e instanceof java.io.IOException) return "网络请求失败，请检查地址、网络及 OpenList 服务";
        return e.getMessage() == null ? "操作未完成，请重试" : e instanceof org.json.JSONException ? "服务器响应格式不兼容" : e.getMessage();
    }

    private void toast(String text) { android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show(); }
    private void up() { if (busy) return; if (!path.equals("/")) load(Policy.parent(path), 1); }

    @Override public void onBackPressed() {
        if (!path.equals("/")) { load(Policy.parent(path), 1); return; }
        new AlertDialog.Builder(this).setTitle("退出 OpenList TV？")
                .setNegativeButton("继续浏览", null).setPositiveButton("退出", (a, b) -> finish()).show();
    }

    @Override protected void onSaveInstanceState(Bundle out) { out.putString("path", path); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() { generation++; io.shutdownNow(); super.onDestroy(); }
}
