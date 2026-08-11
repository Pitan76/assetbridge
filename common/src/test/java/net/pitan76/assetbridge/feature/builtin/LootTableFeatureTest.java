package net.pitan76.assetbridge.feature.builtin;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.asset.BridgedBlockDefinition;
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
        BridgedAssetManager assets = bundleWithBlock("examplemod", "foo");

        apply(assets, ALL_ON);

        AssetPath path = AssetPath.blockLootTable("examplemod", "foo");
        boolean isModernLootDir = net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.COMPONENTS);
        if (isModernLootDir) {
            path = new AssetPath(path.kind(), path.namespace(), "loot_table/blocks/foo.json");
        }
        JsonObject table = read(assets, path);
        assertEquals("minecraft:block", table.get("type").getAsString());
        JsonObject pool = table.getAsJsonArray("pools").get(0).getAsJsonObject();
        assertEquals("examplemod:foo", pool.getAsJsonArray("entries").get(0)
                .getAsJsonObject().get("name").getAsString());
    }

    @Test
    void keepsATableTheArchiveShipped() throws IOException {
        BridgedAssetManager assets = bundleWithBlock("examplemod", "foo");
        AssetPath path = AssetPath.blockLootTable("examplemod", "foo");
        boolean isModernLootDir = net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(net.pitan76.assetbridge.asset.AssetVersion.COMPONENTS);
        if (isModernLootDir) {
            path = new AssetPath(path.kind(), path.namespace(), "loot_table/blocks/foo.json");
        }
        assets.putResource(path, "{\"shipped\":true}".getBytes(StandardCharsets.UTF_8));

        apply(assets, ALL_ON);

        assertEquals(true, read(assets, path).get("shipped").getAsBoolean());
    }

    @Test
    void generatesNothingWithoutTheDataPack() {
        BridgedAssetManager assets = bundleWithBlock("examplemod", "foo");

        apply(assets, Set.of(BlockFeature.ID, LootTableFeature.ID));

        assertFalse(assets.hasResource(AssetPath.blockLootTable("examplemod", "foo")));
    }

    @Test
    void generatesNothingWithoutTheBlocks() {
        // Nothing would exist to drop.
        BridgedAssetManager assets = bundleWithBlock("examplemod", "foo");

        apply(assets, Set.of(DataPackFeature.ID, LootTableFeature.ID));

        assertFalse(assets.hasResource(AssetPath.blockLootTable("examplemod", "foo")));
    }

    private static void apply(BridgedAssetManager assets, Set<String> enabled) {
        new LootTableFeature().apply(new FeatureContext(
                Path.of("."), assets, enabled, List.of(), namespace -> false));
    }

    private static BridgedAssetManager bundleWithBlock(String namespace, String name) {
        BridgedAssetManager assets = new BridgedAssetManager();
        assets.addBlock(new BridgedBlockDefinition(namespace, name, namespace + ":block/" + name,
                BridgedStateDefinition.empty(), "example-mod.jar", AssetVersion.MODERN));
        return assets;
    }

    private static JsonObject read(BridgedAssetManager assets, AssetPath path) throws IOException {
        byte[] data = assets.readResource(path);
        assertNotNull(data, "missing resource: " + path);
        JsonObject parsed = Json.parse(new String(data, StandardCharsets.UTF_8));
        assertNotNull(parsed);
        return parsed;
    }
}
