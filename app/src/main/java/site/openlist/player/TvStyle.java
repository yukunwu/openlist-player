package site.openlist.player;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

final class TvStyle {
    static final int INK = 0xff101010;
    static final int PANEL = 0xb0242424;
    static final int TEXT = 0xfff5f5f5;
    static final int MUTED = 0xffb3b3b3;
    static final int ACCENT = 0xffe50914;
    static final int FOCUS = 0xffeeeeee;
    static final int BROWSER_TEXT = TEXT;
    static final int BROWSER_MUTED = MUTED;
    static final int MENU_SURFACE = 0xe61a1a1a;
    static final int MENU_BORDER = 0x40ffffff;

    static StateListDrawable browserFocus(boolean rail, boolean selected) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, browserBox(0x403d3d3d, FOCUS));
        states.addState(new int[]{android.R.attr.state_pressed}, browserBox(0x66505050, FOCUS));
        states.addState(new int[]{}, browserBox(selected ? 0x24ffffff : rail ? 0x00000000 : 0x0dffffff, 0));
        return states;
    }

    static GradientDrawable browserBox(int color, int border) {
        GradientDrawable shape = box(color, 0);
        shape.setCornerRadius(12);
        if (border != 0) shape.setStroke(2, border);
        return shape;
    }

    static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable box(int color, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(14);
        if (border != 0) d.setStroke(3, border);
        return d;
    }

    static StateListDrawable focus() {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused}, box(0xc0404040, FOCUS));
        d.addState(new int[]{android.R.attr.state_pressed}, box(0xc0505050, FOCUS));
        d.addState(new int[]{}, box(PANEL, 0));
        return d;
    }

    static TextView text(Context c, String text, int size, int color) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    static Button button(Context c, String text, View.OnClickListener listener) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(TEXT);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setBackground(focus());
        b.setPadding(dp(c, 18), dp(c, 8), dp(c, 18), dp(c, 8));
        b.setMinimumHeight(dp(c, 48));
        b.setFocusable(true);
        b.setOnClickListener(listener);
        return b;
    }
}
