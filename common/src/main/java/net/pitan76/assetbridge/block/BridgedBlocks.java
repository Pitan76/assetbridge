package net.pitan76.assetbridge.block;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.asset.BridgedBlockDefinition;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the {@link Block} instances for the bridged assets. Construction happens in common
 * code; the actual registry call is left to each platform, because Fabric registers during
 * mod init while Forge registers from a registry event.
 *
 * <p>Keyed by the plain {@code namespace:path} id string rather than
 * {@code net.minecraft.util.ResourceLocation}: that class is not named the same on every
 * platform this module is compiled once and shared across (Legacy Fabric's Legacy Yarn mapping
 * calls it {@code Identifier}), and this runs unremapped wherever {@code common} is consumed as
 * a plain dependency -- constructing it would throw {@code NoClassDefFoundError} there.
 */
public class BridgedBlocks {
    private static Map<String, Block> blocks = Collections.emptyMap();
    private static Map<String, Item> items = Collections.emptyMap();

    private BridgedBlocks() {
    }

    public static void create(BridgedAssetManager assets) {
        Map<String, Block> createdBlocks = new LinkedHashMap<>();
        Map<String, Item> createdItems = new LinkedHashMap<>();

        for (BridgedBlockDefinition asset : assets.blocks()) {
            String id = asset.id();
            if (id == null || id.indexOf(':') <= 0) {
                AssetBridge.LOGGER.warn("Skipping block with invalid id '{}' from {}", id, asset.sourceArchive());
                continue;
            }
            Block block = BridgedBlock.create(id, asset.states());
            createdBlocks.put(id, block);
            // 1.12.2's block item always declares its tab through the block's own
            // getCreativeTab(), which BridgedBlock already set; there is no later-version
            // Item.Properties#tab to set separately.
            createdItems.put(id, newBlockItem(block));
        }

        blocks = Collections.unmodifiableMap(createdBlocks);
        items = Collections.unmodifiableMap(createdItems);
    }

    public static Map<String, Block> blocks() {
        return blocks;
    }

    public static Map<String, Item> items() {
        return items;
    }

    public static boolean isCutout(String id) {
        return BlockConfig.isCutout(id);
    }

    /**
     * {@code new net.minecraft.item.ItemBlock(Block)} under MCP (Forge); the same class is
     * {@code net.minecraft.item.BlockItem} under Legacy Yarn (Legacy Fabric) -- a class-name
     * difference, not just a member rename, so this has to resolve the class itself
     * reflectively via whichever name actually exists on the running platform, the same reason
     * {@code BridgedBlock#rockMaterial()}/{@code #setHardness} do.
     */
    private static Item newBlockItem(Block block) {
        for (String className : new String[]{"net.minecraft.item.ItemBlock", "net.minecraft.item.BlockItem"}) {
            try {
                Class<?> clazz = Class.forName(className);
                Constructor<?> constructor = clazz.getConstructor(Block.class);
                return (Item) constructor.newInstance(block);
            } catch (ReflectiveOperationException ignored) {
                // Try the next mapping's name.
            }
        }
        throw new IllegalStateException("Neither net.minecraft.item.ItemBlock nor net.minecraft.item.BlockItem exists");
    }
}
