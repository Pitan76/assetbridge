package net.pitan76.assetbridge.archive;

import net.pitan76.assetbridge.asset.AssetPath;

import java.util.Collections;
import java.util.Map;

/**
 * An external JAR/ZIP that has been read into memory.
 * Only asset entries are retained; no class file is ever touched.
 */
public class AssetArchive {
    private final String fileName;
    private final int packFormat;
    private final Map<AssetPath, byte[]> entries;

    public AssetArchive(String fileName, int packFormat, Map<AssetPath, byte[]> entries) {
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

    public Map<AssetPath, byte[]> entries() {
        return entries;
    }
}
