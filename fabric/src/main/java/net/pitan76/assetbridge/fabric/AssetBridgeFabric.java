package net.pitan76.assetbridge.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.block.BridgedBlocks;

import java.util.Map;

public final class AssetBridgeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AssetBridge.init(FabricLoader.getInstance().getGameDir());

        // Mod initialisation runs before the registries freeze, so direct registration is fine.
        for (Map.Entry<ResourceLocation, Block> entry : BridgedBlocks.blocks().entrySet()) {
            Registry.register(Registry.BLOCK, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<ResourceLocation, Item> entry : BridgedBlocks.items().entrySet()) {
            Registry.register(Registry.ITEM, entry.getKey(), entry.getValue());
        }
        AssetBridge.LOGGER.info("Registered {} bridged blocks", BridgedBlocks.blocks().size());
    }
}
