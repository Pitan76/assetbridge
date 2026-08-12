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
import net.pitan76.assetbridge.util.IdUtil;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Mod(AssetBridge.MOD_ID)
public class AssetBridgeForge {
    /** Ids that lost the race against a real mod, so their items must be skipped too. */
    private final Set<ResourceLocation> skipped = new LinkedHashSet<>();

    private static boolean featuresApplied = false;

    private static void ensureFeaturesApplied() {
        if (!featuresApplied) {
            featuresApplied = true;
            AssetBridge.applyFeatures(
                    FMLPaths.GAMEDIR.get(),
                    namespace -> ModList.get().isLoaded(namespace));
        }
    }

    //? if >=1.20.1 {
    /*private static final Map<ResourceLocation, CreativeModeTab> TABS_TO_REGISTER = new LinkedHashMap<>();
    private static final Map<net.minecraft.resources.ResourceKey<CreativeModeTab>, Supplier<java.util.List<Item>>> TAB_CONTENTS = new java.util.HashMap<>();

    private static CreativeModeTab createTab(
            String path,
            Supplier<ItemStack> icon,
            Supplier<java.util.List<Item>> contents) {
        ResourceLocation id = IdUtil.of(AssetBridge.MOD_ID, path);
        net.minecraft.resources.ResourceKey<CreativeModeTab> key =
                net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, id);

        CreativeModeTab tab = CreativeModeTab.builder()
                .title(net.minecraft.network.chat.Component.translatable("itemGroup." + AssetBridge.MOD_ID + "." + path))
                .icon(icon)
                .build();

        TABS_TO_REGISTER.put(id, tab);
        TAB_CONTENTS.put(key, contents);
        return tab;
    }
    
    *///?}

    public AssetBridgeForge() {
        // Load config first to check enabled features during tab setup
        Features.loadConfig(net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get());

        // Forge patches a String constructor into CreativeModeTab; the label becomes the
        // translation key suffix, so it is kept identical to Fabric's tab ids.
        //? if >=1.20.1 {
        /*if (!Features.isEnabled(SplitTabByNamespaceFeature.ID)) {
            BridgedItemGroup.setBlocksTab(createTab(
                    BridgedItemGroup.BLOCKS,
                    BridgedItemGroup::blocksIcon,
                    () -> BridgedItemGroup.sharedTabContents(true)));
            BridgedItemGroup.setItemsTab(createTab(
                    BridgedItemGroup.ITEMS,
                    BridgedItemGroup::itemsIcon,
                    () -> BridgedItemGroup.sharedTabContents(false)));
        }

        BridgedItemGroup.setTabFactory((namespace, iconSupplier) -> createTab(
                namespace,
                iconSupplier,
                () -> BridgedItemGroup.namespaceTabContents(namespace)));
        *///?} else {
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
        //?}

        BridgedItemGroup.setModNameProvider(namespace -> ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(BridgedItemGroup.capitalize(namespace)));

        AssetBridge.init(FMLPaths.GAMEDIR.get(), namespace -> ModList.get().isLoaded(namespace));

        // LOWEST so every other mod has registered by the time we check for id collisions.
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        //? if >=1.20.1 {
        /*bus.addListener(EventPriority.LOWEST, this::onRegister);
        bus.addListener(this::onBuildCreativeModeTabContents);
        *///?} else if >=1.19 {
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
        ensureFeaturesApplied();
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
        ensureFeaturesApplied();
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

    //? if >=1.20.1 {
    /*private void onRegister(net.minecraftforge.registries.RegisterEvent event) {
        if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.BLOCK)) {
            registerBlocksInto(
                    id -> net.minecraftforge.registries.ForgeRegistries.BLOCKS.containsKey(id),
                    (id, block) -> event.register(net.minecraft.core.registries.Registries.BLOCK, id, () -> block));
        } else if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.ITEM)) {
            registerItemsInto(
                    id -> net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id),
                    (id, item) -> event.register(net.minecraft.core.registries.Registries.ITEM, id, () -> item));
        } else if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB)) {
            TABS_TO_REGISTER.forEach((id, tab) -> {
                event.register(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, id, () -> tab);
            });
        }
    }

    private void onBuildCreativeModeTabContents(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        Supplier<java.util.List<Item>> contents = TAB_CONTENTS.get(event.getTabKey());
        if (contents != null) {
            contents.get().forEach(event::accept);
        }
    }
    *///?} else if >=1.19 {
    /*private void onRegister(net.minecraftforge.registries.RegisterEvent event) {
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
    */
    //?} else {
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
