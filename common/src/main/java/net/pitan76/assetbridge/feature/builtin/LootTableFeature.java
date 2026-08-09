package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.BridgedBlockDefinition;
import net.pitan76.assetbridge.data.LootTables;
import net.pitan76.assetbridge.feature.Feature;
import net.pitan76.assetbridge.feature.FeatureContext;
import net.pitan76.assetbridge.util.Json;

import java.nio.charset.StandardCharsets;

/**
 * Gives every bridged block the loot table it needs to drop itself when broken.
 *
 * <p>A block with no loot table drops nothing and Minecraft logs a missing-table error, so
 * without this a bridged block cannot be picked back up in survival.
 */
public class LootTableFeature implements Feature {
    public static final String ID = "loot_tables";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Generate a drop-self loot table for every bridged block. Off: bridged blocks drop nothing when broken.";
    }

    @Override
    public void apply(FeatureContext context) {
        // The tables would be generated into a pack that is never served, and the item they
        // name would not exist either.
        if (!context.isEnabled(DataPackFeature.ID) || !context.isEnabled(BlockFeature.ID)) {
            AssetBridge.LOGGER.info("Not generating loot tables: '{}' and '{}' are both required",
                    DataPackFeature.ID, BlockFeature.ID);
            return;
        }

        int generated = 0;
        for (BridgedBlockDefinition block : context.assets().blocks()) {
            AssetPath path = AssetPath.blockLootTable(block.namespace(), block.path());
            // An archive that shipped its own table keeps it.
            if (context.assets().hasResource(path)) continue;

            context.assets().putResource(path,
                    Json.toString(LootTables.dropSelf(block.id())).getBytes(StandardCharsets.UTF_8));
            generated++;
        }
        AssetBridge.LOGGER.info("Generated {} block loot table(s)", generated);
    }
}
