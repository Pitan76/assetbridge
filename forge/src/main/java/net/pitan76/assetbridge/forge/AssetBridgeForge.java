package net.pitan76.assetbridge.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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
import java.util.function.BiConsumer;
import java.util.function.Predicate;

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

        BridgedItemGroup.setModNameProvider(namespace -> ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(BridgedItemGroup.capitalize(namespace)));

        AssetBridge.init(FMLPaths.GAMEDIR.get(), namespace -> ModList.get().isLoaded(namespace));

        // LOWEST so every other mod has registered by the time we check for id collisions.
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        //? if >=1.19 {
        /*bus.addListener(EventPriority.LOWEST, this::onRegister);
        *///?} else {
        bus.addGenericListener(Block.class, EventPriority.LOWEST, this::registerBlocks);
        bus.addGenericListener(Item.class, EventPriority.LOWEST, this::registerItems);
        //?}
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

    // ---------------------------------------------------------------------------
    // Registration policy. Version-independent: it decides *what* to register and
    // which ids to skip. Only the two primitives it is handed -- "is this id taken"
    // and "register this value" -- differ between Minecraft versions, so adding a
    // version means supplying those two, not reworking the policy.
    // ---------------------------------------------------------------------------

    private void registerBlocksInto(Predicate<ResourceLocation> taken,
                                    BiConsumer<ResourceLocation, Block> register) {
        for (Map.Entry<ResourceLocation, Block> entry : BridgedBlocks.blocks().entrySet()) {
            if (taken.test(entry.getKey())) {
                AssetBridge.LOGGER.info("Skipping {}: already registered by another mod", entry.getKey());
                skipped.add(entry.getKey());
                continue;
            }
            register.accept(entry.getKey(), entry.getValue());
        }
        AssetBridge.LOGGER.info("Registered {} bridged blocks", BridgedBlocks.blocks().size() - skipped.size());
    }

    private void registerItemsInto(Predicate<ResourceLocation> taken,
                                   BiConsumer<ResourceLocation, Item> register) {
        for (Map.Entry<ResourceLocation, Item> entry : BridgedBlocks.items().entrySet()) {
            if (skipped.contains(entry.getKey()) || taken.test(entry.getKey())) continue;
            register.accept(entry.getKey(), entry.getValue());
        }

        int registered = 0;
        for (Map.Entry<ResourceLocation, Item> entry : BridgedItems.items().entrySet()) {
            if (taken.test(entry.getKey())) {
                AssetBridge.LOGGER.info("Skipping item {}: already registered by another mod", entry.getKey());
                continue;
            }
            register.accept(entry.getKey(), entry.getValue());
            registered++;
        }
        AssetBridge.LOGGER.info("Registered {} bridged items", registered);
    }

    // ---------------------------------------------------------------------------
    // Version-specific glue. Types are fully qualified so the imports stay shared.
    // ---------------------------------------------------------------------------

    //? if >=1.19 {
    /*// 1.19 replaced the per-registry generic events with a single RegisterEvent and
    // dropped Block#setRegistryName, so ids are passed to the event instead.
    private void onRegister(net.minecraftforge.registries.RegisterEvent event) {
        if (event.getRegistryKey().equals(net.minecraft.core.Registry.BLOCK_REGISTRY)) {
            registerBlocksInto(
                    id -> net.minecraftforge.registries.ForgeRegistries.BLOCKS.containsKey(id),
                    (id, block) -> event.register(net.minecraft.core.Registry.BLOCK_REGISTRY, id, () -> block));
        } else if (event.getRegistryKey().equals(net.minecraft.core.Registry.ITEM_REGISTRY)) {
            registerItemsInto(
                    id -> net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id),
                    (id, item) -> event.register(net.minecraft.core.Registry.ITEM_REGISTRY, id, () -> item));
        }
    }
    *///?} else {
    private void registerBlocks(net.minecraftforge.event.RegistryEvent.Register<Block> event) {
        registerBlocksInto(
                id -> event.getRegistry().containsKey(id),
                (id, block) -> event.getRegistry().register(block.setRegistryName(id)));
    }

    private void registerItems(net.minecraftforge.event.RegistryEvent.Register<Item> event) {
        registerItemsInto(
                id -> event.getRegistry().containsKey(id),
                (id, item) -> event.getRegistry().register(item.setRegistryName(id)));
    }
    //?}
}
