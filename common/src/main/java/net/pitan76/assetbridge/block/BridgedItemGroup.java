package net.pitan76.assetbridge.block;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the creative tab the bridged blocks are placed in.
 *
 * <p>1.18.2's {@code CreativeModeTab.TABS} is a fixed-size vanilla array, so the tab itself
 * has to be built with loader-specific API (Forge patches in a {@code String} constructor,
 * Fabric API offers a builder). Each platform creates it and hands it over here before
 * {@link net.pitan76.assetbridge.AssetBridge#init} runs.
 */
public final class BridgedItemGroup {
    /** Tab id used for the translation key and, on Fabric, the tab's resource location path. */
    public static final String NAME = "blocks";

    @Nullable
    private static CreativeModeTab tab;

    private BridgedItemGroup() {
    }

    public static void set(CreativeModeTab tab) {
        BridgedItemGroup.tab = tab;
    }

    /** Falls back to the building blocks tab if a platform could not provide one. */
    public static CreativeModeTab get() {
        return tab != null ? tab : CreativeModeTab.TAB_BUILDING_BLOCKS;
    }

    /**
     * Icon for the tab. Evaluated lazily by the renderer, so the bridged blocks already exist
     * by the time this is called.
     */
    public static ItemStack icon() {
        for (var item : BridgedBlocks.items().values()) {
            return new ItemStack(item);
        }
        for (var item : BridgedItems.items().values()) {
            return new ItemStack(item);
        }
        return new ItemStack(Items.BRICKS);
    }
}
