package net.pitan76.assetbridge.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.block.BridgedBlocks;
import net.pitan76.assetbridge.block.BridgedItemGroup;
import net.pitan76.assetbridge.block.BridgedItems;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.CutoutBlocksFeature;
import net.pitan76.assetbridge.feature.builtin.SplitTabByNamespaceFeature;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Mod(AssetBridge.MOD_ID)
public class AssetBridgeForge {
    /** Ids that lost the race against a real mod, so their items must be skipped too. */
    private final Set<ResourceLocation> skipped = new LinkedHashSet<>();

    public AssetBridgeForge() {
        // Load config first to check enabled features during tab setup
        Features.loadConfig(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get());

        // Forge patches a String constructor into CreativeModeTab; the label becomes the
        // translation key suffix, so it is kept identical to Fabric's tab ids.
        if (!Features.isEnabled(SplitTabByNamespaceFeature.ID)) {
            BridgedItemGroup.setBlocksTab(new CreativeModeTab(AssetBridge.MOD_ID + "." + BridgedItemGroup.BLOCKS) {
                @Override
                public ItemStack makeIcon() {
                    return BridgedItemGroup.blocksIcon();
                }
            });
            BridgedItemGroup.setItemsTab(new CreativeModeTab(AssetBridge.MOD_ID + "." + BridgedItemGroup.ITEMS) {
                @Override
                public ItemStack makeIcon() {
                    return BridgedItemGroup.itemsIcon();
                }
            });
        }

        BridgedItemGroup.setTabFactory((namespace, iconSupplier) -> new CreativeModeTab(AssetBridge.MOD_ID + "." + namespace) {
            @Override
            public ItemStack makeIcon() {
                return iconSupplier.get();
            }
        });

        AssetBridge.init(FMLPaths.GAMEDIR.get(), namespace -> ModList.get().isLoaded(namespace));

        // LOWEST so every other mod has registered by the time we check for id collisions.
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addGenericListener(Block.class, EventPriority.LOWEST, this::registerBlocks);
        bus.addGenericListener(Item.class, EventPriority.LOWEST, this::registerItems);
        bus.addListener(this::clientSetup);
    }

    private void clientSetup(final net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        if (Features.isEnabled(CutoutBlocksFeature.ID)) {
            event.enqueueWork(() -> {
                for (Map.Entry<ResourceLocation, Block> entry : BridgedBlocks.blocks().entrySet()) {
                    if (BridgedBlocks.isCutout(entry.getKey())) {
                        net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(entry.getValue(), net.minecraft.client.renderer.RenderType.cutoutMipped());
                    }
                }
            });
        }
    }

    private void registerBlocks(RegistryEvent.Register<Block> event) {
        for (Map.Entry<ResourceLocation, Block> entry : BridgedBlocks.blocks().entrySet()) {
            if (event.getRegistry().containsKey(entry.getKey())) {
                AssetBridge.LOGGER.info("Skipping {}: already registered by another mod", entry.getKey());
                skipped.add(entry.getKey());
                continue;
            }
            event.getRegistry().register(entry.getValue().setRegistryName(entry.getKey()));
        }
        AssetBridge.LOGGER.info("Registered {} bridged blocks", BridgedBlocks.blocks().size() - skipped.size());
    }

    private void registerItems(RegistryEvent.Register<Item> event) {
        for (Map.Entry<ResourceLocation, Item> entry : BridgedBlocks.items().entrySet()) {
            if (skipped.contains(entry.getKey()) || event.getRegistry().containsKey(entry.getKey())) continue;
            event.getRegistry().register(entry.getValue().setRegistryName(entry.getKey()));
        }

        int registered = 0;
        for (Map.Entry<ResourceLocation, Item> entry : BridgedItems.items().entrySet()) {
            if (event.getRegistry().containsKey(entry.getKey())) {
                AssetBridge.LOGGER.info("Skipping item {}: already registered by another mod", entry.getKey());
                continue;
            }
            event.getRegistry().register(entry.getValue().setRegistryName(entry.getKey()));
            registered++;
        }
        AssetBridge.LOGGER.info("Registered {} bridged items", registered);
    }
}
