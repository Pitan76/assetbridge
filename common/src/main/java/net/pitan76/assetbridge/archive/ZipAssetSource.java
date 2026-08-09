package net.pitan76.assetbridge.archive;

import net.pitan76.assetbridge.asset.AssetSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An entry of an archive that is still open. Reading is deferred until the game asks for
 * the resource, and the stream is decompressed again on every read rather than cached.
 */
public class ZipAssetSource implements AssetSource {
    private final ZipFile zip;
    private final String entryName;

    public ZipAssetSource(ZipFile zip, String entryName) {
        this.zip = zip;
        this.entryName = entryName;
    }

    @Override
    public InputStream open() throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) throw new IOException("Entry disappeared from the archive: " + entryName);
        return zip.getInputStream(entry);
    }

    @Override
    public String toString() {
        return entryName;
    }
}
