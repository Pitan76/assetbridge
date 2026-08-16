package net.pitan76.assetbridge.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.block.BridgedBlocks;
import net.pitan76.assetbridge.block.BridgedItemGroup;
import net.pitan76.assetbridge.block.BridgedItems;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.SplitTabByNamespaceFeature;
import net.pitan76.assetbridge.util.IdUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class AssetBridgeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Load config first to check enabled features during tab setup
        Features.loadConfig(FabricLoader.getInstance().getGameDir());

        // The tabs have to exist before the items are built.
        if (Features.isDisabled(SplitTabByNamespaceFeature.ID)) {
            BridgedItemGroup.setBlocksTab(createTab(
                    BridgedItemGroup.BLOCKS, BridgedItemGroup::blocksIcon,
                    () -> BridgedItemGroup.sharedTabContents(true)));
            BridgedItemGroup.setItemsTab(createTab(
                    BridgedItemGroup.ITEMS, BridgedItemGroup::itemsIcon,
                    () -> BridgedItemGroup.sharedTabContents(false)));
        }

        BridgedItemGroup.setTabFactory(
                (namespace, iconSupplier) ->
                createTab(namespace, iconSupplier,
                        () -> BridgedItemGroup.namespaceTabContents(namespace)));

        BridgedItemGroup.setModNameProvider(namespace -> FabricLoader.getInstance().getModContainer(namespace)
                .map(container -> container.getMetadata().getName())
                .orElse(BridgedItemGroup.capitalize(namespace)));

        AssetBridge.init(FabricLoader.getInstance().getGameDir(),
                namespace -> FabricLoader.getInstance().isModLoaded(namespace));
        AssetBridge.applyFeatures(FabricLoader.getInstance().getGameDir(),
                namespace -> FabricLoader.getInstance().isModLoaded(namespace));

        // Mod initialisation runs before the registries freeze, so direct registration is fine.
        int registeredBlocks = 0;
        for (Map.Entry<ResourceLocation, Block> entry : BridgedBlocks.blocks().entrySet()) {
            // Mod init order is not controllable, so a mod loaded after us can still claim the
            // same id. That is the desired outcome anyway: the real mod should win.
            if (blockRegistry().containsKey(entry.getKey())) {
                AssetBridge.LOGGER.info("Skipping {}: already registered by another mod", entry.getKey());
                continue;
            }

            Registry.register(blockRegistry(), entry.getKey(), entry.getValue());
            Registry.register(itemRegistry(), entry.getKey(), BridgedBlocks.items().get(entry.getKey()));
            registeredBlocks++;
        }

        int registeredItems = 0;
        for (Map.Entry<ResourceLocation, Item> entry : BridgedItems.items().entrySet()) {
            if (itemRegistry().containsKey(entry.getKey())) {
                AssetBridge.LOGGER.info("Skipping item {}: already registered by another mod", entry.getKey());
                continue;
            }

            Registry.register(itemRegistry(), entry.getKey(), entry.getValue());
            registeredItems++;
        }
        AssetBridge.LOGGER.info("Registered {} bridged blocks and {} items", registeredBlocks, registeredItems);
    }

    // ---------------------------------------------------------------------------
    // Version-specific glue. 1.19.3 moved the vanilla registries onto
    // BuiltInRegistries and turned creative tabs into registry entries that pull
    // their contents from an event instead of being named by each item.
    // ---------------------------------------------------------------------------

    //? if >=1.19.3 {
    /*private static net.minecraft.core.Registry<Block> blockRegistry() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK;
    }

    private static net.minecraft.core.Registry<Item> itemRegistry() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM;
    }

    // Builds a tab, registers it, and points it at the contents it should collect.
    private static CreativeModeTab createTab(
            String path,
            Supplier<ItemStack> icon,
            Supplier<List<Item>> contents) {
        ResourceLocation id = modId(path);
        net.minecraft.resources.ResourceKey<CreativeModeTab> key =
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, id);

        CreativeModeTab tab = net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
                .title(net.minecraft.network.chat.Component.translatable("itemGroup." + AssetBridge.MOD_ID + "." + path))
                .icon(icon)
                .build();
        Registry.register(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB, key, tab);

        // Evaluated when the tab is filled, which is after the bridged items exist.
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(key)
                .register(entries -> contents.get().forEach(entries::accept));
        return tab;
    }
    *///?} else {
    private static Registry<Block> blockRegistry() {
        return Registry.BLOCK;
    }

    private static Registry<Item> itemRegistry() {
        return Registry.ITEM;
    }

    /** Up to 1.19.2 a tab only needs to exist; the items name it themselves. */
    private static CreativeModeTab createTab(
            String path,
            Supplier<ItemStack> icon,
            Supplier<List<Item>> contents) {
        return net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
                .create(modId(path))
                .icon(icon)
                .build();
    }
    //?}

    // 1.21 made the ResourceLocation constructor private. Its own chain, because the
    // tab block above is already commented out per version and comments do not nest.
    //? if >=1.21 {
    /*private static ResourceLocation modId(String path) {
        return ResourceLocation.fromNamespaceAndPath(AssetBridge.MOD_ID, path);
    }
    *///?} else {
    private static ResourceLocation modId(String path) {
        return IdUtil.of(AssetBridge.MOD_ID, path);
    }
    //?}
}
