package net.pitan76.assetbridge.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.BridgedBlockDefinition;
import net.pitan76.assetbridge.shape.BlockAnalysis;
import net.pitan76.assetbridge.util.IdUtil;

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
            ResourceLocation id = IdUtil.tryParse(asset.id());
            if (id == null) {
                AssetBridge.LOGGER.warn("Skipping block with invalid id '{}' from {}", asset.id(), asset.sourceArchive());
                continue;
            }
            Block block = blockFor(assets, asset, id);
            createdBlocks.put(id, block);
            //? if >=26 {
            /*// 26.1 derives the translation key from the item's own id and throws if it was never
            // set, so it has to be on the properties before construction.
            createdItems.put(id, new BlockItem(block, new Item.Properties()
                    .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id))
                    .useBlockDescriptionPrefix()));
            *///?} elif >=1.19.3 {
            /*// 1.19.3 removed Item.Properties#tab; tabs pull their contents instead.
            createdItems.put(id, new BlockItem(block, new Item.Properties()));
            *///?} else {
            createdItems.put(id, new BlockItem(block, new Item.Properties().tab(BridgedItemGroup.getTab(id.getNamespace(), true))));
            //?}
        }

        blocks = Collections.unmodifiableMap(createdBlocks);
        items = Collections.unmodifiableMap(createdItems);
    }

    /**
     * The block instance for one bridged asset: the vanilla class its model was recognised as
     * where there is one, and the generic block — with the shape its model has, if any —
     * everywhere else.
     */
    private static Block blockFor(BridgedAssetManager assets, BridgedBlockDefinition asset, ResourceLocation id) {
        BlockAnalysis analysis = assets.analysis(asset.id());
        if (analysis == null) return BridgedBlock.create(id, asset.states(), null);

        if (analysis.kind() != null) {
            Block block = BridgedBlockTypes.create(analysis.kind(), id);
            if (block != null) return block;
        }
        return BridgedBlock.create(id, asset.states(), analysis.shape());
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
