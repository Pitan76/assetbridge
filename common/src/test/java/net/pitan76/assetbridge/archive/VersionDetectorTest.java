package net.pitan76.assetbridge.archive;

import net.pitan76.assetbridge.archive.VersionDetector.Detection;
import net.pitan76.assetbridge.archive.VersionDetector.Structure;
import net.pitan76.assetbridge.asset.AssetVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionDetectorTest {
    @ParameterizedTest
    @CsvSource({
            "1.12.2,  LEGACY",
            "1.13,    FLATTENED",
            "1.14.4,  FLATTENED",
            "1.15.2,  MODERN",
            "1.18.2,  MODERN",
            "1.19,    MODERN",
            "1.19.2,  MODERN",
            "1.19.3,  FUTURE",
            "1.20.1,  FUTURE",
            "1.21.1,  FUTURE"
    })
    void mapsMinecraftVersionsOntoAssetGenerations(String version, AssetVersion expected) {
        assertEquals(expected, AssetVersion.fromMinecraftVersion(version));
    }

    @Test
    void theDirectoryLayoutBeatsEveryDeclaredVersion() {
        // pack.mcmeta is hand-written and often stale; assets/*/items/ cannot lie.
        Detection detection = detect("mymod.jar", structure("assets/mymod/items/wand.json"), Map.of(
                "pack.mcmeta", "{\"pack\": {\"pack_format\": 8}}",
                "META-INF/mods.toml", "modId=\"minecraft\"\nversionRange=\"[1.18.2]\""
        ));

        assertEquals(AssetVersion.FUTURE, detection.version());
        assertEquals("assets/*/items/ (1.21.4+)", detection.source());
    }

    @Test
    void spriteAtlasesMarkAnArchiveAsNewerThanThisVersion() {
        assertEquals(AssetVersion.FUTURE, detect("mymod.jar", structure("assets/mymod/atlases/blocks.json")).version());
    }

    @Test
    void pluralTextureDirectoriesMarkAnArchiveAsPreFlattening() {
        Detection detection = detect("mymod.jar", structure("assets/mymod/textures/blocks/foo.png"));

        assertEquals(AssetVersion.LEGACY, detection.version());
        assertEquals("assets/*/textures/blocks/ (pre-1.13)", detection.source());
    }

    @Test
    void loaderMetadataBeatsPackMcmeta() {
        // The loader enforces its dependency range, so it has to be accurate.
        Detection detection = detect("mymod.jar", new Structure(), Map.of(
                "pack.mcmeta", "{\"pack\": {\"pack_format\": 3}}",
                "META-INF/mods.toml", "modId=\"minecraft\"\nversionRange=\"[1.21.1]\""
        ));

        assertEquals(AssetVersion.FUTURE, detection.version());
        assertEquals("META-INF/mods.toml", detection.source());
    }

    @Test
    void readsTheMinecraftDependencyFromNeoForgeMetadata() {
        Detection detection = detect("mymod.jar", new Structure(), Map.of(
                "META-INF/neoforge.mods.toml", """
                        [[dependencies."astralenchant"]]
                        modId="neoforge"
                        versionRange="[21.1.235,)"

                        [[dependencies."astralenchant"]]
                        modId="minecraft"
                        versionRange="[1.21.1]"
                        """));

        // The neoforge dependency's 21.1.235 must not be mistaken for a Minecraft version.
        assertEquals(AssetVersion.FUTURE, detection.version());
        assertEquals("META-INF/neoforge.mods.toml", detection.source());
    }

    @Test
    void readsTheMinecraftDependencyFromForgeMetadata() {
        Detection detection = detect("mymod.jar", new Structure(), Map.of(
                "META-INF/mods.toml", """
                        [[dependencies.mymod]]
                        modId = "minecraft"
                        mandatory = true
                        versionRange = "[1.16.5,1.17)"
                        """));

        assertEquals(AssetVersion.MODERN, detection.version());
    }

    @Test
    void readsTheMinecraftDependencyFromFabricMetadata() {
        Detection detection = detect("mymod.jar", new Structure(), Map.of(
                "fabric.mod.json", "{\"depends\": {\"minecraft\": \">=1.20.1 <1.21\", \"java\": \">=17\"}}"));

        assertEquals(AssetVersion.FUTURE, detection.version());
        assertEquals("fabric.mod.json", detection.source());
    }

    @Test
    void acceptsAnArrayOfFabricVersionPredicates() {
        Detection detection = detect("mymod.jar", new Structure(), Map.of(
                "fabric.mod.json", "{\"depends\": {\"minecraft\": [\"1.12.2\", \"1.13\"]}}"));

        assertEquals(AssetVersion.LEGACY, detection.version());
    }

    @Test
    void usesPackMcmetaForPlainResourcePacks() {
        // A ZIP with no loader metadata is a resource pack, where pack_format is all there is.
        Detection detection = detect("my-pack.zip", new Structure(), Map.of(
                "pack.mcmeta", "{\"pack\": {\"pack_format\": 4}}"));

        assertEquals(AssetVersion.FLATTENED, detection.version());
        assertEquals("pack.mcmeta", detection.source());
    }

    @Test
    void fallsBackToTheFileName() {
        Detection detection = detect("astralenchant-1.21.1-neoforge-v1.1.0.jar", new Structure());

        assertEquals(AssetVersion.FUTURE, detection.version());
        assertEquals("file name", detection.source());
    }

    @Test
    void ignoresModVersionNumbersInTheFileName() {
        // 1.2.0 is the mod's own version; only 1.20.1 is a plausible Minecraft release.
        assertEquals(AssetVersion.FUTURE, detect("mymod-1.2.0-1.20.1.jar", new Structure()).version());
        assertEquals(AssetVersion.MODERN, detect("mymod-1.18.2-3.0.1.jar", new Structure()).version());
    }

    @Test
    void assumesTheCurrentVersionWhenNothingIsAvailable() {
        assertEquals(AssetVersion.MODERN, detect("assets.zip", new Structure()).version());
    }

    @Test
    void survivesBrokenMetadata() {
        assertEquals(AssetVersion.MODERN, detect("assets.zip", new Structure(), Map.of(
                "pack.mcmeta", "{ not json",
                "fabric.mod.json", "also not json",
                "META-INF/mods.toml", "modId=\"minecraft\""
        )).version());
    }

    private static Structure structure(String... entryNames) {
        Structure structure = new Structure();
        for (String entryName : entryNames) {
            structure.observe(entryName);
        }
        return structure;
    }

    private static Detection detect(String fileName, Structure structure) {
        return detect(fileName, structure, Map.of());
    }

    private static Detection detect(String fileName, Structure structure, Map<String, String> metadata) {
        return VersionDetector.detect(fileName, metadata, structure);
    }
}
