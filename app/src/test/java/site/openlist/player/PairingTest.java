package site.openlist.player;

import org.junit.Test;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class PairingTest {
    @Test public void slowConnectionCannotExtendDeadline() throws Exception {
        CountDownLatch expired = new CountDownLatch(1);
        try (PairingServer server = new PairingServer(InetAddress.getLoopbackAddress(), new byte[0], new byte[0],
                s -> fail("Unexpected payload"), expired::countDown, 250)) {
            server.start(); URL url = new URL(server.url());
            try (java.net.Socket peer = new java.net.Socket(url.getHost(), url.getPort())) {
                peer.setSoTimeout(1500);
                for (int i = 0; i < 8 && expired.getCount() != 0; i++) {
                    try { peer.getOutputStream().write('G'); peer.getOutputStream().flush(); }
                    catch (java.io.IOException closed) { break; }
                    Thread.sleep(50);
                }
                assertTrue("Absolute deadline not enforced", expired.await(1, TimeUnit.SECONDS));
                assertPeerClosed(peer);
            }
        }
    }

    @Test public void closeDisconnectsAcceptedClientWithoutExpiryNotification() throws Exception {
        CountDownLatch expired = new CountDownLatch(1);
        try (PairingServer server = new PairingServer(InetAddress.getLoopbackAddress(), new byte[0], new byte[0],
                s -> fail("Unexpected payload"), expired::countDown)) {
            server.start(); URL url = new URL(server.url());
            try (java.net.Socket peer = new java.net.Socket(url.getHost(), url.getPort())) {
                peer.setSoTimeout(1000);
                // A valid GET response proves this connection was accepted before close().
                String request = "GET " + url.getPath() + " HTTP/1.1\r\nHost: " + url.getAuthority() + "\r\n\r\n";
                peer.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                assertTrue(peer.getInputStream().read() >= 0);
            }
            try (java.net.Socket peer = new java.net.Socket(url.getHost(), url.getPort())) {
                peer.setSoTimeout(1000); peer.getOutputStream().write('G'); Thread.sleep(80);
                server.close(); assertPeerClosed(peer);
                assertEquals(1, expired.getCount());
            }
        }
    }

    private static void assertPeerClosed(java.net.Socket peer) throws Exception {
        try { assertEquals(-1, peer.getInputStream().read()); }
        catch (java.net.SocketException reset) { /* Closing an unread socket may reset it. */ }
    }

    @Test public void incompleteBodyExpiresWithoutDelivery() throws Exception {
        CountDownLatch expired = new CountDownLatch(1);
        try (PairingServer server = new PairingServer(InetAddress.getLoopbackAddress(), new byte[0], new byte[0],
                s -> fail("Unexpected payload"), expired::countDown, 300)) {
            server.start(); URL url = new URL(server.url());
            try (java.net.Socket peer = new java.net.Socket(url.getHost(), url.getPort())) {
                peer.setSoTimeout(1500);
                String request = "POST " + url.getPath() + "submit HTTP/1.1\r\nHost: " + url.getAuthority()
                        + "\r\nOrigin: http://" + url.getAuthority()
                        + "\r\nContent-Type: text/plain;charset=UTF-8\r\nContent-Length: 56\r\n\r\n00";
                peer.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                assertTrue(expired.await(1, TimeUnit.SECONDS)); assertPeerClosed(peer);
            }
        }
    }

    @Test public void blockedResponseWriterStillExpires() throws Exception {
        CountDownLatch expired = new CountDownLatch(1);
        try (PairingServer server = new PairingServer(InetAddress.getLoopbackAddress(), new byte[16 * 1024 * 1024], new byte[0],
                s -> fail("Unexpected payload"), expired::countDown, 300)) {
            server.start(); URL url = new URL(server.url());
            try (java.net.Socket peer = new java.net.Socket()) {
                peer.setReceiveBufferSize(1024);
                peer.connect(new java.net.InetSocketAddress(url.getHost(), url.getPort()));
                String request = "GET " + url.getPath() + " HTTP/1.1\r\nHost: " + url.getAuthority() + "\r\n\r\n";
                peer.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                assertTrue("Blocked output prevented expiry", expired.await(1, TimeUnit.SECONDS));
            }
        }
    }

    static String encrypt(byte[] key, String session, String plain) throws Exception {
        byte[] iv = PairingProtocol.random(12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        c.updateAAD(session.getBytes(StandardCharsets.UTF_8));
        return PairingProtocol.hex(iv) + PairingProtocol.hex(c.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    @Test public void roundtripUnicode() throws Exception {
        byte[] key = PairingProtocol.random(32);
        String wire = encrypt(key, "session", "影视账号 \"测试\" 😀");
        assertEquals("影视账号 \"测试\" 😀", PairingProtocol.decrypt(key, "session", wire));
        assertFalse(wire.contains("影视"));
    }

    @Test public void wrongSessionAndTamperRejected() throws Exception {
        byte[] key = PairingProtocol.random(32);
        String wire = encrypt(key, "session", "not-a-real-credential");
        try { PairingProtocol.decrypt(key, "different", wire); fail(); } catch (javax.crypto.AEADBadTagException expected) { }
        char flipped = wire.charAt(30) == '0' ? '1' : '0';
        String changed = wire.substring(0, 30) + flipped + wire.substring(31);
        try { PairingProtocol.decrypt(key, "session", changed); fail(); } catch (javax.crypto.AEADBadTagException expected) { }
    }

    @Test public void invalidHexRejected() {
        for (String s : new String[]{"g1", "a", "12-4"}) {
            try { PairingProtocol.unhex(s); fail(); } catch (IllegalArgumentException expected) { }
        }
    }

    static int send(String address, String origin, String body) throws Exception {
        URL u = new URL(address);
        try (java.net.Socket socket = new java.net.Socket(u.getHost(), u.getPort())) {
            socket.setSoTimeout(3000);
            byte[] bytes = body.getBytes(StandardCharsets.US_ASCII);
            String header = "POST " + u.getPath() + " HTTP/1.1\r\nHost: " + u.getAuthority()
                    + "\r\nOrigin: " + origin + "\r\nContent-Type: text/plain;charset=UTF-8\r\nContent-Length: "
                    + bytes.length + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(bytes); socket.getOutputStream().flush();
            String first = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            if (first == null) throw new java.io.IOException("Socket closed");
            return Integer.parseInt(first.split(" ")[1]);
        }
    }

    @Test public void serverAcceptsOnlyOneEncryptedPayload() throws Exception {
        AtomicReference<String> value = new AtomicReference<>(); CountDownLatch got = new CountDownLatch(1);
        try (PairingServer server = new PairingServer(InetAddress.getByName("127.0.0.1"),
                "page".getBytes(StandardCharsets.UTF_8), new byte[0], s -> { value.set(s); got.countDown(); }, () -> {})) {
            server.start();
            String url = server.url(), session = new URL(url).getPath().split("/")[2];
            String key = url.substring(url.indexOf("#k=") + 3);
            String origin = url.substring(0, url.indexOf("/p/"));
            String endpoint = origin + "/p/" + session + "/submit";
            assertEquals(403, send(endpoint, "http://attacker.invalid", encrypt(PairingProtocol.unhex(key), session, "ignored")));
            assertEquals(403, send(endpoint, origin, "00".repeat(40)));
            assertEquals(200, send(endpoint, origin, encrypt(PairingProtocol.unhex(key), session, "test-payload")));
            assertTrue(got.await(2, TimeUnit.SECONDS)); assertEquals("test-payload", value.get());
            Thread.sleep(80);
            try { send(endpoint, origin, encrypt(PairingProtocol.unhex(key), session, "replay")); fail("Replay accepted"); }
            catch (java.io.IOException expected) { }
        }
    }

    @Test public void expiryClosesSocket() throws Exception {
        CountDownLatch expired = new CountDownLatch(1);
        try (PairingServer server = new PairingServer(InetAddress.getByName("127.0.0.1"), new byte[0], new byte[0],
                s -> fail("Unexpected payload"), expired::countDown, 40)) {
            server.start(); assertTrue(expired.await(3, TimeUnit.SECONDS));
            try { new URL(server.url()).openConnection().getInputStream(); fail(); } catch (java.io.IOException expected) { }
        }
    }

    @Test public void formNeverIncludesKeyAndUsesNoStore() throws Exception {
        try (PairingServer server = new PairingServer(InetAddress.getByName("127.0.0.1"),
                "<html>local form</html>".getBytes(StandardCharsets.UTF_8), new byte[0], s -> {}, () -> {})) {
            server.start();
            HttpURLConnection c = (HttpURLConnection) new URL(server.url()).openConnection();
            assertEquals(200, c.getResponseCode()); assertEquals("no-store", c.getHeaderField("Cache-Control"));
            assertNotNull(c.getHeaderField("Content-Security-Policy"));
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[1024]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n);
                assertEquals("<html>local form</html>", out.toString("UTF-8"));
            } finally { c.disconnect(); }
        }
    }
}
