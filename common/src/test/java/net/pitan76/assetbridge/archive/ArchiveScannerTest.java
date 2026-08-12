package net.pitan76.assetbridge.archive;

import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetSource;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.test.TestArchives;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveScannerTest {
    @TempDir
    Path gameDir;

    private final List<AssetArchive> opened = new ArrayList<>();

    /** A scanned archive keeps its ZIP open, and Windows will not delete a locked file. */
    @AfterEach
    void closeArchives() throws IOException {
        for (AssetArchive archive : opened) archive.close();
        opened.clear();
    }

    @Test
    void createsTheDirectoryWhenItIsMissing() {
        assertEquals(Collections.emptyList(), scan());

        assertTrue(Files.isDirectory(ArchiveScanner.directory(gameDir)));
    }

    @Test
    void readsOnlyAssetEntries() throws IOException {
        writeArchive("example-mod.jar", Map.of(
                "assets/examplemod/blockstates/foo.json", "{}",
                "assets/examplemod/models/block/foo.json", "{}",
                "assets/examplemod/textures/block/foo.png", "not really a png",
                "assets/examplemod/lang/en_us.json", "{}",
                "assets/examplemod/sounds.json", "{}",
                "net/example/ExampleMod.class", "cafebabe",
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0"
        ));

        AssetArchive archive = single();

        assertEquals(Set.of(
                "assets/examplemod/blockstates/foo.json",
                "assets/examplemod/models/block/foo.json",
                "assets/examplemod/textures/block/foo.png",
                "assets/examplemod/lang/en_us.json"
        ), archive.entries().keySet().stream().map(AssetPath::toFullPath).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void detectsTheVersionFromThePackMetadata() throws IOException {
        writeArchive("example-mod.jar", Map.of(
                "pack.mcmeta", "{\"pack\": {\"pack_format\": 4, \"description\": \"x\"}}",
                "assets/examplemod/blockstates/foo.json", "{}"
        ));

        AssetArchive archive = single();
        assertEquals(AssetVersion.FLATTENED, archive.version());
        assertEquals("pack.mcmeta", archive.versionSource());
    }

    @Test
    void fallsBackToTheLoaderMetadataWhenThereIsNoPackMcmeta() throws IOException {
        // The common case: mod JARs are not resource packs and ship no pack.mcmeta.
        writeArchive("example-mod.jar", Map.of(
                "META-INF/neoforge.mods.toml",
                "[[dependencies.\"examplemod\"]]\nmodId=\"minecraft\"\nversionRange=\"[1.21.1]\"\n",
                "assets/examplemod/blockstates/foo.json", "{}"
        ));

        AssetArchive archive = single();
        assertEquals(AssetVersion.COMPONENTS, archive.version());
        assertEquals("META-INF/neoforge.mods.toml", archive.versionSource());
    }

    @Test
    void assumesTheCurrentVersionWhenNothingSaysOtherwise() throws IOException {
        writeArchive("assets.zip", Map.of("assets/examplemod/blockstates/foo.json", "{}"));

        assertEquals(AssetVersion.MODERN, single().version());
    }

    @Test
    void picksUpJarAndZipOnly() throws IOException {
        writeArchive("a.jar", Map.of("assets/a/blockstates/foo.json", "{}"));
        writeArchive("b.ZIP", Map.of("assets/b/blockstates/foo.json", "{}"));
        writeArchive("c.txt", Map.of("assets/c/blockstates/foo.json", "{}"));

        assertEquals(Arrays.asList("a.jar", "b.ZIP"), scan().stream()
                .map(AssetArchive::fileName).collect(Collectors.toList()));
    }

    @Test
    void survivesAFileThatIsNotAnArchive() throws IOException {
        Files.writeString(bridgeDir().resolve("broken.jar"), "definitely not a zip",
                StandardCharsets.UTF_8);
        writeArchive("good.jar", Map.of("assets/examplemod/blockstates/foo.json", "{}"));

        assertEquals(Arrays.asList("good.jar"), scan().stream()
                .map(AssetArchive::fileName).collect(Collectors.toList()));
    }

    @Test
    void readsAnEntryOnDemandRatherThanAtScanTime() throws IOException {
        writeArchive("example-mod.jar", Map.of(
                "assets/examplemod/textures/block/foo.png", "not really a png"
        ));

        AssetArchive archive = single();
        AssetSource source = archive.entries()
                .get(AssetPath.parse("assets/examplemod/textures/block/foo.png"));

        assertEquals("not really a png", new String(source.readAll(), StandardCharsets.UTF_8));
        // Reading twice must work: nothing is consumed or cached.
        assertEquals("not really a png", new String(source.readAll(), StandardCharsets.UTF_8));
    }

    @Test
    void closingTheArchiveInvalidatesItsEntries() throws IOException {
        writeArchive("example-mod.jar", Map.of(
                "assets/examplemod/textures/block/foo.png", "not really a png"
        ));

        AssetArchive archive = single();
        AssetSource source = archive.entries()
                .get(AssetPath.parse("assets/examplemod/textures/block/foo.png"));
        archive.close();

        assertThrows(IllegalStateException.class, source::readAll);
    }

    @Test
    void loadsArchivesInTheSameOrderOnEveryPlatform() throws IOException {
        // Upper and lower case must not sort into two different orders depending on whether
        // the file system happens to be case sensitive.
        writeArchive("Beta.jar", Map.of("assets/b/blockstates/foo.json", "{}"));
        writeArchive("alpha.jar", Map.of("assets/a/blockstates/foo.json", "{}"));
        writeArchive("Charlie.zip", Map.of("assets/c/blockstates/foo.json", "{}"));

        assertEquals(Arrays.asList("alpha.jar", "Beta.jar", "Charlie.zip"),
                scan().stream().map(AssetArchive::fileName).collect(Collectors.toList()));
    }

    @Test
    void keepsTheOrderOfTheEntriesInsideAnArchive() throws IOException {
        Map<String, String> entries = new java.util.LinkedHashMap<>();
        entries.put("assets/examplemod/blockstates/c.json", "{}");
        entries.put("assets/examplemod/blockstates/a.json", "{}");
        entries.put("assets/examplemod/blockstates/b.json", "{}");
        writeArchive("example-mod.jar", entries);

        assertEquals(new ArrayList<>(entries.keySet()),
                single().entries().keySet().stream().map(AssetPath::toFullPath).collect(Collectors.toList()));
    }

    @Test
    void bridgesAnArchiveThatIsItselfInsideAnArchive() throws IOException {
        // A mod carrying its own resource pack, and the jar-in-jar case, are the same shape.
        writeNested("example-mod.jar",
                Map.of("assets/examplemod/blockstates/outer.json", "{}"),
                Map.of("META-INF/jars/inner.jar",
                        Map.of("assets/innermod/blockstates/inner.json", "{}")));

        List<AssetArchive> archives = scan();

        // The nested one loads right after its parent, so the outer still wins a collision.
        assertEquals(Arrays.asList("example-mod.jar", "example-mod.jar!META-INF_jars_inner.jar"),
                archives.stream().map(AssetArchive::fileName).collect(Collectors.toList()));
        assertEquals(Set.of("assets/innermod/blockstates/inner.json"),
                archives.get(1).entries().keySet().stream()
                        .map(AssetPath::toFullPath).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void dropsANestedLibraryThatCarriesNoAssets() throws IOException {
        writeNested("example-mod.jar",
                Map.of("assets/examplemod/blockstates/outer.json", "{}"),
                Map.of("META-INF/jars/library.jar", Map.of("net/example/Library.class", "cafebabe")));

        assertEquals(Arrays.asList("example-mod.jar"), scan().stream().map(AssetArchive::fileName).collect(Collectors.toList()));

        // Nothing useful came out of it, so the extracted copy is not left behind either.
        Path cached = NestedArchives.cacheDirectory(gameDir)
                .resolve("example-mod.jar").resolve("META-INF_jars_library.jar");
        assertFalse(Files.exists(cached), "the cached copy should have been removed");
    }

    @Test
    void readsTheNestedAssetsLazilyFromTheCachedCopy() throws IOException {
        writeNested("example-mod.jar", Collections.emptyMap(),
                Map.of("inner.zip", Map.of("assets/innermod/textures/block/foo.png", "png bytes")));

        AssetArchive archive = single();
        AssetSource source = archive.entries()
                .get(AssetPath.parse("assets/innermod/textures/block/foo.png"));

        assertEquals("png bytes", new String(source.readAll(), StandardCharsets.UTF_8));
    }

    private void writeNested(String name, Map<String, String> own,
                             Map<String, Map<String, String>> nested) throws IOException {
        Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : own.entrySet()) {
            entries.put(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
        for (Map.Entry<String, Map<String, String>> entry : nested.entrySet()) {
            entries.put(entry.getKey(), TestArchives.bytes(entry.getValue()));
        }
        TestArchives.writeRaw(bridgeDir().resolve(name), entries);
    }

    private List<AssetArchive> scan() {
        List<AssetArchive> archives = ArchiveScanner.scan(gameDir);
        opened.addAll(archives);
        return archives;
    }

    private AssetArchive single() {
        List<AssetArchive> archives = scan();
        assertEquals(1, archives.size());
        return archives.get(0);
    }

    private Path bridgeDir() throws IOException {
        return Files.createDirectories(ArchiveScanner.directory(gameDir));
    }

    private void writeArchive(String name, Map<String, String> entries) throws IOException {
        TestArchives.write(bridgeDir().resolve(name), entries);
    }
}
