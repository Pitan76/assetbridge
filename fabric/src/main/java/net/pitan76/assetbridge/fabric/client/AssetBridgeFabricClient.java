package net.pitan76.assetbridge.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.DirectoryResourcePack;
import net.minecraft.resource.ResourcePack;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.CutoutBlocksFeature;
import net.pitan76.assetbridge.feature.builtin.ResourcePackFeature;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

public class AssetBridgeFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 1.12.2/Legacy Fabric has no per-block render-layer registry callable from outside the
        // block class itself (only Block#getRenderLayer(), overridable solely at construction
        // time), and BridgedBlock -- a common-module class this platform must not modify --
        // exposes no hook for it. CutoutBlocksFeature therefore has no effect on this version:
        // bridged blocks always render on the solid layer here.
        if (Features.isEnabled(CutoutBlocksFeature.ID)) {
            AssetBridge.LOGGER.info("CutoutBlocksFeature is on, but has no effect on 1.12.2: " +
                    "there is no per-block render-layer registry to hook into from platform code");
        }

        registerResourcePack();
    }

    /**
     * 1.12.2 has neither {@code PackResources} nor a {@code PackRepository} to inject into (both
     * are 1.13+ concepts): a resource pack here is a {@link DirectoryResourcePack} manually added
     * to {@code MinecraftClient}'s private {@code resourcePacks} list. That list is the game's
     * permanent base list -- {@code MinecraftClient#reloadResources()} copies it first and only
     * appends the user-selected packs on top, never replacing it -- so one reflective addition
     * here survives every future resource reload, exactly like Forge's {@code defaultResourcePacks}
     * trick in {@code AssetBridgeForge}. Fabric Loader invokes {@code onInitializeClient} from a
     * mixin at the tail of {@code MinecraftClient}'s constructor, so the instance already exists
     * by the time this runs.
     */
    private void registerResourcePack() {
        if (!Features.isEnabled(ResourcePackFeature.ID)) return;

        Path resourcePackDir = FabricLoader.getInstance().getGameDir().resolve("assetbridge");
        if (!java.nio.file.Files.isDirectory(resourcePackDir)) return;

        try {
            MinecraftClient client = MinecraftClient.getInstance();
            Field field = MinecraftClient.class.getDeclaredField("resourcePacks");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ResourcePack> resourcePacks = (List<ResourcePack>) field.get(client);
            resourcePacks.add(new DirectoryResourcePack(resourcePackDir.toFile()));
            AssetBridge.LOGGER.info("Registered the Asset Bridge resource pack at {}", resourcePackDir);
        } catch (Exception e) {
            AssetBridge.LOGGER.error("Failed to register the Asset Bridge resource pack via reflection", e);
        }
    }
}
