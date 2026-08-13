package net.pitan76.assetbridge.block;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.SplitTabByNamespaceFeature;
import net.pitan76.assetbridge.asset.AssetPath;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Holds the creative tabs the bridged content is placed in.
 * Can be split by block/item type, or split by namespace (mod).
 *
 * <p>1.12.2's {@code CreativeTabs} assigns each tab a fixed slot out of a small, effectively
 * static array (its constructor takes an explicit {@code int} index), unlike later versions'
 * open-ended tab registry. Building one from common code -- which is compiled against plain
 * vanilla, with no Forge/Fabric patches -- is a platform concern (each loader has its own way
 * of finding the next free index), so this class still only holds and looks up tabs; it never
 * constructs one itself. Until a platform supplies tabs via {@link #setTabFactory} or
 * {@link #setBlocksTab}/{@link #setItemsTab}, everything falls back to vanilla's own tabs.
 */
public class BridgedItemGroup {
    /** Tab ids, used for the translation keys and, on Fabric, the tabs' resource locations. */
    public static final String BLOCKS = "blocks";
    public static final String ITEMS = "items";

    @Nullable
    private static CreativeTabs blocksTab;
    @Nullable
    private static CreativeTabs itemsTab;

    /**
     * Whether {@code CreativeTabs} (the class, not a specific tab) can even be touched on the
     * running platform. False on Legacy Fabric: {@code common} is compiled against MCP, where
     * this class is named {@code CreativeTabs}; Legacy Fabric's Legacy Yarn mapping names the
     * same game class {@code ItemGroup} instead, and {@code common} runs there as a plain,
     * unremapped dependency, so referencing {@code CreativeTabs} throws
     * {@code NoClassDefFoundError} the moment the call actually happens. Callers must check this
     * before calling {@link #getTab}/{@link #blocks}/{@link #items} (or anything that returns a
     * {@code CreativeTabs}) so that call is never reached on that platform.
     */
    private static boolean creativeTabsSupported = true;

    public static void setCreativeTabsSupported(boolean supported) {
        creativeTabsSupported = supported;
    }

    public static boolean creativeTabsSupported() {
        return creativeTabsSupported;
    }

    private static final Map<String, CreativeTabs> namespaceTabs = new LinkedHashMap<>();

    private static Function<String, String> modNameProvider = BridgedItemGroup::capitalize;

    /** Display names read from the archives themselves, keyed by namespace. */
    private static Map<String, String> archiveModNames = Collections.emptyMap();

    public interface TabFactory {
        CreativeTabs create(String namespace, Supplier<ItemStack> iconSupplier);
    }

    @Nullable
    private static TabFactory tabFactory;

    private BridgedItemGroup() {
    }

    public static void setBlocksTab(@Nullable CreativeTabs tab) {
        blocksTab = tab;
    }

    public static void setItemsTab(@Nullable CreativeTabs tab) {
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
    public static CreativeTabs namespaceTab(String namespace) {
        return Features.isEnabled(SplitTabByNamespaceFeature.ID) ? namespaceTabs.get(namespace) : null;
    }

    /** The namespaces that were given their own tab, in registration order. */
    public static Set<String> tabbedNamespaces() {
        return Collections.unmodifiableSet(namespaceTabs.keySet());
    }

    // ---------------------------------------------------------------------------
    // Which items belong in which tab. 1.12.2 always declares a tab at construction time
    // (there is no later-version "tab pulls its contents from an event" model), so only
    // getTab() below is actually used; the two queries are kept anyway so this class' shape
    // matches every other supported version's.
    // ---------------------------------------------------------------------------

    /** Everything that belongs in the shared blocks or items tab. */
    public static List<Item> sharedTabContents(boolean isBlock) {
        List<Item> contents = new ArrayList<>();
        for (Map.Entry<String, ? extends Item> entry : sourceFor(isBlock).entrySet()) {
            if (namespaceTab(net.pitan76.assetbridge.util.Ids.namespaceOf(entry.getKey())) == null) contents.add(entry.getValue());
        }
        return contents;
    }

    /** Everything that belongs in one namespace's own tab: its blocks first, then its items. */
    public static List<Item> namespaceTabContents(String namespace) {
        List<Item> contents = new ArrayList<>();
        for (boolean isBlock : new boolean[]{true, false}) {
            for (Map.Entry<String, ? extends Item> entry : sourceFor(isBlock).entrySet()) {
                if (net.pitan76.assetbridge.util.Ids.namespaceOf(entry.getKey()).equals(namespace)) contents.add(entry.getValue());
            }
        }
        return contents;
    }

    private static Map<String, ? extends Item> sourceFor(boolean isBlock) {
        return isBlock ? BridgedBlocks.items() : BridgedItems.items();
    }

    /** Falls back to a vanilla tab if a platform could not provide one. */
    public static CreativeTabs blocks() {
        return blocksTab != null ? blocksTab : CreativeTabs.BUILDING_BLOCKS;
    }

    public static CreativeTabs items() {
        return itemsTab != null ? itemsTab : CreativeTabs.MISC;
    }

    /** The tab an item/block declares at construction time. */
    public static CreativeTabs getTab(String namespace, boolean isBlock) {
        CreativeTabs tab = namespaceTab(namespace);
        if (tab != null) return tab;
        return isBlock ? blocks() : items();
    }

    public static void initTabs(Set<String> namespaces, Map<String, String> modNames) {
        archiveModNames = Collections.unmodifiableMap(new LinkedHashMap<>(modNames));

        com.google.gson.JsonObject langJson = new com.google.gson.JsonObject();
        langJson.addProperty("itemGroup.assetbridge.blocks", "Asset Bridge Blocks");
        langJson.addProperty("itemGroup.assetbridge.items", "Asset Bridge Items");

        if (!Features.isEnabled(SplitTabByNamespaceFeature.ID) || tabFactory == null) {
            registerLang(langJson);
            return;
        }
        for (String namespace : namespaces) {
            CreativeTabs tab = tabFactory.create(namespace, () -> namespaceIcon(namespace));
            if (tab != null) {
                namespaceTabs.put(namespace, tab);
                langJson.addProperty("itemGroup.assetbridge." + namespace, "Asset Bridge: " + modName(namespace));
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
    private static Item firstIn(Map<String, Item> items, String namespace) {
        for (Map.Entry<String, Item> entry : items.entrySet()) {
            if (net.pitan76.assetbridge.util.Ids.namespaceOf(entry.getKey()).equals(namespace)) return entry.getValue();
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
        return new ItemStack(Items.BRICK);
    }
}
