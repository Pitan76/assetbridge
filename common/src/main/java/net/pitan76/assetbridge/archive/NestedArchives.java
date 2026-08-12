package net.pitan76.assetbridge.archive;

import net.pitan76.assetbridge.AssetBridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds the archives inside an archive: a mod that embeds a resource pack, and the
 * jar-in-jar libraries Fabric mods ship under {@code META-INF/jars/}.
 *
 * <p>A ZIP inside a ZIP cannot be indexed in place — {@code ZipFile} needs random access to
 * a real file, and a {@code ZipInputStream} over the entry would mean reading every nested
 * texture into the heap, which is exactly what the lazy loading avoids. So a nested archive
 * is copied out to a cache directory once and opened from there, and stays lazy.
 *
 * <p>One level only. A jar inside a jar inside a jar is not something mods do, and following
 * it would turn a malformed archive into an unbounded extraction.
 */
public class NestedArchives {
    private NestedArchives() {
    }

    /** Where extracted archives live. Outside {@code mods/}, so no loader ever scans them. */
    public static Path cacheDirectory(Path gameDir) {
        return gameDir.resolve(".cache").resolve(AssetBridge.MOD_ID).resolve("nested");
    }

    /**
     * Extracts every archive inside {@code parent}, reusing what is already cached.
     *
     * @return the extracted files, in a deterministic order
     */
    public static List<Path> extract(Path parent, Path cacheDir) {
        List<Path> extracted = new ArrayList<>();

        try (ZipFile zip = new ZipFile(parent.toFile())) {
            List<ZipEntry> nested = new ArrayList<>();
            Enumeration<? extends ZipEntry> it = zip.entries();
            while (it.hasMoreElements()) {
                ZipEntry entry = it.nextElement();
                if (!entry.isDirectory() && isArchive(entry.getName())) nested.add(entry);
            }
            if (nested.isEmpty()) return Collections.emptyList();

            nested.sort(Comparator.comparing(ZipEntry::getName));
            Path target = cacheDir.resolve(safeName(parent.getFileName().toString()));
            Files.createDirectories(target);

            for (ZipEntry entry : nested) {
                Path file = target.resolve(safeName(entry.getName()));
                if (isStale(file, parent)) copy(zip, entry, file);
                extracted.add(file);
            }
        } catch (IOException e) {
            AssetBridge.LOGGER.error("Could not look inside {}", parent.getFileName(), e);
        }
        return extracted;
    }

    public static boolean isArchive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".zip");
    }

    /** Drops what was extracted from an archive that no longer contains anything usable. */
    public static void forget(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            AssetBridge.LOGGER.warn("Could not remove the cached copy of {}", file, e);
        }
    }

    /** Re-extracts when the player replaced the outer archive with a newer build. */
    private static boolean isStale(Path file, Path parent) throws IOException {
        if (!Files.isRegularFile(file)) return true;
        return Files.getLastModifiedTime(file).compareTo(Files.getLastModifiedTime(parent)) < 0;
    }

    private static void copy(ZipFile zip, ZipEntry entry, Path file) throws IOException {
        // The copy is stamped with the current time, which is necessarily newer than the
        // archive it came from, so the next start reuses it.
        try (InputStream in = zip.getInputStream(entry)) {
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Flattens a path into one file name, keeping it recognisable and safe to write. */
    private static String safeName(String name) {
        StringBuilder safe = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            safe.append(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? c : '_');
        }
        return safe.toString();
    }
}
