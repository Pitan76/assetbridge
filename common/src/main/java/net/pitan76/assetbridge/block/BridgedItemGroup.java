package net.pitan76.assetbridge.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.pitan76.assetbridge.feature.FeatureConfig;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Holds the creative tabs the bridged content is placed in.
 * Can be split by block/item type, or split by namespace (mod).
 */
public class BridgedItemGroup {
    /** Tab ids, used for the translation keys and, on Fabric, the tabs' resource locations. */
    public static final String BLOCKS = "blocks";
    public static final String ITEMS = "items";

    @Nullable
    private static CreativeModeTab blocksTab;
    @Nullable
    private static CreativeModeTab itemsTab;

    private static final Map<String, CreativeModeTab> namespaceTabs = new LinkedHashMap<>();

    public interface TabFactory {
        CreativeModeTab create(String namespace, ItemStack icon);
    }

    @Nullable
    private static TabFactory tabFactory;

    private BridgedItemGroup() {
    }

    public static void setBlocksTab(CreativeModeTab tab) {
        blocksTab = tab;
    }

    public static void setItemsTab(CreativeModeTab tab) {
        itemsTab = tab;
    }

    public static void setTabFactory(TabFactory factory) {
        tabFactory = factory;
    }

    /** Falls back to a vanilla tab if a platform could not provide one. */
    public static CreativeModeTab blocks() {
        return blocksTab != null ? blocksTab : CreativeModeTab.TAB_BUILDING_BLOCKS;
    }

    public static CreativeModeTab items() {
        return itemsTab != null ? itemsTab : CreativeModeTab.TAB_MISC;
    }

    public static CreativeModeTab getTab(String namespace, boolean isBlock) {
        if (!FeatureConfig.isSplitTabByNamespace()) {
            return isBlock ? blocks() : items();
        }
        CreativeModeTab tab = namespaceTabs.get(namespace);
        return tab != null ? tab : (isBlock ? blocks() : items());
    }

    public static void initTabs(Set<String> namespaces) {
        LanguageHelper.injectTranslation("itemGroup.assetbridge.blocks", "Asset Bridge Blocks");
        LanguageHelper.injectTranslation("itemGroup.assetbridge.items", "Asset Bridge Items");

        if (!FeatureConfig.isSplitTabByNamespace() || tabFactory == null) {
            return;
        }
        for (String namespace : namespaces) {
            ItemStack icon = namespaceIcon(namespace);
            CreativeModeTab tab = tabFactory.create(namespace, icon);
            if (tab != null) {
                namespaceTabs.put(namespace, tab);
                LanguageHelper.injectTranslation("itemGroup.assetbridge." + namespace, "Asset Bridge: " + capitalize(namespace));
            }
        }
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] parts = str.split("[_-]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /**
     * Icons are evaluated lazily by the renderer, so the bridged content already exists by
     * the time these are called.
     */
    public static ItemStack blocksIcon() {
        return firstOf(BridgedBlocks.items().values());
    }

    public static ItemStack itemsIcon() {
        return firstOf(BridgedItems.items().values());
    }

    public static ItemStack namespaceIcon(String namespace) {
        for (Map.Entry<ResourceLocation, Item> entry : BridgedBlocks.items().entrySet()) {
            if (entry.getKey().getNamespace().equals(namespace)) {
                return new ItemStack(entry.getValue());
            }
        }
        for (Map.Entry<ResourceLocation, Item> entry : BridgedItems.items().entrySet()) {
            if (entry.getKey().getNamespace().equals(namespace)) {
                return new ItemStack(entry.getValue());
            }
        }
        return new ItemStack(Items.BRICKS);
    }

    private static ItemStack firstOf(Collection<? extends Item> candidates) {
        for (Item item : candidates) {
            return new ItemStack(item);
        }
        return new ItemStack(Items.BRICKS);
    }
}
