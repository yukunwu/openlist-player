package site.openlist.player;

final class PlaybackPolicy {
    static final float[] SPEEDS = {.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
    static final String[] SPEED_LABELS = {"0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×"};

    static long seekTarget(long position, long duration, long delta) {
        long target = Math.max(0, position + delta);
        return duration > 0 ? Math.min(duration, target) : target;
    }

    static int speedIndex(float speed) {
        for (int i = 0; i < SPEEDS.length; i++) if (Math.abs(speed - SPEEDS[i]) < .01f) return i;
        return 2;
    }
}
