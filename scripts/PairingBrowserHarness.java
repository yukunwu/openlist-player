package site.openlist.player;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Uses synthetic test data only; no user configuration or account is read. */
public final class PairingBrowserHarness {
    public static void main(String[] args) throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        try (PairingServer server = new PairingServer(InetAddress.getByName("127.0.0.1"),
                Files.readAllBytes(Path.of(args[0], "pairing.html")), Files.readAllBytes(Path.of(args[0], "forge.min.js")),
                text -> {
                    try { Files.writeString(Path.of(args[1]), text); received.countDown(); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }, () -> {})) {
            server.start();
            System.out.println(server.url()); System.out.flush();
            if (!received.await(90, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
        }
    }
}
