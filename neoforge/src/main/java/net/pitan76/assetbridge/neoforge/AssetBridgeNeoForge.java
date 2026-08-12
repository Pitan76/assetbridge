package net.pitan76.assetbridge.neoforge;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.block.BridgedBlocks;
import net.pitan76.assetbridge.block.BridgedItemGroup;
import net.pitan76.assetbridge.block.BridgedItems;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.CutoutBlocksFeature;
import net.pitan76.assetbridge.feature.builtin.SplitTabByNamespaceFeature;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * NeoForge entry point. Only 1.21.1 is built from this branch, so unlike the Forge and
 * Fabric sources it carries no versioned comments.
 */
@Mod(AssetBridge.MOD_ID)
public class AssetBridgeNeoForge {
    /** Ids that lost the race against a real mod, so their items must be skipped too. */
    private final Set<ResourceLocation> skipped = new LinkedHashSet<>();

    /** Tabs built during construction, registered once RegisterEvent reaches that registry. */
    private final Map<String, CreativeModeTab> tabs = new LinkedHashMap<>();

    private static boolean featuresApplied = false;

    /**
     * The features are what actually build the blocks and items, so they have to have run
     * before anything is registered. Not done in the constructor: {@code ModList} only knows
     * every mod once all of them are constructed, and the features ask it which namespaces
     * are already taken. The first {@link RegisterEvent} is the earliest point that holds.
     */
    private static void ensureFeaturesApplied() {
        if (featuresApplied) return;
        featuresApplied = true;
        AssetBridge.applyFeatures(FMLPaths.GAMEDIR.get(), namespace -> ModList.get().isLoaded(namespace));
    }

    public AssetBridgeNeoForge(IEventBus modBus) {
        Features.loadConfig(FMLPaths.GAMEDIR.get());

        if (Features.isDisabled(SplitTabByNamespaceFeature.ID)) {
            BridgedItemGroup.setBlocksTab(buildTab(BridgedItemGroup.BLOCKS, BridgedItemGroup::blocksIcon));
            BridgedItemGroup.setItemsTab(buildTab(BridgedItemGroup.ITEMS, BridgedItemGroup::itemsIcon));
        }
        BridgedItemGroup.setTabFactory(this::buildTab);

        BridgedItemGroup.setModNameProvider(namespace -> ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(BridgedItemGroup.capitalize(namespace)));

        AssetBridge.init(FMLPaths.GAMEDIR.get(), namespace -> ModList.get().isLoaded(namespace));

        // LOWEST so every other mod has registered by the time we check for id collisions.
        modBus.addListener(EventPriority.LOWEST, this::onRegister);
        modBus.addListener(this::onBuildTabContents);
        modBus.addListener(this::clientSetup);
    }

    private CreativeModeTab buildTab(String path, Supplier<ItemStack> icon) {
        CreativeModeTab tab = CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + AssetBridge.MOD_ID + "." + path))
                .icon(icon)
                .build();
        tabs.put(path, tab);
        return tab;
    }

    private void onRegister(RegisterEvent event) {
        ensureFeaturesApplied();

        if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            tabs.forEach((path, tab) -> event.register(Registries.CREATIVE_MODE_TAB,
                    id(path), () -> tab));
            return;
        }
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            for (Map.Entry<ResourceLocation, Block> entry : BridgedBlocks.blocks().entrySet()) {
                if (BuiltInRegistries.BLOCK.containsKey(entry.getKey())) {
                    AssetBridge.LOGGER.info("Skipping {}: already registered by another mod", entry.getKey());
                    skipped.add(entry.getKey());
                    continue;
                }
                event.register(Registries.BLOCK, entry.getKey(), entry::getValue);
            }
            AssetBridge.LOGGER.info("Registered {} bridged blocks",
                    BridgedBlocks.blocks().size() - skipped.size());
            return;
        }
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            for (Map.Entry<ResourceLocation, Item> entry : BridgedBlocks.items().entrySet()) {
                if (skipped.contains(entry.getKey()) || BuiltInRegistries.ITEM.containsKey(entry.getKey())) continue;
                event.register(Registries.ITEM, entry.getKey(), entry::getValue);
            }

            int registered = 0;
            for (Map.Entry<ResourceLocation, Item> entry : BridgedItems.items().entrySet()) {
                if (BuiltInRegistries.ITEM.containsKey(entry.getKey())) {
                    AssetBridge.LOGGER.info("Skipping item {}: already registered by another mod", entry.getKey());
                    continue;
                }
                event.register(Registries.ITEM, entry.getKey(), entry::getValue);
                registered++;
            }
            AssetBridge.LOGGER.info("Registered {} bridged items", registered);
        }
    }

    /** 1.19.3 onwards a tab collects its own contents rather than items naming their tab. */
    private void onBuildTabContents(BuildCreativeModeTabContentsEvent event) {
        //? if >=26 {
        /*// 26.1 renamed ResourceKey#location to #identifier.
        ResourceLocation key = event.getTabKey().identifier();
        *///?} else {
        ResourceLocation key = event.getTabKey().location();
        //?}
        if (!AssetBridge.MOD_ID.equals(key.getNamespace())) return;

        String path = key.getPath();
        List<Item> contents;
        if (BridgedItemGroup.BLOCKS.equals(path)) {
            contents = BridgedItemGroup.sharedTabContents(true);
        } else if (BridgedItemGroup.ITEMS.equals(path)) {
            contents = BridgedItemGroup.sharedTabContents(false);
        } else {
            contents = BridgedItemGroup.namespaceTabContents(path);
        }
        contents.forEach(event::accept);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        if (Features.isDisabled(CutoutBlocksFeature.ID)) return;
        //? if >=26 {
        /*// 26.1 removed ItemBlockRenderTypes; the layer a block draws on now comes from its block
        // model JSON (`"render_type"`), so there is nothing to register from code. Assets from
        // pre-26 mods carry no such field, so CutoutBlocksFeature has no effect on this version
        // until the served model JSON is rewritten -- see memo/MC-26.1.2.md.
        *///?} else {
        event.enqueueWork(() -> {
            for (Map.Entry<ResourceLocation, Block> entry : BridgedBlocks.blocks().entrySet()) {
                if (BridgedBlocks.isCutout(entry.getKey())) {
                    net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                            entry.getValue(), net.minecraft.client.renderer.RenderType.cutoutMipped());
                }
            }
        });
        //?}
    }

    private static ResourceLocation id(String path) {
        // 1.21 made the ResourceLocation constructor private.
        return ResourceLocation.fromNamespaceAndPath(AssetBridge.MOD_ID, path);
    }
}
