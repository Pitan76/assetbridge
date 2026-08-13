package net.pitan76.assetbridge.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.legacyfabric.fabric.api.registry.v2.RegistryHelper;
import net.legacyfabric.fabric.api.registry.v2.RegistryIds;
import net.minecraft.util.Identifier;
import net.minecraft.item.Item;
import net.minecraft.block.Block;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.block.BridgedBlocks;
import net.pitan76.assetbridge.block.BridgedItemGroup;
import net.pitan76.assetbridge.block.BridgedItems;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.pack.AssetBridgeRepositorySource;

import java.nio.file.Path;
import java.util.Map;

/**
 * Unlike Forge, this platform cannot construct and hand {@code BridgedItemGroup} a real creative
 * tab: {@code BridgedItemGroup.setBlocksTab}/{@code setItemsTab}/{@code setTabFactory} take
 * {@code net.minecraft.creativetab.CreativeTabs} because that is the name the shared {@code
 * common} module was compiled against (MCP mapping, shared with Forge). Legacy Fabric's own
 * compile classpath (Legacy Yarn mapping) has no such class -- the same game class is named
 * {@code net.minecraft.item.itemgroup.ItemGroup} there -- and the {@code common} project
 * dependency is plain library code with no {@code fabric.mod.json}, so Unimined's mod remapper
 * (which only remaps jars it recognises as mods) never bridges the two names for it. Calling
 * those setters from here is therefore not just a source-level type mismatch but a real
 * cross-mapping gap with no remapping step behind it; wiring a namespace tab through them would
 * not link. {@code BridgedItemGroup.setCreativeTabsSupported(false)} below tells {@code
 * BridgedBlock}/{@code BridgedItems} to skip {@code BridgedItemGroup}'s own tab API and instead
 * assign the vanilla default tab (Building Blocks/Miscellaneous) via
 * {@code net.pitan76.assetbridge.util.DefaultCreativeTab}, which resolves the tab class, its
 * setter method, and the constant to assign entirely by reflection so neither
 * {@code CreativeTabs} nor {@code ItemGroup} is ever referenced as a static type. Namespace-split
 * tabs ({@link net.pitan76.assetbridge.feature.builtin.SplitTabByNamespaceFeature}) still have no
 * effect on this platform until that gap is closed with a real remap step for the {@code common}
 * dependency.
 */
public class AssetBridgeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Path gameDir = FabricLoader.getInstance().getGameDir();

        // Load config first to check enabled features during tab setup
        Features.loadConfig(gameDir);

        BridgedItemGroup.setCreativeTabsSupported(false);

        BridgedItemGroup.setModNameProvider(namespace -> FabricLoader.getInstance().getModContainer(namespace)
                .map(container -> container.getMetadata().getName())
                .orElse(BridgedItemGroup.capitalize(namespace)));

        AssetBridge.init(gameDir, FabricLoader.getInstance()::isModLoaded);
        AssetBridge.applyFeatures(gameDir, FabricLoader.getInstance()::isModLoaded);

        // Legacy Fabric 1.12.2 has no PackResources/PackRepository (those are 1.14+ concepts).
        // The bridged resources are written into a dedicated "assetbridge" folder -- mirroring
        // AssetBridgeForge -- so AssetBridgeFabricClient can wrap it in a DirectoryResourcePack
        // (Legacy Yarn's FolderResourcePack equivalent) and inject it into
        // MinecraftClient.getInstance()'s resourcePacks list via reflection. A bare "assets/"
        // folder under the run directory is never scanned by anything on this version, so writing
        // straight there (as this used to) left the converted textures/models unloaded.
        Path resourcePackDir = gameDir.resolve("assetbridge");
        AssetBridgeRepositorySource.RESOURCES.writeTo(resourcePackDir.resolve("assets"));
        AssetBridgeRepositorySource.DATA.writeTo(gameDir.resolve("data"));

        // DirectoryResourcePack (net.minecraft.resource.AbstractFileResourcePack) requires a
        // pack.mcmeta sibling to the assets/ directory it wraps, exactly like a real pack folder.
        if (Features.isEnabled(net.pitan76.assetbridge.feature.builtin.ResourcePackFeature.ID)) {
            java.nio.file.Path packMcmeta = resourcePackDir.resolve("pack.mcmeta");
            if (!java.nio.file.Files.exists(packMcmeta)) {
                try {
                    java.nio.file.Files.createDirectories(resourcePackDir);
                    String content = "{\n  \"pack\": {\n    \"pack_format\": 3,\n    \"description\": \"Asset Bridge Resources\"\n  }\n}";
                    java.nio.file.Files.write(packMcmeta, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (java.io.IOException e) {
                    AssetBridge.LOGGER.error("Failed to write pack.mcmeta", e);
                }
            }
        }

        // Mod initialisation runs before the registries freeze, so direct registration is fine.
        //
        // BridgedBlocks/BridgedItems key their maps by the plain "namespace:path" id string
        // (see BridgedBlocks' class doc for why), so this needs no reflection or raw-type trick
        // to read them.
        Map<String, Block> blocks = BridgedBlocks.blocks();
        Map<String, Item> blockItems = BridgedBlocks.items();
        Map<String, Item> items = BridgedItems.items();

        int registeredBlocks = 0;
        for (Map.Entry<String, Block> entry : blocks.entrySet()) {
            // Mod init order is not controllable, so a mod loaded after us can still claim the
            // same id. That is the desired outcome anyway: the real mod should win.
            Identifier id = new Identifier(entry.getKey());
            if (Block.REGISTRY.containsKey(id)) {
                AssetBridge.LOGGER.info("Skipping {}: already registered by another mod", id);
                continue;
            }
            // Registry#put only adds the name->object mapping; it never assigns the object a raw
            // int id (that's SimpleRegistry#add). Item/Block IDs that skip this step still look
            // registered (present, resolvable by name, model/texture loads fine) but
            // Item#getRawId returns -1 for them, so anything indexed by raw id -- e.g.
            // Stats.used(Item), read via ItemStack#use on every right-click/placement -- throws
            // ArrayIndexOutOfBoundsException(-1) the first time the item is used. RegistryHelper
            // (Legacy Fabric's registry-sync API) is the sanctioned way to register with a real,
            // synced raw id on this platform.
            net.legacyfabric.fabric.api.util.Identifier fabricId = new net.legacyfabric.fabric.api.util.Identifier(entry.getKey());
            RegistryHelper.register(RegistryIds.BLOCKS, fabricId, entry.getValue());
            RegistryHelper.register(RegistryIds.ITEMS, fabricId, blockItems.get(entry.getKey()));
            registeredBlocks++;
        }

        int registeredItems = 0;
        for (Map.Entry<String, Item> entry : items.entrySet()) {
            Identifier id = new Identifier(entry.getKey());
            if (Item.REGISTRY.containsKey(id)) {
                AssetBridge.LOGGER.info("Skipping item {}: already registered by another mod", id);
                continue;
            }
            RegistryHelper.register(RegistryIds.ITEMS, new net.legacyfabric.fabric.api.util.Identifier(entry.getKey()), entry.getValue());
            registeredItems++;
        }
        AssetBridge.LOGGER.info("Registered {} bridged blocks and {} items", registeredBlocks, registeredItems);
    }
}
