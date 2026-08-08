package net.pitan76.assetbridge.archive;

import java.util.Collections;
import java.util.Map;

/**
 * An external JAR/ZIP that has been read into memory.
 * Only asset entries are retained; no class file is ever touched.
 */
public final class AssetArchive {
    private final String fileName;
    private final int packFormat;
    private final Map<String, byte[]> entries;

    public AssetArchive(String fileName, int packFormat, Map<String, byte[]> entries) {
        this.fileName = fileName;
        this.packFormat = packFormat;
        this.entries = Collections.unmodifiableMap(entries);
    }

    public String fileName() {
        return fileName;
    }

    /** pack_format declared by the archive's pack.mcmeta, or -1 when unknown. */
    public int packFormat() {
        return packFormat;
    }

    /** Asset entries keyed by their full archive path, e.g. {@code assets/examplemod/models/block/foo.json}. */
    public Map<String, byte[]> entries() {
        return entries;
    }
}
