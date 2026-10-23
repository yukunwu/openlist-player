package site.openlist.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class HttpRangeTest {
    @Test public void acceptsCorrectPartialResponses() throws Exception {
        assertEquals(100, HttpRange.partialLength("bytes 100-199/200", 100, 100));
        assertEquals(100, HttpRange.partialLength("bytes 100-199/*", 100, -1));
        assertEquals(1, HttpRange.partialLength("bytes 0-0/1", 0, 1));
    }

    @Test public void rejectsIncorrectOffsetsAndLengths() {
        for (String value : new String[]{null, "", "bytes 0-99/200", "bytes 100-99/200",
                "bytes 100-200/200", "bytes 100-199/999999999999999999999", "bytes 100-198/200"}) {
            try { HttpRange.partialLength(value, 100, 100); fail("Invalid range accepted"); }
            catch (java.io.IOException expected) { }
        }
    }

    @Test public void rejectsOverflow() {
        try { HttpRange.partialLength("bytes 0-9223372036854775807/*", 0, -1); fail(); }
        catch (java.io.IOException expected) { }
    }

    @Test public void onlyExactEofIsEmptySuccess() {
        assertTrue(HttpRange.atEnd("bytes */200", 200));
        assertFalse(HttpRange.atEnd("bytes */200", 201));
        assertFalse(HttpRange.atEnd("bytes */200", 199));
        assertFalse(HttpRange.atEnd(null, 200));
        assertFalse(HttpRange.atEnd("bytes */999999999999999999999", 200));
    }
}
