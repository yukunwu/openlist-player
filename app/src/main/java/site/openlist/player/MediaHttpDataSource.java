package site.openlist.player;

import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import android.net.Uri;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Media redirects never carry credentials to a different origin. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class MediaHttpDataSource extends BaseDataSource {
    private final Map<String, String> headers;
    private final String headerOrigin;
    private HttpURLConnection connection;
    private InputStream stream;
    private Uri uri;
    private long remaining;
    private boolean opened;

    static DataSource.Factory factory(String url, Map<String, String> headers) {
        return () -> new MediaHttpDataSource(url, headers);
    }

    private MediaHttpDataSource(String origin, Map<String, String> headers) {
        super(true);
        headerOrigin = origin;
        this.headers = new HashMap<>(headers);
    }

    @Override public long open(DataSpec spec) throws IOException {
        transferInitializing(spec);
        String target = spec.uri.toString();
        if (!Policy.mediaUrl(target)) throw new IOException("Unsupported media URL");
        try {
            for (int hop = 0; hop < 8; hop++) {
                connection = (HttpURLConnection) new URL(target).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "OpenListTV/0.1 Media3");
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (Policy.sameOrigin(headerOrigin, target)) {
                    for (Map.Entry<String, String> e : headers.entrySet()) {
                        String k = e.getKey();
                        if (!k.equalsIgnoreCase("Host") && !k.equalsIgnoreCase("Range")
                                && !k.equalsIgnoreCase("Accept-Encoding")
                                && !k.equalsIgnoreCase("Connection") && !k.equalsIgnoreCase("Content-Length")) {
                            connection.setRequestProperty(k, e.getValue());
                        }
                    }
                }
                if (spec.position != 0 || spec.length != C.LENGTH_UNSET) {
                    String range = "bytes=" + spec.position + "-";
                    if (spec.length != C.LENGTH_UNSET) range += spec.position + spec.length - 1;
                    connection.setRequestProperty("Range", range);
                }
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) throw new IOException("Redirect without destination");
                    String next = new URL(new URL(target), location).toString();
                    if (!Policy.mediaUrl(next) || "https".equalsIgnoreCase(new URL(target).getProtocol())
                            && !"https".equalsIgnoreCase(new URL(next).getProtocol())) {
                        throw new IOException("Unsafe media redirect");
                    }
                    connection.disconnect(); connection = null; target = next;
                    continue;
                }
                if (code == 416 && HttpRange.atEnd(connection.getHeaderField("Content-Range"), spec.position)) {
                    uri = Uri.parse(target); remaining = 0; opened = true;
                    transferStarted(spec); return 0;
                }
                if (code != 200 && code != 206) throw new IOException("Media HTTP " + code);
                uri = Uri.parse(target);
                long length = -1;
                try { length = Long.parseLong(connection.getHeaderField("Content-Length")); }
                catch (Exception ignored) { }
                if (code == 206) length = HttpRange.partialLength(connection.getHeaderField("Content-Range"), spec.position, length);
                long skip = code == 200 ? spec.position : 0;
                if (length >= 0 && skip > length) throw new EOFException();
                remaining = length >= 0 ? length - skip : C.LENGTH_UNSET;
                if (spec.length != C.LENGTH_UNSET) remaining = remaining == C.LENGTH_UNSET
                        ? spec.length : Math.min(remaining, spec.length);
                stream = connection.getInputStream();
                // Some origins ignore Range; never deliver bytes from the wrong position.
                byte[] buffer = new byte[8192];
                while (skip > 0) {
                    int n = stream.read(buffer, 0, (int) Math.min(buffer.length, skip));
                    if (n == -1) throw new EOFException();
                    skip -= n;
                }
                if (remaining < 0 && remaining != C.LENGTH_UNSET) throw new EOFException();
                opened = true;
                transferStarted(spec);
                return remaining;
            }
            throw new IOException("Too many media redirects");
        } catch (IOException | RuntimeException e) { close(); throw e; }
    }

    @Override public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) return 0;
        if (remaining == 0) return C.RESULT_END_OF_INPUT;
        int n = stream.read(buffer, offset, remaining == C.LENGTH_UNSET ? length : (int) Math.min(length, remaining));
        if (n == -1) {
            if (remaining != C.LENGTH_UNSET && remaining > 0) throw new EOFException();
            return C.RESULT_END_OF_INPUT;
        }
        if (remaining != C.LENGTH_UNSET) remaining -= n;
        bytesTransferred(n);
        return n;
    }

    @Override public Uri getUri() { return uri; }
    @Override public Map<String, List<String>> getResponseHeaders() {
        return connection == null ? Collections.emptyMap() : connection.getHeaderFields();
    }
    @Override public void close() throws IOException {
        try { if (stream != null) stream.close(); }
        finally {
            stream = null;
            if (connection != null) connection.disconnect();
            connection = null; uri = null;
            if (opened) { opened = false; transferEnded(); }
        }
    }
}
