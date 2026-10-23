package site.openlist.player;

import java.net.URI;
import java.util.Locale;

final class Policy {
    private Policy() {}

    static String server(String input) {
        URI uri = URI.create(input.trim());
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new UiError(R.string.invalid_server);
        }
        String s = uri.toASCIIString();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    static boolean sameOrigin(String a, String b) {
        try {
            URI x = URI.create(a), y = URI.create(b);
            return x.getScheme().equalsIgnoreCase(y.getScheme())
                    && x.getHost().equalsIgnoreCase(y.getHost()) && port(x) == port(y);
        } catch (RuntimeException e) { return false; }
    }

    private static int port(URI u) {
        return u.getPort() >= 0 ? u.getPort() : "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
    }

    static String child(String path, String name) {
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/")) {
            throw new UiError(R.string.invalid_filename);
        }
        return (path.equals("/") ? "" : path) + "/" + name;
    }

    static String parent(String path) {
        int i = path.lastIndexOf('/');
        return i <= 0 ? "/" : path.substring(0, i);
    }

    static boolean video(String name) {
        return name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|m4v|webm|mov|avi|ts|m2ts|m3u8|mpd|flv|wmv)$");
    }

    static boolean mediaUrl(String url) {
        try {
            URI u = URI.create(url);
            return ("https".equalsIgnoreCase(u.getScheme()) || "http".equalsIgnoreCase(u.getScheme()))
                    && u.getHost() != null && u.getUserInfo() == null;
        } catch (RuntimeException e) { return false; }
    }
}
