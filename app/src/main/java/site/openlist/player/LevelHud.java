package site.openlist.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** An unfilled overlay: only the translucent track, fill and level are drawn over video. */
final class LevelHud extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float level;
    private String label = "";

    LevelHud(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }

    void setLevel(String label, float level) {
        this.label = label; this.level = Math.max(0, Math.min(1, level)); invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float scale = getResources().getDisplayMetrics().density;
        float center = getWidth() / 2f, top = 30 * scale, bottom = getHeight() - 28 * scale;
        paint.setShadowLayer(3 * scale, 0, scale, 0x90000000);
        paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(12 * scale); paint.setColor(0xddffffff);
        canvas.drawText(label, center, 17 * scale, paint);
        canvas.drawText(Math.round(level * 100) + "%", center, getHeight() - 8 * scale, paint);
        paint.clearShadowLayer(); paint.setColor(0x55ffffff);
        canvas.drawRoundRect(center - 2 * scale, top, center + 2 * scale, bottom, 2 * scale, 2 * scale, paint);
        paint.setColor(0xeeffffff);
        canvas.drawRoundRect(center - 2 * scale, bottom - (bottom - top) * level,
                center + 2 * scale, bottom, 2 * scale, 2 * scale, paint);
    }
}
