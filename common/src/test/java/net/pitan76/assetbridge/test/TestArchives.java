package net.pitan76.assetbridge.test;

import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetSource;
import net.pitan76.assetbridge.asset.AssetVersion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds archives and in-memory bundles for tests. */
public class TestArchives {
    private TestArchives() {
    }

    /** Writes a real ZIP so the scanner can be exercised end to end. */
    public static void write(Path file, Map<String, String> entries) throws IOException {
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    /** Builds an {@link AssetArchive} directly, skipping the ZIP round trip. */
    public static AssetArchive archive(String fileName, AssetVersion version, Map<String, String> entries) {
        Map<AssetPath, AssetSource> parsed = new HashMap<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            AssetPath path = AssetPath.parse(entry.getKey());
            if (path == null) throw new IllegalArgumentException("Not a pack path: " + entry.getKey());
            parsed.put(path, AssetSource.ofBytes(entry.getValue().getBytes(StandardCharsets.UTF_8)));
        }
        return new AssetArchive(fileName, version, "test", parsed);
    }
}
