package net.pitan76.assetbridge.block;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.BridgedBlockDefinition;
import net.pitan76.assetbridge.util.ResourceLocations;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the {@link Block} instances for the bridged assets. Construction happens in common
 * code; the actual registry call is left to each platform, because Fabric registers during
 * mod init while Forge registers from a registry event.
 */
public class BridgedBlocks {
    private static Map<ResourceLocation, Block> blocks = Collections.emptyMap();
    private static Map<ResourceLocation, Item> items = Collections.emptyMap();

    private BridgedBlocks() {
    }

    public static void create(BridgedAssetManager assets) {
        Map<ResourceLocation, Block> createdBlocks = new LinkedHashMap<>();
        Map<ResourceLocation, Item> createdItems = new LinkedHashMap<>();

        for (BridgedBlockDefinition asset : assets.blocks()) {
            ResourceLocation id = ResourceLocations.tryParse(asset.id());
            if (id == null) {
                AssetBridge.LOGGER.warn("Skipping block with invalid id '{}' from {}", asset.id(), asset.sourceArchive());
                continue;
            }
            Block block = BridgedBlock.create(id, asset.states());
            createdBlocks.put(id, block);
            // 1.12.2's ItemBlock always declares its tab through the block's own
            // getCreativeTab(), which BridgedBlock already set; there is no later-version
            // Item.Properties#tab to set separately.
            createdItems.put(id, new ItemBlock(block));
        }

        blocks = Collections.unmodifiableMap(createdBlocks);
        items = Collections.unmodifiableMap(createdItems);
    }

    public static Map<ResourceLocation, Block> blocks() {
        return blocks;
    }

    public static Map<ResourceLocation, Item> items() {
        return items;
    }

    public static boolean isCutout(ResourceLocation id) {
        return BlockConfig.isCutout(id);
    }
}
