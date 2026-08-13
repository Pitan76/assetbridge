package net.pitan76.assetbridge.block;

import net.minecraft.item.Item;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.BridgedItemDefinition;
import net.pitan76.assetbridge.util.Ids;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the plain {@link Item} instances for item models that no bridged block claims.
 *
 * <p>Like the blocks, these carry no behaviour: they exist so the model and texture from the
 * external mod can be held, seen in the creative tab and used for screenshots.
 *
 * <p>Keyed by the plain {@code namespace:path} id string rather than
 * {@code net.minecraft.util.ResourceLocation} -- see {@link BridgedBlocks}'s class doc for why.
 */
public class BridgedItems {
    private static Map<String, Item> items = Collections.emptyMap();

    private BridgedItems() {
    }

    public static void create(BridgedAssetManager assets) {
        Map<String, Item> created = new LinkedHashMap<>();

        for (BridgedItemDefinition asset : assets.items()) {
            String id = asset.id();
            if (id == null || id.indexOf(':') <= 0) {
                AssetBridge.LOGGER.warn("Skipping item with invalid id '{}' from {}", id, asset.sourceArchive());
                continue;
            }
            Item item = new Item();
            item.setTranslationKey(id.replace(':', '.'));
            if (BridgedItemGroup.creativeTabsSupported()) {
                item.setCreativeTab(BridgedItemGroup.getTab(Ids.namespaceOf(id), false));
            } else {
                net.pitan76.assetbridge.util.DefaultCreativeTab.assignDefault(item, false);
            }
            created.put(id, item);
        }

        items = Collections.unmodifiableMap(created);
    }

    public static Map<String, Item> items() {
        return items;
    }
}
