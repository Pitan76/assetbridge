package net.pitan76.assetbridge.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.SplitTabByNamespaceFeature;
import net.pitan76.assetbridge.asset.AssetPath;
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

    private static java.util.function.Function<String, String> modNameProvider = BridgedItemGroup::capitalize;

    public interface TabFactory {
        CreativeModeTab create(String namespace, java.util.function.Supplier<ItemStack> iconSupplier);
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

    public static void setModNameProvider(java.util.function.Function<String, String> provider) {
        modNameProvider = provider;
    }

    /** Falls back to a vanilla tab if a platform could not provide one. */
    public static CreativeModeTab blocks() {
        return blocksTab != null ? blocksTab : CreativeModeTab.TAB_BUILDING_BLOCKS;
    }

    public static CreativeModeTab items() {
        return itemsTab != null ? itemsTab : CreativeModeTab.TAB_MISC;
    }

    /** The namespace's own tab when that feature is on and it got one; the shared tab otherwise. */
    public static CreativeModeTab getTab(String namespace, boolean isBlock) {
        CreativeModeTab tab = Features.isEnabled(SplitTabByNamespaceFeature.ID)
                ? namespaceTabs.get(namespace)
                : null;
        if (tab != null) return tab;
        return isBlock ? blocks() : items();
    }

    public static void initTabs(Set<String> namespaces) {
        com.google.gson.JsonObject langJson = new com.google.gson.JsonObject();
        langJson.addProperty("itemGroup.assetbridge.blocks", "Asset Bridge Blocks");
        langJson.addProperty("itemGroup.assetbridge.items", "Asset Bridge Items");

        if (!Features.isEnabled(SplitTabByNamespaceFeature.ID) || tabFactory == null) {
            registerLang(langJson);
            return;
        }
        for (String namespace : namespaces) {
            CreativeModeTab tab = tabFactory.create(namespace, () -> namespaceIcon(namespace));
            if (tab != null) {
                namespaceTabs.put(namespace, tab);
                langJson.addProperty("itemGroup.assetbridge." + namespace, "Asset Bridge: " + modNameProvider.apply(namespace));
            }
        }
        registerLang(langJson);
    }

    private static void registerLang(com.google.gson.JsonObject langJson) {
        byte[] data = net.pitan76.assetbridge.util.Json.toString(langJson).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        net.pitan76.assetbridge.AssetBridge.assets().putResource(
                new AssetPath(AssetPath.PackKind.CLIENT, "assetbridge", "lang/en_us.json"), data);
        net.pitan76.assetbridge.AssetBridge.assets().putResource(
                new AssetPath(AssetPath.PackKind.CLIENT, "assetbridge", "lang/ja_jp.json"), data);
    }

    public static String capitalize(String str) {
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

    /** Prefers a block, which reads better as a tab icon than a loose item. */
    public static ItemStack namespaceIcon(String namespace) {
        Item item = firstIn(BridgedBlocks.items(), namespace);
        if (item == null) item = firstIn(BridgedItems.items(), namespace);
        return item != null ? new ItemStack(item) : fallbackIcon();
    }

    @Nullable
    private static Item firstIn(Map<ResourceLocation, Item> items, String namespace) {
        for (Map.Entry<ResourceLocation, Item> entry : items.entrySet()) {
            if (entry.getKey().getNamespace().equals(namespace)) return entry.getValue();
        }
        return null;
    }

    private static ItemStack firstOf(Collection<? extends Item> candidates) {
        for (Item item : candidates) {
            return new ItemStack(item);
        }
        return fallbackIcon();
    }

    /** Shown when a tab has no bridged content of its own to represent it. */
    private static ItemStack fallbackIcon() {
        return new ItemStack(Items.BRICKS);
    }
}
