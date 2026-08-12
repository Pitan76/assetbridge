package net.pitan76.assetbridge.asset;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Where the bytes of a single resource come from.
 *
 * <p>Textures are by far the largest part of an archive and are only ever needed when the
 * game actually asks for them, so they are served straight out of the still-open ZIP
 * instead of being copied into the heap. Converted resources, which no longer exist in any
 * file, are held as bytes via {@link #ofBytes(byte[])}.
 */
@FunctionalInterface
public interface AssetSource {
    /** Opens a fresh stream over the resource. The caller closes it. */
    InputStream open() throws IOException;

    default byte[] readAll() throws IOException {
        try (InputStream in = open()) {
            //? if >=1.17 {
            return in.readAllBytes();
            //? } else {
            /*
            return IOUtils.toByteArray(in);
            *///? }
        }
    }

    static AssetSource ofBytes(byte[] data) {
        return () -> new ByteArrayInputStream(data);
    }
}
