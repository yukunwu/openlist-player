package site.openlist.player;

import androidx.media3.common.Format;
import java.util.Locale;

final class VideoPresentation {
    static final int ORIGINAL = 0, FOUR_THREE = 1, SIXTEEN_NINE = 2, STRETCH = 3;
    static String[] labels(android.content.Context context) {
        return new String[]{context.getString(R.string.original_ratio), "4:3", "16:9", context.getString(R.string.stretch_ratio)};
    }

    static int validMode(int mode) { return mode >= 0 && mode < 4 ? mode : ORIGINAL; }

    static float aspect(int mode, float original) {
        if (mode == FOUR_THREE) return 4f / 3f;
        if (mode == SIXTEEN_NINE) return 16f / 9f;
        return original;
    }

    static float brightness(float value) { return Math.max(.02f, Math.min(1f, value)); }

    static String details(Format video, Format audio, String loading, String channels) {
        StringBuilder out = new StringBuilder();
        if (video != null) {
            if (video.width > 0 && video.height > 0) out.append(video.width).append(" × ").append(video.height);
            append(out, codec(video.sampleMimeType));
            if (video.frameRate > 0) append(out, String.format(Locale.ROOT, "%.1f fps", video.frameRate));
        }
        if (audio != null) {
            String sound = codec(audio.sampleMimeType);
            if (audio.channelCount > 0) sound += (sound.isEmpty() ? "" : " / ")
                    + String.format(Locale.getDefault(), channels, audio.channelCount);
            append(out, sound);
        }
        return out.length() == 0 ? loading : out.toString();
    }

    private static void append(StringBuilder text, String item) {
        if (item.isEmpty()) return;
        if (text.length() > 0) text.append("  ·  ");
        text.append(item);
    }

    private static String codec(String mime) {
        if (mime == null) return "";
        switch (mime) {
            case "video/avc": return "H.264";
            case "video/hevc": return "HEVC";
            case "video/av01": return "AV1";
            case "video/x-vnd.on2.vp9": return "VP9";
            case "audio/mp4a-latm": return "AAC";
            case "audio/ac3": return "AC-3";
            case "audio/eac3": return "E-AC-3";
            default: return mime.substring(mime.indexOf('/') + 1).toUpperCase(Locale.ROOT);
        }
    }
}
