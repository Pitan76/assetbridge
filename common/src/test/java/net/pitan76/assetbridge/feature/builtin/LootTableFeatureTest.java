package net.pitan76.assetbridge.feature.builtin;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.asset.BridgedBlockAsset;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import net.pitan76.assetbridge.feature.FeatureContext;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LootTableFeatureTest {
    private static final Set<String> ALL_ON =
            Set.of(DataPackFeature.ID, BlockFeature.ID, LootTableFeature.ID);

    @Test
    void generatesADropSelfTablePerBlock() throws IOException {
        AssetBundle bundle = bundleWithBlock("examplemod", "foo");

        apply(bundle, ALL_ON);

        JsonObject table = read(bundle, AssetPath.blockLootTable("examplemod", "foo"));
        assertEquals("minecraft:block", table.get("type").getAsString());
        JsonObject pool = table.getAsJsonArray("pools").get(0).getAsJsonObject();
        assertEquals("examplemod:foo", pool.getAsJsonArray("entries").get(0)
                .getAsJsonObject().get("name").getAsString());
    }

    @Test
    void keepsATableTheArchiveShipped() throws IOException {
        AssetBundle bundle = bundleWithBlock("examplemod", "foo");
        AssetPath path = AssetPath.blockLootTable("examplemod", "foo");
        bundle.putResource(path, "{\"shipped\":true}".getBytes(StandardCharsets.UTF_8));

        apply(bundle, ALL_ON);

        assertEquals(true, read(bundle, path).get("shipped").getAsBoolean());
    }

    @Test
    void generatesNothingWithoutTheDataPack() {
        AssetBundle bundle = bundleWithBlock("examplemod", "foo");

        apply(bundle, Set.of(BlockFeature.ID, LootTableFeature.ID));

        assertFalse(bundle.hasResource(AssetPath.blockLootTable("examplemod", "foo")));
    }

    @Test
    void generatesNothingWithoutTheBlocks() {
        // Nothing would exist to drop.
        AssetBundle bundle = bundleWithBlock("examplemod", "foo");

        apply(bundle, Set.of(DataPackFeature.ID, LootTableFeature.ID));

        assertFalse(bundle.hasResource(AssetPath.blockLootTable("examplemod", "foo")));
    }

    private static void apply(AssetBundle bundle, Set<String> enabled) {
        new LootTableFeature().apply(new FeatureContext(
                Path.of("."), bundle, enabled, List.of(), namespace -> false));
    }

    private static AssetBundle bundleWithBlock(String namespace, String name) {
        AssetBundle bundle = new AssetBundle();
        bundle.addBlock(new BridgedBlockAsset(namespace, name, namespace + ":block/" + name,
                BridgedStateDefinition.empty(), "example-mod.jar", AssetVersion.MODERN));
        return bundle;
    }

    private static JsonObject read(AssetBundle bundle, AssetPath path) throws IOException {
        byte[] data = bundle.readResource(path);
        assertNotNull(data, "missing resource: " + path);
        JsonObject parsed = Json.parse(new String(data, StandardCharsets.UTF_8));
        assertNotNull(parsed);
        return parsed;
    }
}
