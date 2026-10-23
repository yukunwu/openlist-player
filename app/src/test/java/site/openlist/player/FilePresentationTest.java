package site.openlist.player;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FilePresentationTest {
    @Test public void extensionsHandleHiddenAndExtensionlessFiles() {
        assertEquals("MKV", FilePresentation.extension("movie.2026.mkv"));
        assertEquals("FILE", FilePresentation.extension(".hidden"));
        assertEquals("FILE", FilePresentation.extension("no-extension"));
        assertEquals("FILE", FilePresentation.extension("trailing."));
    }

    @Test public void sizesDoNotRoundSmallFilesDownToZeroMegabytes() {
        assertEquals("0 B", FilePresentation.size(-1));
        assertEquals("512 B", FilePresentation.size(512));
        assertEquals("1.0 KB", FilePresentation.size(1024));
        assertEquals("1.0 MB", FilePresentation.size(1048576));
        assertEquals("1.5 GB", FilePresentation.size(1610612736));
    }

    @Test public void headingUsesOnlyCurrentDirectory() {
        assertEquals("", FilePresentation.title("/"));
        assertEquals("Movies", FilePresentation.title("/Drive/Movies"));
        assertEquals("Movies", FilePresentation.title("/Drive/Movies/"));
    }
}
