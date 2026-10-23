package site.openlist.player;

import java.util.Locale;

final class FilePresentation {
    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1 || name.length() - dot > 9) return "FILE";
        return name.substring(dot + 1).toUpperCase(Locale.ROOT);
    }

    static String size(long bytes) {
        if (bytes < 1024) return Math.max(0, bytes) + " B";
        if (bytes < 1048576L) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824L) return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
        return String.format(Locale.ROOT, "%.1f GB", bytes / 1073741824.0);
    }

    static String title(String path) {
        if (path.equals("/")) return "文件库";
        int end = path.endsWith("/") ? path.length() - 1 : path.length();
        return path.substring(path.lastIndexOf('/', end - 1) + 1, end);
    }
}
