package net.pitan76.assetbridge.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.block.BridgedBlocks;
import net.pitan76.assetbridge.block.BridgedItemGroup;
import net.pitan76.assetbridge.block.BridgedItems;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.SplitTabByNamespaceFeature;
import net.pitan76.assetbridge.pack.AssetBridgeRepositorySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * 1.12.2 has no {@code IEventBus}/{@code FMLJavaModLoadingContext}/{@code ModList} -- those are
 * 1.14+ FML. Registration here hooks into the classic lifecycle instead: an {@code @Mod}-annotated
 * instance with an {@code @Mod.EventHandler} preInit method, and {@code RegistryEvent.Register}
 * (which does already exist on 1.12.2) delivered through the shared {@code MinecraftForge.EVENT_BUS}
 * once this instance registers itself as a listener there.
 *
 * <p>Note: 1.12.2 has no per-block render-layer registry callable from outside the block class
 * itself (only {@code Block#getRenderLayer()}, overridable solely at construction time), and
 * {@code BridgedBlock} -- a common-module class this platform must not modify -- exposes no hook
 * for it. {@code CutoutBlocksFeature} therefore has no effect on this version: bridged blocks
 * always render on the solid layer here.
 */
@Mod(modid = AssetBridge.MOD_ID, name = AssetBridge.MOD_NAME, version = "0.1.0")
public class AssetBridgeForge {
    /** Ids that lost the race against a real mod, so their items must be skipped too. */
    private final Set<ResourceLocation> skipped = new LinkedHashSet<>();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Path gameDir = event.getModConfigurationDirectory().getParentFile().toPath();

        // Load config first to check enabled features during tab setup
        Features.loadConfig(gameDir);

        // 1.12.2's CreativeTabs assigns itself the next free slot via its (String) constructor;
        // the label becomes the translation key suffix, kept identical to the other platforms' ids.
        if (!Features.isEnabled(SplitTabByNamespaceFeature.ID)) {
            BridgedItemGroup.setBlocksTab(new CreativeTabs(AssetBridge.MOD_ID + "." + BridgedItemGroup.BLOCKS) {
                @Override
                public ItemStack createIcon() {
                    return BridgedItemGroup.blocksIcon();
                }
            });
            BridgedItemGroup.setItemsTab(new CreativeTabs(AssetBridge.MOD_ID + "." + BridgedItemGroup.ITEMS) {
                @Override
                public ItemStack createIcon() {
                    return BridgedItemGroup.itemsIcon();
                }
            });
        }

        BridgedItemGroup.setTabFactory((namespace, iconSupplier) -> new CreativeTabs(AssetBridge.MOD_ID + "." + namespace) {
            @Override
            public ItemStack createIcon() {
                return iconSupplier.get();
            }
        });

        BridgedItemGroup.setModNameProvider(namespace -> {
            ModContainer container = Loader.instance().getIndexedModList().get(namespace);
            return container != null ? container.getName() : BridgedItemGroup.capitalize(namespace);
        });

        AssetBridge.init(gameDir, Loader::isModLoaded);
        AssetBridge.applyFeatures(gameDir, Loader::isModLoaded);

        // 1.12.2 has neither PackResources nor a PackRepository to inject into: the bridged
        // resources/data are written straight into this mod's own assets/data folders under the
        // dev/run resources root, which the game's normal IResourcePack scanning already reads.
        Path resourcePackDir = gameDir.resolve("assetbridge");
        AssetBridgeRepositorySource.RESOURCES.writeTo(resourcePackDir.resolve("assets"));
        AssetBridgeRepositorySource.DATA.writeTo(gameDir.resolve("data"));

        if (event.getSide().isClient()) {
            Path packMcmeta = resourcePackDir.resolve("pack.mcmeta");
            if (!Files.exists(packMcmeta)) {
                try {
                    Files.createDirectories(resourcePackDir);
                    String content = "{\n  \"pack\": {\n    \"pack_format\": 3,\n    \"description\": \"Asset Bridge Resources\"\n  }\n}";
                    Files.write(packMcmeta, content.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    AssetBridge.LOGGER.error("Failed to write pack.mcmeta", e);
                }
            }

            try {
                List<IResourcePack> defaultResourcePacks =
                        ReflectionHelper.getPrivateValue(
                                Minecraft.class,
                                Minecraft.getMinecraft(),
                                "defaultResourcePacks", "field_110449_ao"
                        );
                defaultResourcePacks.add(new FolderResourcePack(resourcePackDir.toFile()));
                AssetBridge.LOGGER.info("Registered the Asset Bridge resource pack at {} ({} default resource pack(s) total)",
                        resourcePackDir, defaultResourcePacks.size());

                // Adding to defaultResourcePacks alone changes nothing until the resource manager
                // actually reloads from it. FML's own reload -- the "Reloading ResourceManager:
                // Default, FMLFileResourcePack:..." seen right after mod discovery -- already ran
                // before preInit gets here, and nothing forces a second one before block/item
                // models are baked later in Minecraft#init(). Without this, the pack sits in the
                // list, registered but never actually read, and every bridged model/texture comes
                // back as a FileNotFoundException.
                Minecraft.getMinecraft().refreshResources();
            } catch (Exception e) {
                AssetBridge.LOGGER.error("Failed to register FolderResourcePack via reflection", e);
            }
        }

        MinecraftForge.EVENT_BUS.register(this);
    }

    private static class DummyContainer extends DummyModContainer {
        private final String modId;

        public DummyContainer(String modId) {
            super(new ModMetadata());
            this.modId = modId;
            getMetadata().modId = modId;
            getMetadata().name = modId;
        }

        @Override
        public String getModId() {
            return modId;
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

    private void registerWithContainer(ResourceLocation id, Runnable registerAction) {
        ModContainer original = Loader.instance().activeModContainer();
        String namespace = id.getNamespace();
        ModContainer target = Loader.instance().getIndexedModList().get(namespace);
        if (target == null) {
            target = new DummyContainer(namespace);
        }
        Loader.instance().setActiveModContainer(target);
        try {
            registerAction.run();
        } finally {
            Loader.instance().setActiveModContainer(original);
        }
    }

    @SubscribeEvent
    public void registerBlocks(RegistryEvent.Register<Block> event) {
        registerBlocksInto(
                id -> event.getRegistry().containsKey(id),
                (id, block) -> registerWithContainer(id, () -> event.getRegistry().register(block.setRegistryName(id))));
    }

    @SubscribeEvent
    public void registerItems(RegistryEvent.Register<Item> event) {
        registerItemsInto(
                id -> event.getRegistry().containsKey(id),
                (id, item) -> registerWithContainer(id, () -> event.getRegistry().register(item.setRegistryName(id))));
    }
}
