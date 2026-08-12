package net.pitan76.assetbridge.block;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.BridgedItemDefinition;
import net.pitan76.assetbridge.util.ResourceLocations;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the plain {@link Item} instances for item models that no bridged block claims.
 *
 * <p>Like the blocks, these carry no behaviour: they exist so the model and texture from the
 * external mod can be held, seen in the creative tab and used for screenshots.
 */
public class BridgedItems {
    private static Map<ResourceLocation, Item> items = Collections.emptyMap();

    private BridgedItems() {
    }

    public static void create(BridgedAssetManager assets) {
        Map<ResourceLocation, Item> created = new LinkedHashMap<>();

        for (BridgedItemDefinition asset : assets.items()) {
            ResourceLocation id = ResourceLocations.tryParse(asset.id());
            if (id == null) {
                AssetBridge.LOGGER.warn("Skipping item with invalid id '{}' from {}", asset.id(), asset.sourceArchive());
                continue;
            }
            Item item = new Item();
            item.setTranslationKey(id.toString());
            item.setCreativeTab(BridgedItemGroup.getTab(id.getNamespace(), false));
            created.put(id, item);
        }

        items = Collections.unmodifiableMap(created);
    }

    public static Map<ResourceLocation, Item> items() {
        return items;
    }
}
