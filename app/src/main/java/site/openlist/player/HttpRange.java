package site.openlist.player;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks byte offsets before a partial response reaches the decoder. */
final class HttpRange {
    private static final Pattern PARTIAL = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+|\\*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNSATISFIED = Pattern.compile("bytes \\*/(\\d+)", Pattern.CASE_INSENSITIVE);

    static long partialLength(String value, long position, long contentLength) throws IOException {
        Matcher match = PARTIAL.matcher(value == null ? "" : value.trim());
        try {
            if (!match.matches()) throw new IOException("Missing or invalid Content-Range");
            long start = Long.parseLong(match.group(1)), end = Long.parseLong(match.group(2));
            if (start != position || end < start || end - start == Long.MAX_VALUE
                    || (!match.group(3).equals("*") && end >= Long.parseLong(match.group(3)))) {
                throw new IOException("Incorrect media range");
            }
            long length = end - start + 1;
            if (contentLength >= 0 && contentLength != length) throw new IOException("Inconsistent media length");
            return length;
        } catch (NumberFormatException e) { throw new IOException("Invalid media range", e); }
    }

    static boolean atEnd(String value, long position) {
        Matcher match = UNSATISFIED.matcher(value == null ? "" : value.trim());
        try { return match.matches() && position == Long.parseLong(match.group(1)); }
        catch (NumberFormatException e) { return false; }
    }
}
