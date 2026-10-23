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
        addRail(rail, BrowserArt.LIBRARY, getString(R.string.library), true, v -> load("/", 1));
        addRail(rail, BrowserArt.UP, getString(R.string.up), false, v -> up());
        addRail(rail, BrowserArt.REFRESH, getString(R.string.refresh), false, v -> load(path, 1));
        View spacer = new View(this);
        rail.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        addRail(rail, BrowserArt.SERVER, getString(R.string.server), false, v -> new AlertDialog.Builder(this)
                .setTitle(getString(R.string.server_account))
                .setItems(new String[]{getString(R.string.connect_login), getString(R.string.logout)}, (dialog, which) -> {
                    if (which == 0) showLogin(); else confirmLogout();
                }).setNegativeButton(getString(R.string.cancel), null).show());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(d(22), d(12), d(22), d(8));
        root.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout masthead = new LinearLayout(this);
        masthead.setGravity(Gravity.CENTER_VERTICAL);
        folderTitle = TvStyle.text(this, getString(R.string.library), 20, TvStyle.BROWSER_TEXT);
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
        location = TvStyle.text(this, getString(R.string.all_files), 12, TvStyle.BROWSER_MUTED);
        location.setSingleLine(true);
        location.setEllipsize(TextUtils.TruncateAt.START);
        breadcrumb.addView(location, new LinearLayout.LayoutParams(0, -2, 1));
        summary = TvStyle.text(this, getString(R.string.cloud_files), 11, TvStyle.BROWSER_MUTED);
        summary.setPadding(d(12), 0, 0, 0);
        breadcrumb.addView(summary);
        LinearLayout.LayoutParams breadcrumbParams = lp(-1, d(26));
        breadcrumbParams.bottomMargin = d(6); content.addView(breadcrumb, breadcrumbParams);

        LinearLayout columns = new LinearLayout(this);
        columns.setPadding(d(18), 0, d(20), d(5));
        TextView columnName = TvStyle.text(this, getString(R.string.name), 11, TvStyle.BROWSER_MUTED);
        columns.addView(columnName, new LinearLayout.LayoutParams(0, -2, 1));
        TextView columnKind = TvStyle.text(this, getString(R.string.size), 11, TvStyle.BROWSER_MUTED);
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
        status = TvStyle.text(this, getString(R.string.connect_start), 11, TvStyle.BROWSER_MUTED);
        status.setSingleLine(true); status.setEllipsize(TextUtils.TruncateAt.END);
        footer.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        TextView hint = TvStyle.text(this, getString(R.string.remote_hint), 10, TvStyle.BROWSER_MUTED);
        hint.setPadding(d(12), 0, 0, 0); footer.addView(hint);
        LinearLayout.LayoutParams footerParams = lp(-1, d(22));
        footerParams.topMargin = d(3); content.addView(footer, footerParams);
        updateConnection();
        root.setAlpha(0);
        root.animate().alpha(1).setDuration(300).start();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.logout_title))
                .setMessage(getString(R.string.logout_message))
                .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.exit), (a, b) -> {
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
        if (api == null) { connection.setText(getString(R.string.not_connected)); return; }
        connection.setText((api.token.isEmpty() ? getString(R.string.guest_prefix) : getString(R.string.signed_in_prefix)) + Uri.parse(api.server).getHost());
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
        EditText url = field(form, getString(R.string.server_label), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText user = field(form, getString(R.string.user_label), "", InputType.TYPE_CLASS_TEXT);
        EditText pass = field(form, getString(R.string.password_label), "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText otp = field(form, getString(R.string.otp_label), "", InputType.TYPE_CLASS_NUMBER);
        loginUrl = url; loginUser = user; loginPass = pass; loginOtp = otp;
        form.addView(TvStyle.button(this, getString(R.string.scan_config), v -> {
            if (loginDialog != null) loginDialog.dismiss();
            startActivityForResult(new Intent(this, PairingActivity.class), PAIR_REQUEST);
        }));
        TextView feedback = TvStyle.text(this, getString(R.string.session_notice), 12, TvStyle.MUTED);
        form.addView(feedback);
        ScrollView scroller = new ScrollView(this); scroller.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.connect_library))
                .setView(scroller).setPositiveButton(getString(R.string.login), null)
                .setNeutralButton(getString(R.string.guest_browse), null).setNegativeButton(getString(R.string.cancel), null).create();
        loginDialog = dialog;
        dialog.setOnDismissListener(x -> { pass.setText(""); otp.setText(""); });
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                try {
                    String server = Policy.server(url.getText().toString());
                    generation++; store.clear(); store.server(server); api = new OpenListApi(server, "");
                    pass.setText(""); dialog.dismiss(); updateConnection(); load("/", 1);
                } catch (RuntimeException e) { feedback.setText(message(e)); }
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                final String server;
                try { server = Policy.server(url.getText().toString()); }
                catch (RuntimeException e) { feedback.setText(message(e)); return; }
                if (user.getText().toString().trim().isEmpty()) { feedback.setText(getString(R.string.username_required)); return; }
                String username = user.getText().toString().trim();
                String password = pass.getText().toString(); String code = otp.getText().toString();
                pass.setText(""); otp.setText("");
                Button login = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                login.setEnabled(false); feedback.setText(getString(R.string.connecting));
                int request = ++generation;
                io.execute(() -> {
                    try {
                        JSONObject data = new JSONObject().put("username", username).put("password", password);
                        if (!code.isEmpty()) data.put("otp_code", code);
                        JSONObject response = new OpenListApi(server, "").post("/api/auth/login", data);
                        String token = response.getJSONObject("data").getString("token");
                        if (token.isEmpty()) throw new Exception(getString(R.string.missing_token));
                        runOnUiThread(() -> {
                            if (isDestroyed() || request != generation || !dialog.isShowing()) return;
                            api = new OpenListApi(server, token); store.server(server);
                            try { store.save(server, token); }
                            catch (Exception e) { store.clear(); toast(getString(R.string.key_unavailable)); }
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
        busy = true; status.setText(getString(R.string.loading_directory));
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
                    location.setText(path.equals("/") ? getString(R.string.all_files) : getString(R.string.all_files_prefix) + path.substring(1).replace("/", "  /  "));
                    folderTitle.setText(path.equals("/") ? getString(R.string.library) : FilePresentation.title(path)); render(total);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isDestroyed() || request != generation) return;
                    busy = false; status.setText(message(e));
                    new AlertDialog.Builder(this).setTitle(getString(R.string.directory_error)).setMessage(message(e))
                            .setPositiveButton(getString(R.string.retry), (a, b) -> load(destination, nextPage))
                            .setNeutralButton(getString(R.string.login), (a, b) -> showLogin()).setNegativeButton(getString(R.string.back), null).show();
                });
            }
        });
    }

    private void render(int total) {
        body.removeAllViews();
        if (!path.equals("/")) addRow(BrowserArt.UP, getString(R.string.parent_directory), getString(R.string.return_parent), "", v -> up());
        for (Entry entry : entries) {
            boolean video = Policy.video(entry.name);
            int kind = entry.dir ? BrowserArt.FOLDER : video ? BrowserArt.VIDEO : BrowserArt.FILE;
            View row = addRow(kind, entry.name, entry.dir ? getString(R.string.folder) : FilePresentation.extension(entry.name)
                    + (video ? getString(R.string.video_suffix) : getString(R.string.file_suffix)), entry.dir ? getString(R.string.open_directory) : FilePresentation.size(entry.size), v -> {
                if (busy) return;
                if (entry.dir) load(Policy.child(path, entry.name), 1);
                else if (Policy.video(entry.name)) resolve(entry, false);
                else toast(getString(R.string.videos_only));
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
            TextView label = TvStyle.text(this, getString(R.string.no_files), 20, TvStyle.BROWSER_TEXT);
            label.setPadding(0, d(14), 0, d(6)); empty.addView(label);
            empty.addView(TvStyle.text(this, getString(R.string.empty_hint), 12, TvStyle.BROWSER_MUTED));
            body.addView(empty);
        }
        LinearLayout nav = new LinearLayout(this);
        if (page > 1) nav.addView(TvStyle.button(this, getString(R.string.previous_page), v -> load(path, page - 1)));
        if ((long) page * PAGE_SIZE < total) nav.addView(TvStyle.button(this, getString(R.string.next_page), v -> load(path, page + 1)));
        body.addView(nav);
        int folders = 0, videos = 0;
        for (Entry entry : entries) { if (entry.dir) folders++; else if (Policy.video(entry.name)) videos++; }
        summary.setText(entries.isEmpty() ? getString(R.string.empty_directory) : (total > PAGE_SIZE ? getString(R.string.this_page) : "")
                + getString(R.string.folder_counts, folders, videos));
        status.setText(getString(R.string.page_status, total, page));
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
        AnchoredMenu.show(this, anchor, new String[]{getString(R.string.internal_player), getString(R.string.external_player)}, 0,
                which -> resolve(entry, which == 1), null);
    }

    private void resolve(Entry entry, boolean external) {
        if (api == null) { showLogin(); return; }
        if (busy) return;
        int request = ++generation;
        OpenListApi client = api; String file = Policy.child(path, entry.name);
        busy = true; status.setText(getString(R.string.resolving));
        io.execute(() -> {
            try {
                JSONObject data = client.post("/api/fs/get", new JSONObject().put("path", file).put("password", "")).getJSONObject("data");
                String url = data.optString("raw_url");
                if (!Policy.mediaUrl(url)) throw new Exception(getString(R.string.no_media_url));
                HashMap<String, String> headers = new HashMap<>();
                // fs/get.header is page metadata, not media credentials. Drivers needing headers
                // must expose a playable proxy URL through OpenList's existing Web Proxy setting.
                runOnUiThread(() -> {
                    if (isDestroyed() || request != generation) return;
                    busy = false;
                    status.setText(getString(R.string.playback_source, Uri.parse(url).getHost()));
                    if (external) {
                        if (!headers.isEmpty()) { toast(getString(R.string.headers_internal)); return; }
                        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*");
                        intent.putExtra(Intent.EXTRA_TITLE, entry.name);
                        try { startActivity(Intent.createChooser(intent, getString(R.string.choose_player))); }
                        catch (ActivityNotFoundException e) { toast(getString(R.string.install_player)); }
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
                    new AlertDialog.Builder(this).setTitle(getString(R.string.resolve_error)).setMessage(message(e)).setPositiveButton(getString(R.string.ok), null).show();
                });
            }
        });
    }

    private String message(Exception e) {
        if (e instanceof UiError) return getString(((UiError) e).resource, ((UiError) e).arguments);
        if (e instanceof java.net.SocketTimeoutException) return getString(R.string.network_timeout);
        if (e instanceof javax.net.ssl.SSLException) return getString(R.string.tls_error);
        if (e instanceof java.io.IOException) return getString(R.string.network_error);
        return e.getMessage() == null ? getString(R.string.generic_error) : e instanceof org.json.JSONException ? getString(R.string.response_error) : e.getMessage();
    }

    private void toast(String text) { android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show(); }
    private void up() { if (busy) return; if (!path.equals("/")) load(Policy.parent(path), 1); }

    @Override public void onBackPressed() {
        if (!path.equals("/")) { load(Policy.parent(path), 1); return; }
        new AlertDialog.Builder(this).setTitle(getString(R.string.exit_title))
                .setNegativeButton(getString(R.string.keep_browsing), null).setPositiveButton(getString(R.string.exit), (a, b) -> finish()).show();
    }

    @Override protected void onSaveInstanceState(Bundle out) { out.putString("path", path); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() { generation++; io.shutdownNow(); super.onDestroy(); }
}
