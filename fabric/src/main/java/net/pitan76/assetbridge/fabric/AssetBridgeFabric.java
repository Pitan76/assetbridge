package net.pitan76.assetbridge.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.block.BridgedBlocks;
import net.pitan76.assetbridge.block.BridgedItemGroup;
import net.pitan76.assetbridge.block.BridgedItems;

import java.util.Map;

public class AssetBridgeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // The tabs have to exist before the items are built.
        BridgedItemGroup.setBlocksTab(FabricItemGroupBuilder
                .create(new ResourceLocation(AssetBridge.MOD_ID, BridgedItemGroup.BLOCKS))
                .icon(BridgedItemGroup::blocksIcon)
                .build());
        BridgedItemGroup.setItemsTab(FabricItemGroupBuilder
                .create(new ResourceLocation(AssetBridge.MOD_ID, BridgedItemGroup.ITEMS))
                .icon(BridgedItemGroup::itemsIcon)
                .build());

        BridgedItemGroup.setTabFactory((namespace, icon) -> FabricItemGroupBuilder
                .create(new ResourceLocation(AssetBridge.MOD_ID, namespace))
                .icon(() -> icon)
                .build());

        AssetBridge.init(FabricLoader.getInstance().getGameDir(),
                namespace -> FabricLoader.getInstance().isModLoaded(namespace));

        // Mod initialisation runs before the registries freeze, so direct registration is fine.
        int registeredBlocks = 0;
        for (Map.Entry<ResourceLocation, Block> entry : BridgedBlocks.blocks().entrySet()) {
            // Mod init order is not controllable, so a mod loaded after us can still claim the
            // same id. That is the desired outcome anyway: the real mod should win.
            if (Registry.BLOCK.containsKey(entry.getKey())) {
                AssetBridge.LOGGER.info("Skipping {}: already registered by another mod", entry.getKey());
                continue;
            }
            Registry.register(Registry.BLOCK, entry.getKey(), entry.getValue());
            Registry.register(Registry.ITEM, entry.getKey(), BridgedBlocks.items().get(entry.getKey()));
            registeredBlocks++;
        }

        int registeredItems = 0;
        for (Map.Entry<ResourceLocation, Item> entry : BridgedItems.items().entrySet()) {
            if (Registry.ITEM.containsKey(entry.getKey())) {
                AssetBridge.LOGGER.info("Skipping item {}: already registered by another mod", entry.getKey());
                continue;
            }
            Registry.register(Registry.ITEM, entry.getKey(), entry.getValue());
            registeredItems++;
        }
        AssetBridge.LOGGER.info("Registered {} bridged blocks and {} items", registeredBlocks, registeredItems);
    }
}
