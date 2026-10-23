package site.openlist.player;

import androidx.media3.common.Format;
import org.junit.Test;
import static org.junit.Assert.*;

public class VideoPresentationTest {
    @Test public void fixedRatiosOverrideSourceAndOriginalPreservesIt() {
        assertEquals(4f / 3f, VideoPresentation.aspect(VideoPresentation.FOUR_THREE, 2.35f), .0001f);
        assertEquals(16f / 9f, VideoPresentation.aspect(VideoPresentation.SIXTEEN_NINE, 2.35f), .0001f);
        assertEquals(2.35f, VideoPresentation.aspect(VideoPresentation.ORIGINAL, 2.35f), .0001f);
        assertEquals(VideoPresentation.ORIGINAL, VideoPresentation.validMode(99));
    }

    @Test public void brightnessNeverTurnsScreenOffOrExceedsMaximum() {
        assertEquals(.02f, VideoPresentation.brightness(-2f), .0001f);
        assertEquals(1f, VideoPresentation.brightness(2f), .0001f);
        assertEquals(.5f, VideoPresentation.brightness(.5f), .0001f);
    }

    @Test public void metadataContainsMediaDetailsWithoutInstructionsOrUnknownNumbers() {
        Format video = new Format.Builder().setWidth(3840).setHeight(2160)
                .setSampleMimeType("video/hevc").setFrameRate(23.976f).build();
        Format audio = new Format.Builder().setSampleMimeType("audio/ac3").setChannelCount(6).build();
        assertEquals("3840 × 2160  ·  HEVC  ·  24.0 fps  ·  AC-3 / 6 声道", VideoPresentation.details(video, audio, "正在载入视频信息", "%1$d 声道"));
        assertEquals("正在载入视频信息", VideoPresentation.details(null, null, "正在载入视频信息", "%1$d 声道"));
        assertEquals("H.264", VideoPresentation.details(new Format.Builder().setSampleMimeType("video/avc").build(), null, "Loading media details", "%1$d channels"));
    }
}
