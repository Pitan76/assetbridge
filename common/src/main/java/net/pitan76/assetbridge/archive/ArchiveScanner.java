package net.pitan76.assetbridge.archive;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.util.Json;

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
public final class ArchiveScanner {
    /** Directories inside {@code assets/<namespace>/} that are worth extracting for the MVP. */
    private static final String[] INTERESTING = {"blockstates/", "models/", "textures/", "lang/"};

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

    private static AssetArchive read(Path file) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        int packFormat = -1;

        try (ZipFile zip = new ZipFile(file.toFile())) {
            var it = zip.entries();
            while (it.hasMoreElements()) {
                ZipEntry entry = it.nextElement();
                if (entry.isDirectory()) continue;
                String path = entry.getName().replace('\\', '/');

                if (path.equals("pack.mcmeta")) {
                    packFormat = readPackFormat(zip, entry);
                    continue;
                }
                if (!isInteresting(path)) continue;

                try (InputStream in = zip.getInputStream(entry)) {
                    entries.put(path, in.readAllBytes());
                }
            }
        }
        AssetBridge.LOGGER.info("Read {} asset entries from {} (pack_format={})",
                entries.size(), file.getFileName(), packFormat);
        return new AssetArchive(file.getFileName().toString(), packFormat, entries);
    }

    private static int readPackFormat(ZipFile zip, ZipEntry entry) {
        try (InputStream in = zip.getInputStream(entry)) {
            JsonObject root = Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (root != null && root.has("pack") && root.getAsJsonObject("pack").has("pack_format")) {
                return root.getAsJsonObject("pack").get("pack_format").getAsInt();
            }
        } catch (Exception ignored) {
            // A malformed pack.mcmeta only costs us the version hint.
        }
        return -1;
    }

    private static boolean isInteresting(String path) {
        if (!path.startsWith("assets/")) return false;
        int nsEnd = path.indexOf('/', "assets/".length());
        if (nsEnd < 0) return false;
        String rest = path.substring(nsEnd + 1);
        for (String prefix : INTERESTING) {
            if (rest.startsWith(prefix)) return true;
        }
        return false;
    }
}
