package net.pitan76.assetbridge.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Small stream helpers that fill in for Java 9+ {@code InputStream} additions on Java 8. */
public class IoUtil {
    private static final int BUFFER_SIZE = 8192;

    private IoUtil() {
    }

    /** Java 8 equivalent of {@code InputStream#readAllBytes()} (added in Java 9). */
    public static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
