package site.openlist.player;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.media3.ui.AspectRatioFrameLayout;
import java.util.List;

/** Device smoke test; supply a synthetic HTTP video URL through -e mediaUrl. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlayerUiInstrumentation extends Instrumentation {
    private String mediaUrl;
    private Activity activity;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mediaUrl = arguments == null ? null : arguments.getString("mediaUrl"); start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check(mediaUrl != null, "Supply -e mediaUrl with a local synthetic test-video URL");
            int systemBrightness = Settings.System.getInt(getTargetContext().getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
            Intent intent = new Intent().setClassName(getTargetContext(), "site.openlist.player.PlayerActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("url", mediaUrl).putExtra("title", "Synthetic UI test.mp4");
            activity = startActivitySync(intent); waitForIdleSync();
            TextView info = null;
            for (int i = 0; i < 100; i++) {
                info = findText(activity.getWindow().getDecorView(), "640 × 360");
                if (info != null) break;
                SystemClock.sleep(100);
            }
            check(info != null, "Synthetic video did not decode to 640 x 360");
            check(!info.getText().toString().contains("确认"), "Metadata contains operation instructions");
            VideoPlayerView player = findPlayer(activity.getWindow().getDecorView());
            check(player != null, "Player not found");
            for (String mode : new String[]{"4:3", "16:9", activity.getString(R.string.stretch_ratio), activity.getString(R.string.original_ratio)}) {
                runOnMainSync(player::showController); waitForIdleSync();
                click(VideoPresentation.labels(activity)[VideoPresentation.validMode(activity.getPreferences(0).getInt("video_mode", 0))]);
                clickNode(mode); SystemClock.sleep(350); waitForIdleSync();
                AspectRatioFrameLayout frame = activity.findViewById(androidx.media3.ui.R.id.exo_content_frame);
                float actual = frame.getWidth() / (float) frame.getHeight();
                float expected = mode.equals("4:3") ? 4f / 3f : mode.equals(activity.getString(R.string.stretch_ratio))
                        ? player.getWidth() / (float) player.getHeight() : 16f / 9f;
                check(Math.abs(actual - expected) < .02f, "Incorrect measured ratio for " + mode);
            }
            click("1×"); clickNode("1.5×");
            check(findText(activity.getWindow().getDecorView(), "1.5×") != null, "Speed selection not reflected");
            click("1.5×"); clickNode("1×");
            runOnMainSync(player::showController); SystemClock.sleep(500);
            float x = player.getWidth() * .27f, y = player.getHeight() * .4f;
            float before = activity.getWindow().getAttributes().screenBrightness;
            long down = SystemClock.uptimeMillis();
            pointer(down, MotionEvent.ACTION_DOWN, x, y);
            for (int i = 1; i <= 5; i++) { SystemClock.sleep(30); pointer(down, MotionEvent.ACTION_MOVE, x, y - player.getHeight() * .04f * i); }
            pointer(down, MotionEvent.ACTION_UP, x, y - player.getHeight() * .2f);
            waitForIdleSync();
            check(activity.getWindow().getAttributes().screenBrightness >= .02f
                    && activity.getWindow().getAttributes().screenBrightness != before, "Immediate brightness swipe failed");
            check(systemBrightness == Settings.System.getInt(getTargetContext().getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128), "System brightness was modified");
            runOnMainSync(player::showController); waitForIdleSync(); clickDescription(activity.getString(R.string.lock));
            View lock = findDescription(activity.getWindow().getDecorView(), activity.getString(R.string.unlock));
            check(lock != null && lock.isShown(), "Lock confirmation icon missing");
            int[] lockPoint = new int[2];
            runOnMainSync(() -> {
                lock.getLocationOnScreen(lockPoint);
                lockPoint[0] += lock.getWidth() / 2; lockPoint[1] += lock.getHeight() / 2;
            });
            SystemClock.sleep(2800); waitForIdleSync();
            check(!lock.isShown(), "Locked icon stayed visible while idle");
            down = SystemClock.uptimeMillis();
            pointer(down, MotionEvent.ACTION_DOWN, lockPoint[0], lockPoint[1]);
            pointer(down, MotionEvent.ACTION_UP, lockPoint[0], lockPoint[1]);
            waitForIdleSync();
            check(lock.isShown() && !player.getUseController(), "Hidden icon tap accidentally unlocked");
            SystemClock.sleep(2800); waitForIdleSync();
            check(!lock.isShown(), "Revealed icon did not auto-hide");
            runOnMainSync(activity::onBackPressed); waitForIdleSync();
            check(lock.isShown() && !activity.isFinishing(), "Locked Back did not reveal icon");
            down = SystemClock.uptimeMillis();
            pointer(down, MotionEvent.ACTION_DOWN, player.getWidth() * .6f, y);
            pointer(down, MotionEvent.ACTION_UP, player.getWidth() * .6f, y);
            check(!player.isControllerFullyVisible() && !player.getUseController(), "Locked touch revealed controls");
            down = SystemClock.uptimeMillis();
            pointer(down, MotionEvent.ACTION_DOWN, lockPoint[0], lockPoint[1]);
            SystemClock.sleep(2800); waitForIdleSync();
            check(lock.isShown(), "Unlock target disappeared while pressed");
            pointer(down, MotionEvent.ACTION_UP, lockPoint[0], lockPoint[1]);
            waitForIdleSync();
            check(player.getUseController(), "Visible lock icon did not unlock");
            runOnMainSync(player::hideController); SystemClock.sleep(400);
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
            runOnMainSync(() -> check(!player.getPlayer().getPlayWhenReady(), "Remote confirm did not pause"));
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
            runOnMainSync(() -> check(player.getPlayer().getPlayWhenReady(), "Remote confirm did not resume"));
            long[] start = new long[1]; runOnMainSync(() -> start[0] = player.getPlayer().getCurrentPosition());
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); waitForIdleSync();
            runOnMainSync(() -> check(player.getPlayer().getCurrentPosition() >= start[0] + 9000, "Remote right did not seek"));
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); waitForIdleSync();
            check(player.isControllerFullyVisible(), "Remote down did not reveal controls");
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync();
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync();
            check(!activity.isFinishing() && player.isControllerFullyVisible(), "Popup Back did not return to controls");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync();
            check(!activity.isFinishing() && !player.isControllerFullyVisible(), "Controller Back exited instead of hiding");
            runOnMainSync(player::showController);
            runOnMainSync(activity::onBackPressed); waitForIdleSync();
            check(!activity.isFinishing() && !player.isControllerFullyVisible(), "System Back differs from key Back");
            runOnMainSync(activity::onBackPressed); waitForIdleSync();
            check(activity.isFinishing(), "Final Back did not exit player");
            result.putString("stream", "PASS: metadata, ratios, speed, brightness, lock, remote transport and layered Back\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + error + "\n"); finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void pointer(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN); sendPointerSync(event); event.recycle();
    }

    private void click(String prefix) {
        runOnMainSync(() -> {
            TextView button = findText(activity.getWindow().getDecorView(), prefix);
            check(button != null, "Missing control: " + prefix); button.performClick();
        });
        waitForIdleSync(); SystemClock.sleep(250);
    }

    private void clickNode(String text) {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        check(root != null, "No dialog accessibility root");
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        for (AccessibilityNodeInfo node : nodes) {
            if (!text.contentEquals(node.getContentDescription())
                    && (node.getText() == null || !text.equals(node.getText().toString().replace("✓", "").trim()))) continue;
            while (node != null) {
                if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    waitForIdleSync(); SystemClock.sleep(200); return;
                }
                node = node.getParent();
            }
        }
        throw new AssertionError("Cannot click dialog item: " + text);
    }

    private void clickDescription(String description) {
        runOnMainSync(() -> {
            View control = findDescription(activity.getWindow().getDecorView(), description);
            check(control != null, "Missing control: " + description); control.performClick();
        }); waitForIdleSync();
    }

    private static View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = findDescription(((ViewGroup) view).getChildAt(i), description); if (found != null) return found;
        }
        return null;
    }

    private static TextView findText(View view, String prefix) {
        if (view instanceof TextView && ((TextView) view).getText().toString().startsWith(prefix)) return (TextView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            TextView found = findText(((ViewGroup) view).getChildAt(i), prefix); if (found != null) return found;
        }
        return null;
    }

    private static VideoPlayerView findPlayer(View view) {
        if (view instanceof VideoPlayerView) return (VideoPlayerView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            VideoPlayerView found = findPlayer(((ViewGroup) view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }

    private static AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String className) {
        if (node == null) return null;
        if (className.contentEquals(node.getClassName())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findNode(node.getChild(i), className); if (found != null) return found;
        }
        return null;
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
