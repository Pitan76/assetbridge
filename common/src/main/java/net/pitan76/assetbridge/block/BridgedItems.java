package net.pitan76.assetbridge.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.asset.BridgedItemAsset;

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

    public static void create(AssetBundle bundle) {
        Map<ResourceLocation, Item> created = new LinkedHashMap<>();

        for (BridgedItemAsset asset : bundle.items()) {
            ResourceLocation id = ResourceLocation.tryParse(asset.id());
            if (id == null) {
                AssetBridge.LOGGER.warn("Skipping item with invalid id '{}' from {}", asset.id(), asset.sourceArchive());
                continue;
            }
            created.put(id, new Item(new Item.Properties().tab(BridgedItemGroup.items())));
        }

        items = Collections.unmodifiableMap(created);
    }

    public static Map<ResourceLocation, Item> items() {
        return items;
    }
}
