package net.pitan76.assetbridge.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.block.BridgedBlocks;

import java.util.Map;

@Mod(AssetBridge.MOD_ID)
public final class AssetBridgeForge {
    public AssetBridgeForge() {
        AssetBridge.init(FMLPaths.GAMEDIR.get());

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addGenericListener(Block.class, this::registerBlocks);
        bus.addGenericListener(Item.class, this::registerItems);
    }

    private void registerBlocks(RegistryEvent.Register<Block> event) {
        for (Map.Entry<ResourceLocation, Block> entry : BridgedBlocks.blocks().entrySet()) {
            event.getRegistry().register(entry.getValue().setRegistryName(entry.getKey()));
        }
        AssetBridge.LOGGER.info("Registered {} bridged blocks", BridgedBlocks.blocks().size());
    }

    private void registerItems(RegistryEvent.Register<Item> event) {
        for (Map.Entry<ResourceLocation, Item> entry : BridgedBlocks.items().entrySet()) {
            event.getRegistry().register(entry.getValue().setRegistryName(entry.getKey()));
        }
    }
}
