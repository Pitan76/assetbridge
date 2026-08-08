package net.pitan76.assetbridge.asset;

import net.pitan76.assetbridge.asset.AssetPath.Category;
import net.pitan76.assetbridge.asset.AssetPath.PackKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetPathTest {
    @Test
    void splitsAFullPath() {
        AssetPath path = AssetPath.parse("assets/examplemod/models/block/foo.json");

        assertNotNull(path);
        assertEquals(PackKind.CLIENT, path.kind());
        assertEquals("examplemod", path.namespace());
        assertEquals("models/block/foo.json", path.path());
    }

    @Test
    void recognisesTheDataRoot() {
        AssetPath path = AssetPath.parse("data/examplemod/loot_tables/blocks/foo.json");

        assertNotNull(path);
        assertEquals(PackKind.SERVER, path.kind());
    }

    @Test
    void normalisesBackslashes() {
        AssetPath path = AssetPath.parse("assets\\examplemod\\textures\\block\\foo.png");

        assertNotNull(path);
        assertEquals("textures/block/foo.png", path.path());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "assets/examplemod/models/block/foo.json",
            "assets/examplemod/blockstates/foo.json",
            "data/examplemod/loot_tables/blocks/foo.json",
            "assets/examplemod/textures/block/foo.png.mcmeta"
    })
    void parseAndToFullPathRoundTrip(String fullPath) {
        AssetPath path = AssetPath.parse(fullPath);

        assertNotNull(path);
        assertEquals(fullPath, path.toFullPath());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "META-INF/MANIFEST.MF",
            "net/example/ExampleMod.class",
            "pack.mcmeta",
            "assets",
            "assets/",
            "assets/examplemod",
            "assets//foo.json"
    })
    void rejectsNonPackPaths(String fullPath) {
        assertNull(AssetPath.parse(fullPath));
    }

    @ParameterizedTest
    @CsvSource({
            "blockstates/foo.json,       BLOCKSTATE",
            "models/block/foo.json,      BLOCK_MODEL",
            "models/item/foo.json,       ITEM_MODEL",
            "models/misc/foo.json,       MODEL",
            "textures/block/foo.png,     TEXTURE",
            "textures/block/foo.png.mcmeta, TEXTURE_META",
            "lang/en_us.json,            LANG",
            "sounds.json,                OTHER"
    })
    void derivesTheCategoryFromThePath(String path, Category expected) {
        assertEquals(expected, new AssetPath(PackKind.CLIENT, "examplemod", path.trim()).category());
    }

    @Test
    void onlyBridgeableCategoriesAreExtracted() {
        assertTrue(new AssetPath(PackKind.CLIENT, "examplemod", "models/block/foo.json").isBridgeable());
        assertFalse(new AssetPath(PackKind.CLIENT, "examplemod", "sounds.json").isBridgeable());
    }

    @Test
    void readsTheBlockNameFromABlockStatePath() {
        assertEquals("foo", AssetPath.blockState("examplemod", "foo").blockStateName());
        assertNull(new AssetPath(PackKind.CLIENT, "examplemod", "models/block/foo.json").blockStateName());
    }

    @Test
    void readsTheItemNameFromAnItemModelPath() {
        assertEquals("wand", AssetPath.itemModel("examplemod", "wand").itemModelName());
        // Nested files are model fragments shared by other models, not items.
        assertNull(new AssetPath(PackKind.CLIENT, "examplemod", "models/item/parts/handle.json").itemModelName());
        assertNull(new AssetPath(PackKind.CLIENT, "examplemod", "models/block/foo.json").itemModelName());
    }

    @Test
    void buildsGeneratedPaths() {
        assertEquals("assets/examplemod/blockstates/foo.json", AssetPath.blockState("examplemod", "foo").toFullPath());
        assertEquals("assets/examplemod/models/item/foo.json", AssetPath.itemModel("examplemod", "foo").toFullPath());
    }
}
