package net.pitan76.assetbridge.archive;

import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;

import java.util.Collections;
import java.util.Map;

/**
 * An external JAR/ZIP that has been read into memory.
 * Only asset entries are retained; no class file is ever touched.
 */
public class AssetArchive {
    private final String fileName;
    private final AssetVersion version;
    private final String versionSource;
    private final Map<AssetPath, byte[]> entries;

    public AssetArchive(String fileName, AssetVersion version, String versionSource, Map<AssetPath, byte[]> entries) {
        this.fileName = fileName;
        this.version = version;
        this.versionSource = versionSource;
        this.entries = Collections.unmodifiableMap(entries);
    }

    public String fileName() {
        return fileName;
    }

    /** The asset generation this archive was authored for. */
    public AssetVersion version() {
        return version;
    }

    /** Which metadata file the version came from, for logging. */
    public String versionSource() {
        return versionSource;
    }

    public Map<AssetPath, byte[]> entries() {
        return entries;
    }
}
