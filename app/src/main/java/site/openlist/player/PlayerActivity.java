package site.openlist.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.PopupWindow;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.DefaultTimeBar;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    private VideoPlayerView playerView;
    private TextView info;
    private LevelHud levelHud;
    private TextView feedback;
    private TextView ratioButton, speedButton;
    private ImageButton lockButton;
    private PopupWindow optionsMenu;
    private View top;
    private AudioManager audio;
    private boolean touchDevice, locked, controlsVisible, unlockingTouch;
    private boolean lockHintVisible;
    private static final long LOCK_HINT_TIMEOUT_MS = 2500;
    private final PlaybackInput input = new PlaybackInput();
    private final Set<Integer> consumedKeys = new HashSet<>();
    private OnBackInvokedCallback backCallback;
    private boolean stopping;
    private long lastSeekKeyTime;
    private float speed = 1;
    private final PlaybackGesture gesture = new PlaybackGesture();
    private float touchBrightness, touchVolume;
    private int volumeMax;
    private int videoMode;
    private float windowBrightness = -1;
    private int dragging;
    private boolean ownsTouch;
    private final Runnable hideLevel = () -> { if (levelHud != null) levelHud.setVisibility(View.GONE); };
    private final Runnable hideFeedback = () -> { if (feedback != null) feedback.setVisibility(View.GONE); };
    private final Runnable hideLockHint = () -> {
        lockHintVisible = false;
        updateLockButton();
    };
    private String url, title;
    private Map<String, String> headers;
    private long position;
    private boolean play = true;
    private boolean errorVisible;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        url = getIntent().getStringExtra("url"); title = getIntent().getStringExtra("title");
        if (url == null || !Policy.mediaUrl(url)) { finish(); return; }
        headers = new HashMap<>();
        Object raw = getIntent().getSerializableExtra("headers");
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    headers.put((String) entry.getKey(), (String) entry.getValue());
                }
            }
        }
        videoMode = VideoPresentation.validMode(getPreferences(MODE_PRIVATE).getInt("video_mode", 0));
        touchDevice = getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK) != Configuration.UI_MODE_TYPE_TELEVISION;
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (state != null) {
            position = state.getLong("position"); play = state.getBoolean("play", true);
            windowBrightness = state.getFloat("brightness", -1);
            locked = state.getBoolean("locked", false); speed = state.getFloat("speed", 1);
        }
        if (windowBrightness >= 0) applyBrightness(windowBrightness, false);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK); setContentView(root);
        playerView = new VideoPlayerView(this);
        playerView.setVideoMode(videoMode);
        playerView.setShowSubtitleButton(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        playerView.setControllerAutoShow(true);
        playerView.setControllerAnimationEnabled(false);
        playerView.setControllerShowTimeoutMs(4500);
        styleController();
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout header = new LinearLayout(this);
        top = header;
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(10), dp(20), dp(20));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xb3000000, 0x00000000}));
        LinearLayout metadata = new LinearLayout(this);
        metadata.setOrientation(LinearLayout.VERTICAL);
        TextView name = TvStyle.text(this, title == null ? "视频" : title, 16, TvStyle.TEXT);
        name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END); metadata.addView(name);
        info = TvStyle.text(this, "正在载入视频信息", 11, TvStyle.MUTED);
        info.setSingleLine(true); info.setEllipsize(TextUtils.TruncateAt.END); metadata.addView(info);
        header.addView(metadata, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(top, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        LinearLayout basic = playerView.findViewById(androidx.media3.ui.R.id.exo_basic_controls);
        ratioButton = action(VideoPresentation.LABELS[videoMode], v -> showVideoModes());
        speedButton = action(PlaybackPolicy.SPEED_LABELS[PlaybackPolicy.speedIndex(speed)], v -> showSpeeds());
        // Insert before Media3's overflow sentinel so compact screens retain its overflow handling.
        basic.addView(ratioButton, Math.max(0, basic.getChildCount() - 1));
        basic.addView(speedButton, Math.max(0, basic.getChildCount() - 1));
        levelHud = new LevelHud(this); levelHud.setVisibility(View.GONE);
        FrameLayout.LayoutParams hud = new FrameLayout.LayoutParams(dp(58), dp(176), Gravity.CENTER_VERTICAL | Gravity.START);
        hud.leftMargin = dp(26); root.addView(levelHud, hud);
        feedback = TvStyle.text(this, "", 16, TvStyle.TEXT);
        feedback.setPadding(dp(18), dp(10), dp(18), dp(10));
        feedback.setBackground(TvStyle.browserBox(0x99000000, 0)); feedback.setVisibility(View.GONE);
        root.addView(feedback, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        lockButton = new ImageButton(this);
        lockButton.setFocusable(true); lockButton.setClickable(true);
        lockButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        lockButton.setBackground(TvStyle.browserFocus(false, false));
        lockButton.setOnClickListener(v -> setLocked(!locked));
        FrameLayout.LayoutParams lockParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.BOTTOM | Gravity.START);
        lockParams.leftMargin = dp(20); lockParams.bottomMargin = dp(96); root.addView(lockButton, lockParams);
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            controlsVisible = visibility == View.VISIBLE && !locked;
            if (!controlsVisible) {
                input.leaveControls();
                playerView.setControllerShowTimeoutMs(4500);
            }
            top.setVisibility(controlsVisible ? View.VISIBLE : View.GONE);
            updateLockButton();
        });
        setLocked(locked);
        playerView.requestFocus();
    }

    private int dp(int n) { return TvStyle.dp(this, n); }

    private void styleController() {
        // Shade the lower controls, not the entire video, while preserving Media3's layout and input handling.
        View backdrop = playerView.findViewById(androidx.media3.ui.R.id.exo_controls_background);
        if (backdrop != null) backdrop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0xa6000000}));
        View bottom = playerView.findViewById(androidx.media3.ui.R.id.exo_bottom_bar);
        if (bottom != null) bottom.setBackgroundColor(Color.TRANSPARENT);
        View progress = playerView.findViewById(androidx.media3.ui.R.id.exo_progress);
        if (progress instanceof DefaultTimeBar) {
            DefaultTimeBar bar = (DefaultTimeBar) progress;
            bar.setPlayedColor(TvStyle.ACCENT); bar.setScrubberColor(TvStyle.TEXT);
            bar.setBufferedColor(0x88ffffff); bar.setUnplayedColor(0x33ffffff);
        }
    }

    private TextView action(String label, View.OnClickListener listener) {
        TextView button = TvStyle.text(this, label, 12, TvStyle.TEXT);
        button.setGravity(Gravity.CENTER); button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(TvStyle.browserFocus(true, false));
        button.setFocusable(true); button.setClickable(true); button.setOnClickListener(listener);
        button.setLayoutParams(new LinearLayout.LayoutParams(-2, dp(48)));
        button.setOnFocusChangeListener((v, focused) -> {
            if (focused && controlsVisible) input.enterControls();
            playerView.setControllerShowTimeoutMs(focused ? 0 : 4500);
        });
        return button;
    }

    private void showVideoModes() {
        input.enterControls();
        playerView.setControllerShowTimeoutMs(0);
        optionsMenu = AnchoredMenu.show(this, ratioButton, VideoPresentation.LABELS, videoMode, which -> {
                    videoMode = which; playerView.setVideoMode(which);
                    getPreferences(MODE_PRIVATE).edit().putInt("video_mode", which).apply();
                    ratioButton.setText(VideoPresentation.LABELS[which]);
                }, this::restoreControllerTimeout);
    }

    private void showSpeeds() {
        input.enterControls();
        playerView.setControllerShowTimeoutMs(0);
        optionsMenu = AnchoredMenu.show(this, speedButton, PlaybackPolicy.SPEED_LABELS,
                PlaybackPolicy.speedIndex(speed), which -> {
                    speed = PlaybackPolicy.SPEEDS[which];
                    if (player != null) player.setPlaybackSpeed(speed);
                    speedButton.setText(PlaybackPolicy.SPEED_LABELS[which]);
                }, this::restoreControllerTimeout);
    }

    private void restoreControllerTimeout() {
        optionsMenu = null;
        if (isFinishing() || stopping) return;
        input.enterControls();
        playerView.setControllerShowTimeoutMs(4500);
        // The popup returns focus to its anchor. Do not showController() here: Media3 may focus Play instead.
    }

    private float currentBrightness() {
        if (windowBrightness >= 0) return windowBrightness;
        return VideoPresentation.brightness(Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128) / 255f);
    }

    private void applyBrightness(float value, boolean hud) {
        windowBrightness = VideoPresentation.brightness(value);
        WindowManager.LayoutParams p = getWindow().getAttributes(); p.screenBrightness = windowBrightness;
        getWindow().setAttributes(p);
        if (hud) showLevel("亮度", windowBrightness, false);
    }

    private void showLevel(String label, float value, boolean right) {
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) levelHud.getLayoutParams();
        p.gravity = Gravity.CENTER_VERTICAL | (right ? Gravity.END : Gravity.START);
        p.leftMargin = p.rightMargin = dp(26); levelHud.setLayoutParams(p);
        levelHud.setLevel(label, value); levelHud.setVisibility(View.VISIBLE);
        levelHud.removeCallbacks(hideLevel);
        if (dragging == PlaybackGesture.NONE) levelHud.postDelayed(hideLevel, 800);
    }

    private void updateLockButton() {
        lockButton.setImageDrawable(new BrowserArt(locked ? BrowserArt.LOCK : BrowserArt.UNLOCK,
                locked ? TvStyle.ACCENT : TvStyle.TEXT));
        lockButton.setBackground(TvStyle.browserBox(0x66000000, locked ? 0x99e50914 : 0));
        lockButton.setContentDescription(locked ? "解锁画面" : "锁定画面");
        lockButton.setVisibility(touchDevice && (locked ? lockHintVisible : controlsVisible) ? View.VISIBLE : View.GONE);
    }

    private void showLockHint() {
        if (!locked || stopping) return;
        lockHintVisible = true;
        lockButton.removeCallbacks(hideLockHint);
        updateLockButton();
        if (!unlockingTouch) lockButton.postDelayed(hideLockHint, LOCK_HINT_TIMEOUT_MS);
    }

    private void clearLockHint() {
        unlockingTouch = false;
        lockHintVisible = false;
        if (lockButton != null) lockButton.removeCallbacks(hideLockHint);
    }

    private void setLocked(boolean value) {
        resetTouch(); clearLockHint(); locked = value && touchDevice;
        input.leaveControls();
        playerView.setUseController(!locked); playerView.setControllerAutoShow(!locked);
        if (locked) {
            playerView.hideController(); controlsVisible = false; top.setVisibility(View.GONE);
            levelHud.setVisibility(View.GONE); feedback.setVisibility(View.GONE);
            showLockHint();
        } else playerView.showController();
        updateLockButton();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (playerView == null) return super.dispatchTouchEvent(event);
        int action = event.getActionMasked();
        if (locked) {
            if (action == MotionEvent.ACTION_DOWN) {
                int[] point = new int[2]; lockButton.getLocationOnScreen(point);
                // A tap on the hidden icon's old bounds reveals it, never unlocks in the same stream.
                unlockingTouch = lockButton.isShown()
                        && event.getRawX() >= point[0] && event.getRawX() <= point[0] + lockButton.getWidth()
                        && event.getRawY() >= point[1] && event.getRawY() <= point[1] + lockButton.getHeight();
                showLockHint();
            }
            if (unlockingTouch) super.dispatchTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                unlockingTouch = false;
                showLockHint();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            resetTouch();
            if (controlsVisible) input.enterControls();
            touchBrightness = currentBrightness();
            volumeMax = audio == null ? 0 : audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            touchVolume = audio == null ? 0 : audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            dragging = PlaybackGesture.NONE;
            boolean eligible = touchDevice && !errorVisible && optionsMenu == null
                    && event.getY() > dp(48) && event.getY() < playerView.getHeight() - dp(80)
                    && !hitsControl(getWindow().getDecorView(), event.getRawX(), event.getRawY());
            gesture.begin(event.getX(), event.getY(), playerView.getWidth(), event.getEventTime(), eligible);
            // Keep the entire blank-video stream away from Media3 until it proves to be a tap.
            ownsTouch = eligible;
            if (ownsTouch) return true;
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            gesture.cancel();
            if (ownsTouch) return true;
        } else if (ownsTouch && event.getPointerCount() > 1) {
            return true;
        } else if (ownsTouch && action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 1) {
            int mode = gesture.move(event.getX(), event.getY(), event.getEventTime(),
                    ViewConfiguration.get(this).getScaledTouchSlop(), ViewConfiguration.getLongPressTimeout());
            if (dragging == PlaybackGesture.NONE && mode != PlaybackGesture.NONE) {
                playerView.hideController();
            }
            dragging = mode;
            if (mode == PlaybackGesture.BRIGHTNESS) applyBrightness(touchBrightness + gesture.delta(event.getY(), playerView.getHeight()), true);
            if (mode == PlaybackGesture.VOLUME && audio != null && volumeMax > 0) {
                int volume = Math.max(0, Math.min(volumeMax, Math.round(touchVolume + gesture.delta(event.getY(), playerView.getHeight()) * volumeMax)));
                try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0); }
                catch (SecurityException ignored) { }
                showLevel("音量", audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) volumeMax, true);
            }
            return true;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean consumed = ownsTouch;
            boolean tap = consumed && action == MotionEvent.ACTION_UP && gesture.isTap();
            resetTouch();
            if (consumed) levelHud.postDelayed(hideLevel, 800);
            if (tap) playerView.performClick();
            if (consumed) return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private boolean hitsControl(View view, float x, float y) {
        if (!view.isShown()) return false;
        int[] point = new int[2]; view.getLocationOnScreen(point);
        if (x < point[0] || y < point[1] || x >= point[0] + view.getWidth() || y >= point[1] + view.getHeight()) return false;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) if (hitsControl(group.getChildAt(i), x, y)) return true;
        }
        return view != playerView && !(view instanceof ViewGroup)
                && (view.isClickable() || view instanceof DefaultTimeBar);
    }

    private void resetTouch() { ownsTouch = false; gesture.cancel(); dragging = PlaybackGesture.NONE; }

    private void initialize() {
        if (player != null) return;
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this).setEnableDecoderFallback(true);
        player = new ExoPlayer.Builder(this, renderers)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(MediaHttpDataSource.factory(url, headers))).build();
        player.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
        player.setHandleAudioBecomingNoisy(true);
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) { details(); }
            @Override public void onTracksChanged(Tracks tracks) { details(); }
            @Override public void onVideoSizeChanged(VideoSize size) { details(); }
            @Override public void onPlaybackParametersChanged(PlaybackParameters parameters) {
                speed = parameters.speed;
                speedButton.setText(PlaybackPolicy.SPEED_LABELS[PlaybackPolicy.speedIndex(speed)]);
            }
            @Override public void onPlayerError(PlaybackException error) {
                if (errorVisible || isFinishing()) return;
                errorVisible = true;
                new AlertDialog.Builder(PlayerActivity.this).setTitle("播放暂时中断")
                        .setMessage("错误：" + error.getErrorCodeName()
                                + "\n可能是链接过期、网盘限速、网络异常，或电视不支持此编码。返回文件库重新打开可获取新链接。")
                        .setPositiveButton("返回文件库", (a, b) -> finish())
                        .setNegativeButton("重试", (a, b) -> { errorVisible = false; player.prepare(); player.play(); })
                        .setOnCancelListener(x -> errorVisible = false).show();
            }
        });
        MediaItem.Builder item = new MediaItem.Builder().setUri(url);
        String lower = title == null ? "" : title.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".m3u8")) item.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8);
        if (lower.endsWith(".mpd")) item.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD);
        player.setMediaItem(item.build());
        player.setPlaybackSpeed(speed);
        player.seekTo(position); player.setPlayWhenReady(play); player.prepare();
    }

    private void details() {
        if (player == null) return;
        info.setText(VideoPresentation.details(player.getVideoFormat(), player.getAudioFormat()));
    }

    @Override protected void onStart() { super.onStart(); stopping = false; initialize(); }
    @Override protected void onStop() {
        stopping = true; consumedKeys.clear(); resetTouch(); clearLockHint();
        if (lockButton != null) updateLockButton();
        if (optionsMenu != null) optionsMenu.dismiss();
        if (player != null) {
            position = player.getCurrentPosition(); play = player.getPlayWhenReady();
            playerView.setPlayer(null); player.release(); player = null;
        }
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putFloat("brightness", windowBrightness);
        out.putBoolean("locked", locked); out.putFloat("speed", speed);
        out.putLong("position", player == null ? position : player.getCurrentPosition());
        out.putBoolean("play", player == null ? play : player.getPlayWhenReady()); super.onSaveInstanceState(out);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_UP && consumedKeys.remove(key)) {
            if (key == KeyEvent.KEYCODE_BACK && !event.isCanceled()) handleBack();
            return true;
        }
        if (playerView == null || event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        if (key == KeyEvent.KEYCODE_BACK) { consumedKeys.add(key); return true; }
        boolean confirm = key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER;
        boolean horizontal = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean vertical = key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN;
        if (locked && (confirm || horizontal || vertical
                || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || key == KeyEvent.KEYCODE_SPACE)) {
            showLockHint();
            consumedKeys.add(key); return true;
        }
        if (optionsMenu != null && optionsMenu.isShowing()) return super.dispatchKeyEvent(event);
        if (vertical && !input.navigate(controlsVisible)) {
            input.enterControls(); playerView.showController(); ratioButton.requestFocus();
            consumedKeys.add(key); return true;
        }
        if (!input.navigate(controlsVisible) && (confirm || horizontal) && player != null) {
            consumedKeys.add(key);
            if (confirm) {
                if (event.getRepeatCount() == 0) {
                    if (player.getPlayWhenReady()) player.pause();
                    else { if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0); player.play(); }
                    showFeedback(player.getPlayWhenReady() ? "播放" : "暂停");
                }
            } else if (player.isCurrentMediaItemSeekable()
                    && (event.getRepeatCount() == 0 || event.getEventTime() - lastSeekKeyTime > 180)) {
                lastSeekKeyTime = event.getEventTime();
                long target = PlaybackPolicy.seekTarget(player.getCurrentPosition(), player.getDuration(),
                        key == KeyEvent.KEYCODE_DPAD_LEFT ? -10000 : 10000);
                player.seekTo(target); showFeedback((key == KeyEvent.KEYCODE_DPAD_LEFT ? "−10 秒" : "+10 秒")
                        + "  ·  " + String.format(java.util.Locale.ROOT, "%02d:%02d", target / 60000, target / 1000 % 60));
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() { handleBack(); }

    private void handleBack() {
        if (playerView == null) { finish(); return; }
        switch (PlaybackInput.back(optionsMenu != null && optionsMenu.isShowing(), locked, controlsVisible)) {
            case CLOSE_MENU: optionsMenu.dismiss(); break;
            case LOCKED:
                showLockHint();
                break;
            case HIDE_CONTROLS:
                input.leaveControls(); playerView.hideController(); break;
            case EXIT: finish(); break;
        }
    }

    private void showFeedback(String label) {
        feedback.setText(label); feedback.setVisibility(View.VISIBLE);
        feedback.removeCallbacks(hideFeedback); feedback.postDelayed(hideFeedback, 800);
    }

    @Override protected void onDestroy() {
        resetTouch(); clearLockHint();
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        if (levelHud != null) levelHud.removeCallbacks(hideLevel);
        if (feedback != null) feedback.removeCallbacks(hideFeedback);
        super.onDestroy();
    }
}
