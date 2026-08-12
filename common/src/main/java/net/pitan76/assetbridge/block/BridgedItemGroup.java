package net.pitan76.assetbridge.block;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.SplitTabByNamespaceFeature;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

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

    private static Function<String, String> modNameProvider = BridgedItemGroup::capitalize;

    /** Display names read from the archives themselves, keyed by namespace. */
    private static Map<String, String> archiveModNames = Map.of();

    public interface TabFactory {
        CreativeModeTab create(String namespace, Supplier<ItemStack> iconSupplier);
    }

    @Nullable
    private static TabFactory tabFactory;

    private BridgedItemGroup() {
    }

    public static void setBlocksTab(@Nullable CreativeModeTab tab) {
        blocksTab = tab;
    }

    public static void setItemsTab(@Nullable CreativeModeTab tab) {
        itemsTab = tab;
    }

    public static void setTabFactory(@Nullable TabFactory factory) {
        tabFactory = factory;
    }

    /**
     * The fallback used when the archive named no mod. The loader can only answer for mods
     * that are actually installed, which the bridged archives are not, so this is rarely
     * more than {@link #capitalize}.
     */
    public static void setModNameProvider(Function<String, String> provider) {
        modNameProvider = provider;
    }

    /**
     * What to call a namespace: the name the mod gives itself in its own metadata, else
     * whatever the platform can offer, else the namespace capitalised.
     */
    public static String modName(String namespace) {
        String declared = archiveModNames.get(namespace);
        return declared != null ? declared : modNameProvider.apply(namespace);
    }

    /** The namespace's own tab, when that feature is on and it got one. */
    @Nullable
    public static CreativeModeTab namespaceTab(String namespace) {
        return Features.isEnabled(SplitTabByNamespaceFeature.ID) ? namespaceTabs.get(namespace) : null;
    }

    /** The namespaces that were given their own tab, in registration order. */
    public static Set<String> tabbedNamespaces() {
        return Collections.unmodifiableSet(namespaceTabs.keySet());
    }

    // ---------------------------------------------------------------------------
    // Which items belong in which tab.
    //
    // Up to 1.19.2 an item declared its own tab through Item.Properties#tab, so this
    // was answered at construction time. 1.19.3 inverted it: a tab collects its
    // contents from an event, so the answer has to be available afterwards instead.
    // Both models are served from the queries below, which stay version-independent;
    // the loaders decide when to ask.
    // ---------------------------------------------------------------------------

    /** Everything that belongs in the shared blocks or items tab. */
    public static List<Item> sharedTabContents(boolean isBlock) {
        List<Item> contents = new ArrayList<>();
        for (Map.Entry<ResourceLocation, ? extends Item> entry : sourceFor(isBlock).entrySet()) {
            if (namespaceTab(entry.getKey().getNamespace()) == null) contents.add(entry.getValue());
        }
        return contents;
    }

    /** Everything that belongs in one namespace's own tab: its blocks first, then its items. */
    public static List<Item> namespaceTabContents(String namespace) {
        List<Item> contents = new ArrayList<>();
        for (boolean isBlock : new boolean[]{true, false}) {
            for (Map.Entry<ResourceLocation, ? extends Item> entry : sourceFor(isBlock).entrySet()) {
                if (entry.getKey().getNamespace().equals(namespace)) contents.add(entry.getValue());
            }
        }
        return contents;
    }

    private static Map<ResourceLocation, ? extends Item> sourceFor(boolean isBlock) {
        return isBlock ? BridgedBlocks.items() : BridgedItems.items();
    }

    //? if <1.19.3 {
    /** Falls back to a vanilla tab if a platform could not provide one. */
    public static CreativeModeTab blocks() {
        return blocksTab != null ? blocksTab : CreativeModeTab.TAB_BUILDING_BLOCKS;
    }

    public static CreativeModeTab items() {
        return itemsTab != null ? itemsTab : CreativeModeTab.TAB_MISC;
    }

    /** The tab an item declares at construction time. Gone from 1.19.3 onwards. */
    public static CreativeModeTab getTab(String namespace, boolean isBlock) {
        CreativeModeTab tab = namespaceTab(namespace);
        if (tab != null) return tab;
        return isBlock ? blocks() : items();
    }
    //?}

    public static void initTabs(Set<String> namespaces, Map<String, String> modNames) {
        archiveModNames = Map.copyOf(modNames);

        JsonObject langJson = new JsonObject();
        langJson.addProperty("itemGroup.assetbridge.blocks", "Asset Bridge: Blocks");
        langJson.addProperty("itemGroup.assetbridge.items", "Asset Bridge: Items");

        if (Features.isDisabled(SplitTabByNamespaceFeature.ID) || tabFactory == null) {
            registerLang(langJson);
            return;
        }
        for (String namespace : namespaces) {
            CreativeModeTab tab = tabFactory.create(namespace, () -> namespaceIcon(namespace));
            if (tab != null) {
                namespaceTabs.put(namespace, tab);
                langJson.addProperty("itemGroup.assetbridge." + namespace, "Asset Bridge: " + modName(namespace));
            }
        }
        registerLang(langJson);
    }

    private static void registerLang(JsonObject langJson) {
        byte[] data = Json.toString(langJson).getBytes(StandardCharsets.UTF_8);
        registerLang("en_us", data);
        registerLang("ja_jp", data);
    }

    private static void registerLang(String locale, byte[] data) {
        AssetBridge.assets().putResource(AssetPath.lang("assetbridge", locale), data);
    }

    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] parts = str.split("[_-]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(" ");
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
