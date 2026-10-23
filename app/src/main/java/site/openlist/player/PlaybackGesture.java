package site.openlist.player;

/** Side swipes adjust levels immediately; a moved stream must never become a player click. */
final class PlaybackGesture {
    static final int NONE = 0, BRIGHTNESS = 1, VOLUME = 2;
    private int candidate, active;
    private float startX, startY;
    private boolean moved;

    void begin(float x, float y, float width, long time, boolean eligible) {
        startX = x; startY = y; active = NONE; moved = false;
        candidate = !eligible ? NONE : x < width * .45f ? BRIGHTNESS : x > width * .55f ? VOLUME : NONE;
    }

    int move(float x, float y, long time, int slop, int longPressMs) {
        if (active != NONE) return active;
        float dx = Math.abs(x - startX), dy = Math.abs(y - startY);
        if (Math.max(dx, dy) > slop) moved = true;
        if (dx > slop && dx >= dy) candidate = NONE;
        if (candidate != NONE && dy > slop && dy > dx) active = candidate;
        return active;
    }

    boolean isTap() { return !moved && active == NONE; }

    float delta(float y, float height) { return (startY - y) / Math.max(1f, height * .7f); }
    void cancel() { candidate = NONE; active = NONE; moved = true; }
}
