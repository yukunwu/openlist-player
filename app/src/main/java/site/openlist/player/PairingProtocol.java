package site.openlist.player;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class PairingProtocol {
    private static final SecureRandom RANDOM = new SecureRandom();
    private PairingProtocol() {}

    static byte[] random(int count) { byte[] b = new byte[count]; RANDOM.nextBytes(b); return b; }

    static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) s.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return s.toString();
    }

    static byte[] unhex(String value) {
        if (value.length() % 2 != 0 || value.length() > 32768) throw new IllegalArgumentException("Invalid data");
        byte[] b = new byte[value.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int a = Character.digit(value.charAt(i * 2), 16), c = Character.digit(value.charAt(i * 2 + 1), 16);
            if (a < 0 || c < 0) throw new IllegalArgumentException("Invalid data");
            b[i] = (byte) ((a << 4) | c);
        }
        return b;
    }

    static String decrypt(byte[] key, String session, String wire) throws Exception {
        byte[] bytes = unhex(wire);
        if (bytes.length < 28 || key.length != 32) throw new IllegalArgumentException("Invalid data");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, bytes, 0, 12));
        cipher.updateAAD(session.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
    }
}
