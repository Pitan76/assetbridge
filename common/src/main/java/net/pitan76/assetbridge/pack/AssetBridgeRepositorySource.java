package net.pitan76.assetbridge.pack;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.DataPackFeature;
import net.pitan76.assetbridge.feature.builtin.ResourcePackFeature;

import java.util.function.Consumer;

/**
 * Injects the bridged assets as an always-enabled built-in pack.
 * Registered from {@code PackRepositoryMixin} so no loader-specific resource API is needed.
 *
 * <p>There is one instance per pack root: Minecraft builds a separate repository for
 * resource packs and for data packs, and the mixin picks the matching one.
 */
public class AssetBridgeRepositorySource implements RepositorySource {
    public static final AssetBridgeRepositorySource RESOURCES = new AssetBridgeRepositorySource(
            AssetPath.PackKind.CLIENT, AssetBridgePackResources.PACK_ID, ResourcePackFeature.ID);
    public static final AssetBridgeRepositorySource DATA = new AssetBridgeRepositorySource(
            AssetPath.PackKind.SERVER, AssetBridgePackResources.DATA_PACK_ID, DataPackFeature.ID);

    private final AssetPath.PackKind kind;
    private final String packId;
    private final String featureId;

    private AssetBridgeRepositorySource(AssetPath.PackKind kind, String packId, String featureId) {
        this.kind = kind;
        this.packId = packId;
        this.featureId = featureId;
    }

    @Override
    public void loadPacks(Consumer<Pack> consumer, Pack.PackConstructor constructor) {
        if (!Features.isEnabled(featureId)) return;
        // An empty pack is not an error, but Minecraft has no reason to load one.
        if (!AssetBridge.bundle().hasResources(kind)) return;

        Pack pack = Pack.create(
                packId,
                true,
                () -> new AssetBridgePackResources(AssetBridge.bundle(), kind),
                constructor,
                // Above vanilla so the bridged assets resolve, but below the packs the
                // player enabled themselves so their own pack still wins.
                Pack.Position.BOTTOM,
                PackSource.BUILT_IN
        );
        if (pack != null) {
            consumer.accept(pack);
        } else {
            AssetBridge.LOGGER.error("Could not create the Asset Bridge pack '{}'", packId);
        }
    }
}
