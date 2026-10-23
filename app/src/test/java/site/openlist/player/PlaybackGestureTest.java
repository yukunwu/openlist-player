package site.openlist.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackGestureTest {
    @Test public void leftSwipeAdjustsImmediatelyWithoutHold() {
        PlaybackGesture g = new PlaybackGesture();
        g.begin(100, 300, 1000, 0, true);
        assertEquals(PlaybackGesture.BRIGHTNESS, g.move(100, 270, 20, 10, 500));
        assertFalse(g.isTap());
        assertTrue(g.delta(270, 500) > 0);
    }

    @Test public void onlyStationaryReleaseIsATap() {
        PlaybackGesture g = new PlaybackGesture();
        g.begin(100, 300, 1000, 0, true);
        g.move(102, 302, 20, 10, 500); assertTrue(g.isTap());
        g.move(180, 302, 50, 10, 500); assertFalse(g.isTap());
        assertEquals(PlaybackGesture.NONE, g.move(180, 100, 100, 10, 500));
    }

    @Test public void cancelledStreamNeverBecomesClick() {
        PlaybackGesture g = new PlaybackGesture();
        g.begin(800, 300, 1000, 0, true); g.cancel();
        assertFalse(g.isTap());
        assertEquals(PlaybackGesture.NONE, g.move(800, 100, 30, 10, 500));
    }

    @Test public void rightVolumeDoesNotRequireHoldAndRejectsHorizontalMotion() {
        PlaybackGesture g = new PlaybackGesture();
        g.begin(800, 300, 1000, 0, true);
        assertEquals(PlaybackGesture.VOLUME, g.move(800, 200, 100, 10, 500));
        g.begin(800, 300, 1000, 0, true);
        assertEquals(PlaybackGesture.NONE, g.move(850, 301, 100, 10, 500));
        assertEquals(PlaybackGesture.NONE, g.move(850, 200, 700, 10, 500));
    }

    @Test public void lockedOrControlAreaAndCancellationNeverClaimTouch() {
        PlaybackGesture g = new PlaybackGesture();
        g.begin(100, 300, 1000, 0, false);
        assertEquals(PlaybackGesture.NONE, g.move(100, 100, 800, 10, 500));
        g.begin(500, 300, 1000, 0, true);
        assertEquals(PlaybackGesture.NONE, g.move(500, 100, 800, 10, 500));
        g.begin(800, 300, 1000, 0, true); g.cancel();
        assertEquals(PlaybackGesture.NONE, g.move(800, 100, 800, 10, 500));
    }

    @Test public void remoteSeekClampsAtMediaBoundsAndSpeedDefaultsToNormal() {
        assertEquals(0, PlaybackPolicy.seekTarget(5000, 60000, -10000));
        assertEquals(60000, PlaybackPolicy.seekTarget(55000, 60000, 10000));
        assertEquals(65000, PlaybackPolicy.seekTarget(55000, -1, 10000));
        assertEquals(2, PlaybackPolicy.speedIndex(1));
        assertEquals(4, PlaybackPolicy.speedIndex(1.5f));
    }
}
