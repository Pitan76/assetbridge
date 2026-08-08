package net.pitan76.assetbridge.archive;

import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.test.TestArchives;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveScannerTest {
    @TempDir
    Path gameDir;

    @Test
    void createsTheDirectoryWhenItIsMissing() {
        assertEquals(List.of(), ArchiveScanner.scan(gameDir));

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
        assertEquals(AssetVersion.FUTURE, archive.version());
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

        assertEquals(List.of("a.jar", "b.ZIP"), ArchiveScanner.scan(gameDir).stream()
                .map(AssetArchive::fileName).toList());
    }

    @Test
    void survivesAFileThatIsNotAnArchive() throws IOException {
        Files.writeString(bridgeDir().resolve("broken.jar"), "definitely not a zip",
                StandardCharsets.UTF_8);
        writeArchive("good.jar", Map.of("assets/examplemod/blockstates/foo.json", "{}"));

        assertEquals(List.of("good.jar"), ArchiveScanner.scan(gameDir).stream()
                .map(AssetArchive::fileName).toList());
    }

    private AssetArchive single() {
        List<AssetArchive> archives = ArchiveScanner.scan(gameDir);
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
