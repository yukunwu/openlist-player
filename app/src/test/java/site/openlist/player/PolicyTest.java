package site.openlist.player;

import org.junit.Test;
import static org.junit.Assert.*;

public class PolicyTest {
    @Test public void rejectsUnconfiguredServer() {
        for (String s : new String[]{"", " ", "\n\t"}) {
            try { Policy.server(s); fail("Blank server accepted"); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void normalizesServer() {
        assertEquals("https://example.com/openlist", Policy.server(" https://example.com/openlist/// "));
    }
    @Test public void rejectsCredentialsAndParams() {
        for (String s : new String[]{"https://user:pass@example.com", "file:///tmp/a", "https://example.com/?token=x", "example.com", "https://example.com/#x"}) {
            try { Policy.server(s); fail(s); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void originsRespectPortAndScheme() {
        assertTrue(Policy.sameOrigin("https://example.com", "https://EXAMPLE.com:443/a"));
        assertFalse(Policy.sameOrigin("https://example.com", "http://example.com"));
        assertFalse(Policy.sameOrigin("https://example.com", "https://example.com:444"));
        assertFalse(Policy.sameOrigin("https://example.com", "https://cdn.example.com"));
    }
    @Test public void pathsAreNotUrls() {
        assertEquals("/影视/影片 #1.mkv", Policy.child("/影视", "影片 #1.mkv"));
        assertEquals("/影视", Policy.parent("/影视/动画"));
        assertEquals("/", Policy.parent("/影视"));
        assertEquals("/", Policy.parent("/"));
    }
    @Test public void rejectsTraversal() {
        for (String s : new String[]{"..", ".", "a/b", ""}) {
            try { Policy.child("/", s); fail(s); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void videoExtensions() {
        assertTrue(Policy.video("MOVIE.MKV")); assertTrue(Policy.video("live.m3u8"));
        assertTrue(Policy.video("movie.m2ts")); assertFalse(Policy.video("movie.txt"));
    }
    @Test public void mediaUrlsRestricted() {
        assertTrue(Policy.mediaUrl("https://cdn.example.com/file?sign=abc"));
        assertFalse(Policy.mediaUrl("javascript:alert(1)"));
        assertFalse(Policy.mediaUrl("file:///sdcard/private"));
        assertFalse(Policy.mediaUrl("https://user:pass@cdn.example.com"));
    }
}
