package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.feature.FeatureContext;
import net.pitan76.assetbridge.test.TestArchives;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RecipeFeatureTest {
    private static final Predicate<String> NOTHING_LOADED = namespace -> false;
    private static final Set<String> ALL_ON = Set.of(DataPackFeature.ID, RecipeFeature.ID);

    private static final String SHAPELESS = "{\"type\":\"minecraft:crafting_shapeless\","
            + "\"ingredients\":[{\"item\":\"minecraft:stone\"}],"
            + "\"result\":{\"item\":\"examplemod:foo\"}}";

    private static final AssetPath FOO =
            new AssetPath(AssetPath.PackKind.SERVER, "examplemod", "recipes/foo.json");

    @Test
    void bridgesAVanillaRecipe() throws IOException {
        AssetBundle bundle = apply(ALL_ON, NOTHING_LOADED, Map.of(
                "data/examplemod/recipes/foo.json", SHAPELESS
        ));

        assertEquals(SHAPELESS, new String(bundle.readResource(FOO), StandardCharsets.UTF_8));
    }

    @Test
    void leavesAModdedRecipeTypeBehind() {
        AssetBundle bundle = apply(ALL_ON, NOTHING_LOADED, Map.of(
                "data/examplemod/recipes/foo.json", "{\"type\":\"create:mixing\"}"
        ));

        assertFalse(bundle.hasResource(FOO));
    }

    @Test
    void skipsANamespaceARealModOwns() {
        AssetBundle bundle = apply(ALL_ON, namespace -> namespace.equals("examplemod"), Map.of(
                "data/examplemod/recipes/foo.json", SHAPELESS
        ));

        assertFalse(bundle.hasResource(FOO));
    }

    @Test
    void bridgesNothingWithoutTheDataPack() {
        AssetBundle bundle = apply(Set.of(RecipeFeature.ID), NOTHING_LOADED, Map.of(
                "data/examplemod/recipes/foo.json", SHAPELESS
        ));

        assertFalse(bundle.hasResource(FOO));
    }

    private static AssetBundle apply(Set<String> enabled, Predicate<String> namespaceInUse,
                                     Map<String, String> entries) {
        AssetBundle bundle = new AssetBundle();
        List<AssetArchive> archives =
                List.of(TestArchives.archive("example-mod.jar", AssetVersion.MODERN, entries));

        new RecipeFeature().apply(
                new FeatureContext(Path.of("."), bundle, enabled, archives, namespaceInUse));
        return bundle;
    }
}
