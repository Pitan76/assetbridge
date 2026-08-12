package net.pitan76.assetbridge.archive;

import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetSource;
import net.pitan76.assetbridge.asset.AssetVersion;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * An external JAR/ZIP that has been indexed. Only asset entries are retained; no class file
 * is ever touched.
 *
 * <p>The archive is left open for as long as the game runs, so entries that were not
 * converted at startup can be streamed on demand instead of being held in the heap.
 * {@link #close()} invalidates every {@link AssetSource} this archive handed out.
 */
public class AssetArchive implements Closeable {
    private final String fileName;
    private final AssetVersion version;
    private final String versionSource;
    private final Map<AssetPath, AssetSource> entries;
    private final Map<String, String> modNames;
    @Nullable
    private final ZipFile zip;

    public AssetArchive(String fileName, AssetVersion version, String versionSource,
                        Map<AssetPath, AssetSource> entries) {
        this(fileName, version, versionSource, entries, Collections.<String, String>emptyMap(), null);
    }

    public AssetArchive(String fileName, AssetVersion version, String versionSource,
                        Map<AssetPath, AssetSource> entries, Map<String, String> modNames,
                        @Nullable ZipFile zip) {
        this.fileName = fileName;
        this.version = version;
        this.versionSource = versionSource;
        this.entries = Collections.unmodifiableMap(entries);
        this.modNames = Collections.unmodifiableMap(modNames);
        this.zip = zip;
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

    public Map<AssetPath, AssetSource> entries() {
        return entries;
    }

    /**
     * The display names the archive's own loader metadata declares, keyed by namespace.
     * Empty when it carries none.
     */
    public Map<String, String> modNames() {
        return modNames;
    }

    @Override
    public void close() throws IOException {
        if (zip != null) zip.close();
    }
}
