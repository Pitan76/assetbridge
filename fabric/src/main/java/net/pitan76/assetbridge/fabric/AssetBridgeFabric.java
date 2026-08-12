package net.pitan76.assetbridge.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.legacyfabric.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.minecraft.util.Identifier;
import net.minecraft.item.itemgroup.ItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.block.BridgedBlocks;
import net.pitan76.assetbridge.block.BridgedItemGroup;
import net.pitan76.assetbridge.block.BridgedItems;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.SplitTabByNamespaceFeature;
import net.pitan76.assetbridge.pack.AssetBridgeRepositorySource;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

public class AssetBridgeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Path gameDir = FabricLoader.getInstance().getGameDir();

        // Load config first to check enabled features during tab setup
        Features.loadConfig(gameDir);

        // The tabs have to exist before the items are built.
        if (!Features.isEnabled(SplitTabByNamespaceFeature.ID)) {
            BridgedItemGroup.setBlocksTab(createTab(BridgedItemGroup.BLOCKS, BridgedItemGroup::blocksIcon));
            BridgedItemGroup.setItemsTab(createTab(BridgedItemGroup.ITEMS, BridgedItemGroup::itemsIcon));
        }

        BridgedItemGroup.setTabFactory((namespace, iconSupplier) -> createTab(namespace, iconSupplier));

        BridgedItemGroup.setModNameProvider(namespace -> FabricLoader.getInstance().getModContainer(namespace)
                .map(container -> container.getMetadata().getName())
                .orElse(BridgedItemGroup.capitalize(namespace)));

        AssetBridge.init(gameDir, FabricLoader.getInstance()::isModLoaded);
        AssetBridge.applyFeatures(gameDir, FabricLoader.getInstance()::isModLoaded);

        // 1.12.2 has neither PackResources nor a PackRepository to inject into: the bridged
        // resources/data are written straight into this mod's own assets/data folders under the
        // dev/run resources root, which the game's normal IResourcePack scanning already reads.
        AssetBridgeRepositorySource.RESOURCES.writeTo(gameDir.resolve("assets"));
        AssetBridgeRepositorySource.DATA.writeTo(gameDir.resolve("data"));

        // Mod initialisation runs before the registries freeze, so direct registration is fine.
        int registeredBlocks = 0;
        for (Map.Entry<Identifier, Block> entry : BridgedBlocks.blocks().entrySet()) {
            // Mod init order is not controllable, so a mod loaded after us can still claim the
            // same id. That is the desired outcome anyway: the real mod should win.
            if (Block.REGISTRY.containsKey(entry.getKey())) {
                AssetBridge.LOGGER.info("Skipping {}: already registered by another mod", entry.getKey());
                continue;
            }
            Block.REGISTRY.put(entry.getKey(), entry.getValue());
            Item.REGISTRY.put(entry.getKey(), BridgedBlocks.items().get(entry.getKey()));
            registeredBlocks++;
        }

        int registeredItems = 0;
        for (Map.Entry<Identifier, Item> entry : BridgedItems.items().entrySet()) {
            if (Item.REGISTRY.containsKey(entry.getKey())) {
                AssetBridge.LOGGER.info("Skipping item {}: already registered by another mod", entry.getKey());
                continue;
            }
            Item.REGISTRY.put(entry.getKey(), entry.getValue());
            registeredItems++;
        }
        AssetBridge.LOGGER.info("Registered {} bridged blocks and {} items", registeredBlocks, registeredItems);
    }

    // ---------------------------------------------------------------------------
    // Version-specific glue. 1.12.2 predates the flattened registry types entirely:
    // Block/Item still key their registries by Identifier through a plain MutableRegistry
    // (put/containsKey, no Registry.register helper), and creative tabs are ItemGroup, built
    // through Legacy Fabric API's builder since ItemGroup's own constructor takes an explicit
    // slot index it does not allocate for you.
    // ---------------------------------------------------------------------------

    private static ItemGroup createTab(String path, Supplier<ItemStack> icon) {
        return FabricItemGroupBuilder.create(modId(path))
                .iconWithItemStack(icon)
                .build();
    }

    private static Identifier modId(String path) {
        return new Identifier(AssetBridge.MOD_ID, path);
    }
}
