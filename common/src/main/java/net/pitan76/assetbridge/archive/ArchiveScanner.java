package net.pitan76.assetbridge.archive;

import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Finds and reads the archives placed in {@code mods/assetbridge/}. */
public class ArchiveScanner {
    /**
     * Load order, and with it who wins an id collision, must not depend on the file system
     * or the platform. Sorting {@code Path} would not do: path comparison is case sensitive
     * on Linux and case insensitive on Windows, so the same two files could load in a
     * different order on each. Case-insensitive first, then exact, so the order is total.
     */
    private static final Comparator<Path> BY_FILE_NAME = Comparator
            .comparing((Path file) -> file.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(file -> file.getFileName().toString());

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
            return Collections.emptyList();
        }

        Path cacheDir = NestedArchives.cacheDirectory(gameDir);
        List<AssetArchive> archives = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.sorted(BY_FILE_NAME).collect(Collectors.toList())) {
                if (!Files.isRegularFile(file) || !NestedArchives.isArchive(file.getFileName().toString())) {
                    continue;
                }
                String fileName = file.getFileName().toString();
                add(archives, file, fileName, false);

                // A mod may carry a resource pack, or a whole other mod, inside itself.
                // Those load right after their parent, so the outer one still wins a
                // collision, and nothing about the order depends on the file system.
                for (Path nested : NestedArchives.extract(file, cacheDir)) {
                    add(archives, nested, fileName + "!" + nested.getFileName(), true);
                }
            }
        } catch (IOException e) {
            AssetBridge.LOGGER.error("Failed to list {}", dir, e);
        }
        return archives;
    }

    /**
     * Reads one archive, unless it holds nothing worth bridging: an empty archive would only
     * keep a file handle open for the rest of the session. Most jar-in-jar libraries are
     * exactly that, so their extracted copy is dropped again rather than left in the cache.
     */
    private static void add(List<AssetArchive> archives, Path file, String displayName, boolean nested) {
        AssetArchive archive;
        try {
            archive = read(file, displayName);
        } catch (IOException e) {
            AssetBridge.LOGGER.error("Failed to read archive {}", displayName, e);
            if (nested) NestedArchives.forget(file);
            return;
        }

        if (!archive.entries().isEmpty()) {
            archives.add(archive);
            return;
        }
        try {
            archive.close();
        } catch (IOException e) {
            AssetBridge.LOGGER.warn("Could not close {}", displayName, e);
        }
        if (nested) NestedArchives.forget(file);
    }

    /**
     * Indexes the archive without reading any resource body. The {@link ZipFile} stays open
     * and is owned by the returned archive, so closing it is {@link AssetArchive#close()}'s
     * job — not this method's.
     */
    private static AssetArchive read(Path file, String displayName) throws IOException {
        // Insertion ordered so that iterating an archive follows the order of the ZIP
        // itself rather than a hash, which keeps logs and pack listings reproducible.
        Map<AssetPath, AssetSource> entries = new LinkedHashMap<>();
        Map<String, String> metadata = new HashMap<>();
        VersionDetector.Structure structure = new VersionDetector.Structure();
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

        VersionDetector.Detection detection = VersionDetector.detect(displayName, metadata, structure);
        AssetBridge.LOGGER.info("Indexed {} asset entries from {} (assets look like {}, from {})",
                entries.size(), displayName, detection.version(), detection.source());
        return new AssetArchive(displayName, detection.version(), detection.source(), entries,
                ModMetadata.displayNames(metadata), zip);
    }

    private static String readText(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
