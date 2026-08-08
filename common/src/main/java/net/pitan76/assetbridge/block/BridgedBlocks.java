package net.pitan76.assetbridge.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.asset.BridgedBlockAsset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the {@link Block} instances for the bridged assets. Construction happens in common
 * code; the actual registry call is left to each platform, because Fabric registers during
 * mod init while Forge registers from a registry event.
 */
public final class BridgedBlocks {
    private static Map<ResourceLocation, Block> blocks = Collections.emptyMap();
    private static Map<ResourceLocation, Item> items = Collections.emptyMap();

    private BridgedBlocks() {
    }

    public static void create(AssetBundle bundle) {
        Map<ResourceLocation, Block> createdBlocks = new LinkedHashMap<>();
        Map<ResourceLocation, Item> createdItems = new LinkedHashMap<>();

        for (BridgedBlockAsset asset : bundle.blocks()) {
            ResourceLocation id = ResourceLocation.tryParse(asset.id());
            if (id == null) {
                AssetBridge.LOGGER.warn("Skipping block with invalid id '{}' from {}", asset.id(), asset.sourceArchive());
                continue;
            }
            Block block = new Block(BlockBehaviour.Properties.of(Material.STONE).strength(1.5F, 6.0F));
            createdBlocks.put(id, block);
            createdItems.put(id, new BlockItem(block, new Item.Properties().tab(BridgedItemGroup.get())));
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
}
