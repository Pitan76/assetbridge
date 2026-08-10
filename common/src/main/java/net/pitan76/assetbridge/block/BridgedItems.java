package net.pitan76.assetbridge.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.BridgedItemDefinition;

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
            ResourceLocation id = ResourceLocation.tryParse(asset.id());
            if (id == null) {
                AssetBridge.LOGGER.warn("Skipping item with invalid id '{}' from {}", asset.id(), asset.sourceArchive());
                continue;
            }
            //? if >=26 {
            /*// 26.1 derives the translation key from the item's own id and throws if it was never
            // set, so it has to be on the properties before construction.
            created.put(id, new Item(new Item.Properties()
                    .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id))));
            *///?} elif >=1.19.3 {
            /*// 1.19.3 removed Item.Properties#tab; tabs pull their contents instead.
            created.put(id, new Item(new Item.Properties()));
            *///?} else {
            created.put(id, new Item(new Item.Properties().tab(BridgedItemGroup.getTab(id.getNamespace(), false))));
            //?}
        }

        items = Collections.unmodifiableMap(created);
    }

    public static Map<ResourceLocation, Item> items() {
        return items;
    }
}
