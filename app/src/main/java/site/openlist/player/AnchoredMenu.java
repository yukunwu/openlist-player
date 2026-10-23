package site.openlist.player;

import android.app.Activity;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** Focusable popup shared by file-row long press and playback options. */
final class AnchoredMenu {
    interface Selection { void choose(int index); }

    static PopupWindow show(Activity activity, View anchor, String[] labels, int selected,
            Selection selection, Runnable dismissed) {
        int pad = TvStyle.dp(activity, 6), rowHeight = TvStyle.dp(activity, 44);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(pad, pad, pad, pad);
        Rect visible = new Rect(); anchor.getWindowVisibleDisplayFrame(visible);
        int width = Math.min(TvStyle.dp(activity, 244), visible.width() - pad * 2);
        rowHeight = Math.min(rowHeight, Math.max(TvStyle.dp(activity, 32), (visible.height() - pad * 4) / labels.length));
        PopupWindow popup = new PopupWindow(panel, width, -2, true);
        popup.setBackgroundDrawable(TvStyle.browserBox(TvStyle.MENU_SURFACE, TvStyle.MENU_BORDER));
        popup.setElevation(TvStyle.dp(activity, 12)); popup.setOutsideTouchable(true);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = TvStyle.text(activity, (i == selected ? "✓  " : "    ") + labels[i], 14, TvStyle.TEXT);
            item.setContentDescription(labels[i]);
            item.setGravity(Gravity.CENTER_VERTICAL); item.setPadding(pad * 2, 0, pad * 2, 0);
            item.setBackground(TvStyle.browserFocus(true, i == selected));
            item.setFocusable(true); item.setClickable(true);
            item.setOnKeyListener((v, key, event) -> {
                if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
                if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        int next = (index + (key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : labels.length - 1)) % labels.length;
                        panel.getChildAt(next).requestFocus();
                    }
                    return true;
                }
                return false;
            });
            item.setOnClickListener(v -> { popup.dismiss(); selection.choose(index); });
            panel.addView(item, new LinearLayout.LayoutParams(-1, rowHeight));
        }
        popup.setOnDismissListener(() -> {
            if (anchor.isAttachedToWindow()) anchor.requestFocus();
            if (dismissed != null) dismissed.run();
        });
        int[] point = new int[2]; anchor.getLocationOnScreen(point);
        int height = labels.length * rowHeight + pad * 2;
        int y = point[1] + anchor.getHeight() + pad;
        if (y + height > visible.bottom) y = point[1] - height - pad;
        int x = Math.max(visible.left + pad, Math.min(point[0] + anchor.getWidth() - width, visible.right - width - pad));
        popup.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT, x, Math.max(visible.top + pad, y));
        panel.getChildAt(Math.max(0, selected)).requestFocus();
        return popup;
    }
}
