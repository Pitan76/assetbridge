package net.pitan76.assetbridge.archive;

import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Finds and reads the archives placed in {@code mods/assetbridge/}. */
public class ArchiveScanner {
    private ArchiveScanner() {
    }

    public static Path directory(Path gameDir) {
        return gameDir.resolve("mods").resolve(AssetBridge.MOD_ID);
    }

    public static List<AssetArchive> scan(Path gameDir) {
        Path dir = directory(gameDir);
        if (!Files.isDirectory(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                AssetBridge.LOGGER.warn("Could not create {}", dir, e);
            }
            return List.of();
        }

        List<AssetArchive> archives = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!Files.isRegularFile(file) || !(name.endsWith(".jar") || name.endsWith(".zip"))) continue;
                try {
                    archives.add(read(file));
                } catch (IOException e) {
                    AssetBridge.LOGGER.error("Failed to read archive {}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            AssetBridge.LOGGER.error("Failed to list {}", dir, e);
        }
        return archives;
    }

    /**
     * Indexes the archive without reading any resource body. The {@link ZipFile} stays open
     * and is owned by the returned archive, so closing it is {@link AssetArchive#close()}'s
     * job — not this method's.
     */
    private static AssetArchive read(Path file) throws IOException {
        Map<AssetPath, AssetSource> entries = new HashMap<>();
        Map<String, String> metadata = new HashMap<>();
        VersionDetector.Structure structure = new VersionDetector.Structure();
        String fileName = file.getFileName().toString();

        ZipFile zip = new ZipFile(file.toFile());
        try {
            var it = zip.entries();
            while (it.hasMoreElements()) {
                ZipEntry entry = it.nextElement();
                if (entry.isDirectory()) continue;

                if (VersionDetector.METADATA_FILES.contains(entry.getName())) {
                    metadata.put(entry.getName(), readText(zip, entry));
                    continue;
                }
                structure.observe(entry.getName());

                AssetPath path = AssetPath.parse(entry.getName());
                if (path == null || !path.isBridgeable()) continue;

                entries.put(path, new ZipAssetSource(zip, entry.getName()));
            }
        } catch (IOException | RuntimeException e) {
            zip.close();
            throw e;
        }

        VersionDetector.Detection detection = VersionDetector.detect(fileName, metadata, structure);
        AssetBridge.LOGGER.info("Indexed {} asset entries from {} (assets look like {}, from {})",
                entries.size(), fileName, detection.version(), detection.source());
        return new AssetArchive(fileName, detection.version(), detection.source(), entries, zip);
    }

    private static String readText(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
