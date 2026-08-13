package net.pitan76.assetbridge.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
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
 * not link. Bridged blocks/items on this platform fall back to {@code BridgedItemGroup}'s own
 * vanilla-tab default ({@code CreativeTabs.BUILDING_BLOCKS}/{@code MISC}, resolved entirely
 * inside {@code common} where the mapping is consistent) until that gap is closed with a real
 * remap step for the {@code common} dependency.
 */
public class AssetBridgeFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Path gameDir = FabricLoader.getInstance().getGameDir();

        // Load config first to check enabled features during tab setup
        Features.loadConfig(gameDir);

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
        // BridgedBlocks/BridgedItems key their maps by net.minecraft.util.ResourceLocation --
        // common was compiled against the MCP mapping (shared with Forge), and that class does
        // not exist at all under Legacy Fabric's Legacy Yarn mapping (the same game class is
        // named net.minecraft.util.Identifier there), with no remap step bridging the two for
        // this plain, non-mod library dependency (see the class Javadoc above). Reading the maps
        // through raw types erases the key's declared type to Object, so this can still read the
        // ids out -- via their "namespace:path" string form -- without the compiler ever needing
        // to resolve the ResourceLocation class itself.
        @SuppressWarnings("unchecked")
        Map<Object, Object> rawBlocks = (Map<Object, Object>) (Map<?, ?>) BridgedBlocks.blocks();
        @SuppressWarnings("unchecked")
        Map<Object, Object> rawBlockItems = (Map<Object, Object>) (Map<?, ?>) BridgedBlocks.items();
        @SuppressWarnings("unchecked")
        Map<Object, Object> rawItems = (Map<Object, Object>) (Map<?, ?>) BridgedItems.items();

        int registeredBlocks = 0;
        for (Map.Entry<Object, Object> entry : rawBlocks.entrySet()) {
            // Mod init order is not controllable, so a mod loaded after us can still claim the
            // same id. That is the desired outcome anyway: the real mod should win.
            Identifier id = new Identifier(entry.getKey().toString());
            if (Block.REGISTRY.containsKey(id)) {
                AssetBridge.LOGGER.info("Skipping {}: already registered by another mod", id);
                continue;
            }
            Block block = (Block) entry.getValue();
            Block.REGISTRY.put(id, block);
            Item.REGISTRY.put(id, (Item) rawBlockItems.get(entry.getKey()));
            registeredBlocks++;
        }

        int registeredItems = 0;
        for (Map.Entry<Object, Object> entry : rawItems.entrySet()) {
            Identifier id = new Identifier(entry.getKey().toString());
            if (Item.REGISTRY.containsKey(id)) {
                AssetBridge.LOGGER.info("Skipping item {}: already registered by another mod", id);
                continue;
            }
            Item.REGISTRY.put(id, (Item) entry.getValue());
            registeredItems++;
        }
        AssetBridge.LOGGER.info("Registered {} bridged blocks and {} items", registeredBlocks, registeredItems);
    }
}
