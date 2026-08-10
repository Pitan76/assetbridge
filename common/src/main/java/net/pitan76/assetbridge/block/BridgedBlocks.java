package net.pitan76.assetbridge.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.pitan76.assetbridge.AssetBridge;
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
            ResourceLocation id = ResourceLocation.tryParse(asset.id());
            if (id == null) {
                AssetBridge.LOGGER.warn("Skipping block with invalid id '{}' from {}", asset.id(), asset.sourceArchive());
                continue;
            }
            Block block = BridgedBlock.create(id, asset.states());
            createdBlocks.put(id, block);
            //? if >=26 {
            /*// 26.1 derives the translation key from the item's own id and throws if it was never
            // set, so it has to be on the properties before construction.
            createdItems.put(id, new BlockItem(block, new Item.Properties()
                    .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id))));
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

    public static Map<ResourceLocation, Block> blocks() {
        return blocks;
    }

    public static Map<ResourceLocation, Item> items() {
        return items;
    }

    public static boolean isCutout(ResourceLocation id) {
        String configVal = net.pitan76.assetbridge.feature.Features.getConfigValue("cutout_blocks");
        if (configVal == null) return false;
        String trimmed = configVal.trim();
        if (trimmed.equalsIgnoreCase("false")) return false;
        if (trimmed.equalsIgnoreCase("true")) {
            String path = id.getPath().toLowerCase();
            return path.contains("leaves")
                    || path.contains("glass")
                    || path.contains("sapling")
                    || path.contains("crop")
                    || path.contains("flower")
                    || path.contains("plant")
                    || path.contains("cutout")
                    || path.contains("portal");
        }
        String[] ids = trimmed.split(",");
        String idStr = id.toString();
        for (String targetId : ids) {
            if (targetId.trim().equals(idStr)) {
                return true;
            }
        }
        return false;
    }
}
