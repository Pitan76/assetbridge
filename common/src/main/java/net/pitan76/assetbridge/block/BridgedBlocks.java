package net.pitan76.assetbridge.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.Item;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.BridgedBlockDefinition;

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
            ResourceLocation id = new ResourceLocation(asset.id());
            Block block = BridgedBlock.create(id, asset.states());
            createdBlocks.put(id, block);
//            createdItems.put(id, new ItemBlock(block, new Item.Properties().tab(BridgedItemGroup.getTab(id.getNamespace(), true))));
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
