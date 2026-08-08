package net.pitan76.assetbridge.block;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Holds the creative tabs the bridged content is placed in: one for blocks, one for items.
 * They are kept apart because an item-heavy mod can contribute hundreds of entries, which
 * would bury the blocks in a shared tab.
 *
 * <p>1.18.2's {@code CreativeModeTab.TABS} is a fixed-size vanilla array, so the tabs
 * themselves have to be built with loader-specific API (Forge patches in a {@code String}
 * constructor, Fabric API offers a builder). Each platform creates them and hands them over
 * here before {@link net.pitan76.assetbridge.AssetBridge#init} runs.
 */
public final class BridgedItemGroup {
    /** Tab ids, used for the translation keys and, on Fabric, the tabs' resource locations. */
    public static final String BLOCKS = "blocks";
    public static final String ITEMS = "items";

    @Nullable
    private static CreativeModeTab blocksTab;
    @Nullable
    private static CreativeModeTab itemsTab;

    private BridgedItemGroup() {
    }

    public static void setBlocksTab(CreativeModeTab tab) {
        blocksTab = tab;
    }

    public static void setItemsTab(CreativeModeTab tab) {
        itemsTab = tab;
    }

    /** Falls back to a vanilla tab if a platform could not provide one. */
    public static CreativeModeTab blocks() {
        return blocksTab != null ? blocksTab : CreativeModeTab.TAB_BUILDING_BLOCKS;
    }

    public static CreativeModeTab items() {
        return itemsTab != null ? itemsTab : CreativeModeTab.TAB_MISC;
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

    private static ItemStack firstOf(Collection<? extends Item> candidates) {
        for (Item item : candidates) {
            return new ItemStack(item);
        }
        return new ItemStack(Items.BRICKS);
    }
}
