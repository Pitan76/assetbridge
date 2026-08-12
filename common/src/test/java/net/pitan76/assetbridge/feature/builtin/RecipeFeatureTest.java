package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.feature.FeatureContext;
import net.pitan76.assetbridge.test.TestArchives;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RecipeFeatureTest {
    private static final Predicate<String> NOTHING_LOADED = namespace -> false;
    private static final Set<String> ALL_ON = new HashSet<>(Arrays.asList(DataPackFeature.ID, RecipeFeature.ID));

    private static final String SHAPELESS = "{\"type\":\"minecraft:crafting_shapeless\","
            + "\"ingredients\":[{\"item\":\"minecraft:stone\"}],"
            + "\"result\":{\"item\":\"examplemod:foo\"}}";

    private static final AssetPath FOO =
            new AssetPath(AssetPath.PackKind.SERVER, "examplemod", "recipes/foo.json");

    @Test
    void bridgesAVanillaRecipe() throws IOException {
        BridgedAssetManager assets = apply(ALL_ON, NOTHING_LOADED, Collections.singletonMap(
                "data/examplemod/recipes/foo.json", SHAPELESS
        ));

        boolean isModern = net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.COMPONENTS);
        String expected = isModern ? "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:stone\"}],\"result\":{\"id\":\"examplemod:foo\"}}" : SHAPELESS;
        
        boolean isModernRecipeDir = net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.ITEM_DEFINITIONS);
        AssetPath targetPath = isModernRecipeDir ? new AssetPath(AssetPath.PackKind.SERVER, "examplemod", "recipe/foo.json") : FOO;
        assertEquals(expected, new String(assets.readResource(targetPath), StandardCharsets.UTF_8));
    }

    @Test
    void leavesAModdedRecipeTypeBehind() {
        BridgedAssetManager assets = apply(ALL_ON, NOTHING_LOADED, Collections.singletonMap(
                "data/examplemod/recipes/foo.json", "{\"type\":\"create:mixing\"}"
        ));

        assertFalse(assets.hasResource(FOO));
    }

    @Test
    void skipsANamespaceARealModOwns() {
        BridgedAssetManager assets = apply(ALL_ON, namespace -> namespace.equals("examplemod"), Collections.singletonMap(
                "data/examplemod/recipes/foo.json", SHAPELESS
        ));

        assertFalse(assets.hasResource(FOO));
    }

    @Test
    void bridgesNothingWithoutTheDataPack() {
        BridgedAssetManager assets = apply(new HashSet<>(Arrays.asList(RecipeFeature.ID)), NOTHING_LOADED, Collections.singletonMap(
                "data/examplemod/recipes/foo.json", SHAPELESS
        ));

        assertFalse(assets.hasResource(FOO));
    }

    private static BridgedAssetManager apply(Set<String> enabled, Predicate<String> isNamespaceUsed,
                                     Map<String, String> entries) {
        BridgedAssetManager assets = new BridgedAssetManager();
        List<AssetArchive> archives =
                Arrays.asList(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, entries));

        new RecipeFeature().apply(
                new FeatureContext(Path.of("."), assets, enabled, archives, isNamespaceUsed));
        return assets;
    }
}
