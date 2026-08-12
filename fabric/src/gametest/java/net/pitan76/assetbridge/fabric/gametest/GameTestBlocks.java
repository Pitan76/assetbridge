package net.pitan76.assetbridge.fabric.gametest;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.ItemBlock;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.block.Block;
import net.pitan76.assetbridge.asset.BridgedProperty;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import net.pitan76.assetbridge.block.BridgedBlock;

import java.util.List;
import java.util.Objects;

/**
 * Registers bridged blocks with known state definitions so the game tests have something
 * stable to place. The real blocks come from archives in {@code mods/assetbridge/}, which a
 * test environment has no reason to contain, so they are built here by hand instead.
 */
public class GameTestBlocks implements ModInitializer {
    public static final String NAMESPACE = "assetbridge-gametest";

    public static final Block FACING = create("facing",
            property("facing", List.of("north", "south", "west", "east", "up", "down")));
    public static final Block HORIZONTAL_FACING = create("horizontal_facing",
            property("facing", List.of("north", "south", "west", "east")));
    public static final Block AXIS = create("axis",
            property("axis", List.of("x", "y", "z")));
    public static final Block CUSTOM_PROPERTY = create("custom_property",
            property("variant", List.of("plain", "carved")));

    @Override
    public void onInitialize() {
        // Mod init still runs before the registries freeze, same as the main mod.
        register("facing", FACING);
        register("horizontal_facing", HORIZONTAL_FACING);
        register("axis", AXIS);
        register("custom_property", CUSTOM_PROPERTY);
    }

    private static void register(String name, Block block) {
        ResourceLocation id = id(name);
        Registry.register(Registry.BLOCK, id, block);
        Registry.register(Registry.ITEM, id,
                new ItemBlock(block, new Item.Properties().tab(CreativeModeTab.TAB_BUILDING_BLOCKS)));
    }

    /** The id is now part of a block's properties, so it has to be known before construction. */
    private static Block create(String name, BridgedProperty... properties) {
        return BridgedBlock.create(id(name), new BridgedStateDefinition(List.of(properties)));
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(NAMESPACE, name);
    }

    private static BridgedProperty property(String name, List<String> values) {
        return Objects.requireNonNull(BridgedProperty.of(name, values),
                "test property " + name + " is not representable");
    }
}
