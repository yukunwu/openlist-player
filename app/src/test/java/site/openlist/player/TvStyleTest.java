package site.openlist.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class TvStyleTest {
    @Test public void corePaletteIsNeutralExceptRedAccent() {
        for (int color : new int[]{TvStyle.INK, TvStyle.PANEL, TvStyle.TEXT, TvStyle.MUTED,
                TvStyle.FOCUS, TvStyle.MENU_SURFACE, TvStyle.MENU_BORDER}) {
            assertEquals((color >> 16) & 255, (color >> 8) & 255);
            assertEquals((color >> 8) & 255, color & 255);
        }
        assertTrue(((TvStyle.ACCENT >> 16) & 255) > ((TvStyle.ACCENT >> 8) & 255));
    }

    @Test public void overlaysAreTranslucentButTextStaysOpaque() {
        assertTrue((TvStyle.MENU_SURFACE >>> 24) < 255);
        assertTrue((TvStyle.MENU_SURFACE >>> 24) >= 200);
        assertTrue((TvStyle.PANEL >>> 24) < 255);
        assertEquals(255, TvStyle.TEXT >>> 24);
    }
}
