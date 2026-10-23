package site.openlist.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackInputTest {
    @Test public void backClosesOnlyTopmostLayer() {
        assertEquals(PlaybackInput.Back.CLOSE_MENU, PlaybackInput.back(true, false, true));
        assertEquals(PlaybackInput.Back.HIDE_CONTROLS, PlaybackInput.back(false, false, true));
        assertEquals(PlaybackInput.Back.EXIT, PlaybackInput.back(false, false, false));
    }

    @Test public void lockedBackNeverExitsOrRevealsControls() {
        assertEquals(PlaybackInput.Back.LOCKED, PlaybackInput.back(false, true, false));
        assertEquals(PlaybackInput.Back.LOCKED, PlaybackInput.back(false, true, true));
    }

    @Test public void visibleControllerAloneDoesNotChangeRemoteTransportMode() {
        PlaybackInput input = new PlaybackInput();
        assertFalse(input.navigate(true));
        input.enterControls(); assertTrue(input.navigate(true));
        input.leaveControls(); assertFalse(input.navigate(true));
    }

    @Test public void hiddenControlsCannotTrapRemoteNavigation() {
        PlaybackInput input = new PlaybackInput();
        input.enterControls(); assertFalse(input.navigate(false));
    }
}
