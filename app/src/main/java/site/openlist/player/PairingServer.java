package site.openlist.player;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/** Short-lived local form receiver; only authenticated ciphertext is accepted. */
final class PairingServer implements AutoCloseable {
    private final ServerSocket listener;
    private final byte[] key = PairingProtocol.random(32);
    private final String session = PairingProtocol.hex(PairingProtocol.random(16));
    private final String host;
    private final String origin;
    private final byte[] page, forge;
    interface Receiver { void accept(String text); }
    private final Receiver received;
    private final Runnable expired;
    private final long deadline;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean consumed;
    private Socket activeClient;
    private final Timer expiryTimer = new Timer("openlist-pairing-expiry", true);

    PairingServer(InetAddress address, byte[] page, byte[] forge, Receiver received, Runnable expired) throws Exception {
        this(address, page, forge, received, expired, 180000);
    }

    PairingServer(InetAddress address, byte[] page, byte[] forge, Receiver received, Runnable expired, long ttlMs) throws Exception {
        listener = new ServerSocket(0, 4, address);
        listener.setSoTimeout(1000);
        host = address.getHostAddress() + ":" + listener.getLocalPort();
        origin = "http://" + host;
        this.page = page; this.forge = forge; this.received = received; this.expired = expired;
        deadline = System.nanoTime() + ttlMs * 1000000L;
    }

    String url() { return origin + "/p/" + session + "/#k=" + PairingProtocol.hex(key); }
    String code() { return session.substring(0, 6).toUpperCase(java.util.Locale.ROOT); }

    void start() {
        expiryTimer.schedule(new TimerTask() {
            @Override public void run() { expire(); }
        }, Math.max(1, (deadline - System.nanoTime()) / 1000000L));
        Thread t = new Thread(this::serve, "openlist-pairing");
        t.setDaemon(true); t.start();
    }

    private void serve() {
        int count = 0;
        try {
            while (!closed.get() && !consumed && System.nanoTime() < deadline && count < 40) {
                try (Socket client = listener.accept()) {
                    synchronized (this) {
                        if (closed.get()) return;
                        activeClient = client;
                    }
                    count++;
                    client.setSoTimeout(2500);
                    try { handle(client); } catch (Exception ignored) { /* Never log pairing payloads. */ }
                    finally { synchronized (this) { activeClient = null; } }
                } catch (SocketTimeoutException ignored) { }
            }
        } catch (Exception ignored) { }
        finally {
            expire();
        }
    }

    private void handle(Socket client) throws Exception {
        if (System.nanoTime() >= deadline) return;
        InputStream in = client.getInputStream();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int end = 0;
        while (head.size() < 8192 && end != 0x0d0a0d0a) {
            int b = in.read(); if (b == -1) return;
            head.write(b); end = (end << 8) | b;
        }
        if (end != 0x0d0a0d0a) { reply(client, 400, "text/plain", "Invalid request"); return; }
        String[] lines = new String(head.toByteArray(), StandardCharsets.US_ASCII).split("\r\n");
        String[] request = lines[0].split(" ");
        if (request.length != 3) return;
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int n = lines[i].indexOf(':');
            if (n > 0) {
                String name = lines[i].substring(0, n).toLowerCase(java.util.Locale.ROOT);
                if (headers.containsKey(name)) { reply(client, 400, "text/plain", "Duplicate header"); return; }
                headers.put(name, lines[i].substring(n + 1).trim());
            }
        }
        String base = "/p/" + session + "/";
        if (!host.equals(headers.get("host"))) { reply(client, 403, "text/plain", "Forbidden host"); return; }
        if (request[0].equals("GET") && request[1].equals(base)) {
            reply(client, 200, "text/html; charset=utf-8", page); return;
        }
        if (request[0].equals("GET") && request[1].equals(base + "forge.min.js")) {
            reply(client, 200, "application/javascript", forge); return;
        }
        if (!request[0].equals("POST") || !request[1].equals(base + "submit")) {
            reply(client, 404, "text/plain", "Not found"); return;
        }
        if (!origin.equals(headers.get("origin")) || headers.containsKey("transfer-encoding")
                || !"text/plain;charset=UTF-8".equalsIgnoreCase(headers.get("content-type"))) {
            reply(client, 403, "text/plain", "Forbidden origin"); return;
        }
        int length;
        try { length = Integer.parseInt(headers.get("content-length")); }
        catch (Exception e) { reply(client, 400, "text/plain", "Invalid length"); return; }
        if (length < 56 || length > 16384) { reply(client, 400, "text/plain", "Invalid length"); return; }
        byte[] body = new byte[length]; int at = 0;
        while (at < length) { int n = in.read(body, at, length - at); if (n == -1) return; at += n; }
        if (System.nanoTime() >= deadline || closed.get()) { reply(client, 410, "text/plain", "Expired"); return; }
        String plain;
        try {
            synchronized (this) {
                if (closed.get() || System.nanoTime() >= deadline) return;
                plain = PairingProtocol.decrypt(key, session, new String(body, StandardCharsets.US_ASCII));
                consumed = true;
            }
        }
        catch (Exception e) { reply(client, 403, "text/plain", "Invalid encrypted payload"); return; }
        reply(client, 200, "text/plain; charset=utf-8", "已发送，请回到电视确认。");
        received.accept(plain);
    }

    private void reply(Socket client, int code, String mime, String text) throws Exception {
        reply(client, code, mime, text.getBytes(StandardCharsets.UTF_8));
    }

    private void reply(Socket client, int code, String mime, byte[] bytes) throws Exception {
        String csp = mime.startsWith("text/html") ? "Content-Security-Policy: default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; form-action 'none'; frame-ancestors 'none'; base-uri 'none'\r\n" : "";
        String header = "HTTP/1.1 " + code + " Result\r\nContent-Type: " + mime + "\r\nContent-Length: " + bytes.length
                + "\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\n"
                + csp + "Connection: close\r\n\r\n";
        OutputStream out = client.getOutputStream();
        out.write(header.getBytes(StandardCharsets.US_ASCII)); out.write(bytes); out.flush();
    }

    private void expire() {
        boolean notify;
        synchronized (this) {
            notify = !consumed && !closed.get();
            close();
        }
        if (notify) expired.run();
    }

    @Override public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            expiryTimer.cancel();
            try { listener.close(); } catch (Exception ignored) { }
            try { if (activeClient != null) activeClient.close(); } catch (Exception ignored) { }
            Arrays.fill(key, (byte) 0);
        }
    }
}
