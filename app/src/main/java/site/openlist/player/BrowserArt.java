package site.openlist.player;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Small vector artwork, independent of fonts and Android system icon versions. */
final class BrowserArt extends Drawable {
    static final int LIBRARY = 0, FOLDER = 1, VIDEO = 2, FILE = 3, UP = 4,
            REFRESH = 5, SERVER = 6, CHEVRON = 7, MARK = 8, LOCK = 9, UNLOCK = 10;
    private final int kind, color;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    BrowserArt(int kind, int color) { this.kind = kind; this.color = color; }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        canvas.save();
        canvas.translate(b.left, b.top);
        canvas.scale(b.width() / 24f, b.height() / 24f);
        paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.65f);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        Path p = new Path();
        switch (kind) {
            case LOCK:
            case UNLOCK:
                canvas.drawRoundRect(5, 10, 19, 21, 2, 2, paint);
                if (kind == LOCK) {
                    p.moveTo(8, 10); p.lineTo(8, 7); p.cubicTo(8, 2, 16, 2, 16, 7); p.lineTo(16, 10);
                } else {
                    p.moveTo(8, 10); p.lineTo(8, 6); p.cubicTo(8, 1, 18, 1, 18, 6); p.lineTo(18, 7);
                }
                canvas.drawPath(p, paint);
                canvas.drawLine(12, 14, 12, 17, paint); break;
            case MARK:
                canvas.drawRoundRect(3, 4, 21, 20, 5, 5, paint);
                p.moveTo(10, 8); p.lineTo(16, 12); p.lineTo(10, 16); p.close();
                paint.setStyle(Paint.Style.FILL); canvas.drawPath(p, paint); break;
            case LIBRARY:
                canvas.drawRoundRect(3, 4, 10, 11, 1.5f, 1.5f, paint);
                canvas.drawRoundRect(14, 4, 21, 11, 1.5f, 1.5f, paint);
                canvas.drawRoundRect(3, 15, 10, 22, 1.5f, 1.5f, paint);
                canvas.drawRoundRect(14, 15, 21, 22, 1.5f, 1.5f, paint); break;
            case FOLDER:
                p.moveTo(3, 7); p.lineTo(3, 18); p.quadTo(3, 20, 5, 20);
                p.lineTo(19, 20); p.quadTo(21, 20, 21, 18); p.lineTo(21, 9);
                p.quadTo(21, 7, 19, 7); p.lineTo(11, 7); p.lineTo(9, 4);
                p.lineTo(5, 4); p.quadTo(3, 4, 3, 7); p.close();
                canvas.drawPath(p, paint); canvas.drawLine(3, 10, 21, 10, paint); break;
            case VIDEO:
                canvas.drawRoundRect(3, 3, 21, 21, 4, 4, paint);
                p.moveTo(10, 8); p.lineTo(16, 12); p.lineTo(10, 16); p.close();
                paint.setStyle(Paint.Style.FILL); canvas.drawPath(p, paint); break;
            case FILE:
                p.moveTo(14, 3); p.lineTo(6, 3); p.lineTo(6, 21); p.lineTo(19, 21);
                p.lineTo(19, 8); p.lineTo(14, 3); p.lineTo(14, 8); p.lineTo(19, 8);
                canvas.drawPath(p, paint); canvas.drawLine(9, 13, 15, 13, paint);
                canvas.drawLine(9, 17, 14, 17, paint); break;
            case UP:
                p.moveTo(5, 10); p.lineTo(11, 4); p.lineTo(17, 10);
                p.moveTo(11, 4); p.lineTo(11, 15); p.quadTo(11, 20, 20, 20);
                canvas.drawPath(p, paint); break;
            case REFRESH:
                canvas.drawArc(4, 4, 20, 20, 35, 285, false, paint);
                p.moveTo(20, 4); p.lineTo(20, 10); p.lineTo(14, 10); canvas.drawPath(p, paint); break;
            case SERVER:
                canvas.drawRoundRect(3, 3, 21, 10, 2, 2, paint);
                canvas.drawRoundRect(3, 14, 21, 21, 2, 2, paint);
                canvas.drawPoint(7, 6.5f, paint); canvas.drawPoint(7, 17.5f, paint);
                canvas.drawLine(12, 6.5f, 17, 6.5f, paint);
                canvas.drawLine(12, 17.5f, 17, 17.5f, paint); break;
            case CHEVRON:
                p.moveTo(9, 6); p.lineTo(15, 12); p.lineTo(9, 18); canvas.drawPath(p, paint); break;
            default: break;
        }
        canvas.restore();
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    static final class Backdrop extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            paint.setShader(new LinearGradient(0, 0, b.right, b.bottom,
                    new int[]{0xff232323, 0xff141414, 0xff090909}, null, Shader.TileMode.CLAMP));
            c.drawRect(b, paint); paint.setShader(null);
            paint.setColor(0x04ffffff);
            c.drawCircle(b.right * .88f, -b.height() * .25f, b.height() * .85f, paint);
            paint.setColor(0x02ffffff);
            c.drawCircle(b.right, b.height() * .08f, b.height() * .7f, paint);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}
