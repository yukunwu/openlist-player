package site.openlist.player;

/** Pure navigation policy shared by legacy Back and Android's gesture-back callback. */
final class PlaybackInput {
    enum Back { CLOSE_MENU, LOCKED, HIDE_CONTROLS, EXIT }
    private boolean navigating;

    void enterControls() { navigating = true; }
    void leaveControls() { navigating = false; }
    boolean navigate(boolean controlsVisible) { return navigating && controlsVisible; }

    static Back back(boolean menu, boolean locked, boolean controlsVisible) {
        if (menu) return Back.CLOSE_MENU;
        if (locked) return Back.LOCKED;
        return controlsVisible ? Back.HIDE_CONTROLS : Back.EXIT;
    }
}
