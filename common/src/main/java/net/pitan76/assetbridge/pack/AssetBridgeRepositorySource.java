package net.pitan76.assetbridge.pack;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.DataPackFeature;
import net.pitan76.assetbridge.feature.builtin.ResourcePackFeature;
import org.jetbrains.annotations.Nullable;

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

    /** Whether this source should contribute a pack at all. */
    private boolean shouldLoad() {
        if (!Features.isEnabled(featureId)) return false;
        // An empty pack is not an error, but Minecraft has no reason to load one.
        return AssetBridge.assets().hasResources(kind);
    }

    private void emit(Consumer<Pack> consumer, @Nullable Pack pack) {
        if (pack != null) {
            consumer.accept(pack);
        } else {
            AssetBridge.LOGGER.error("Could not create the Asset Bridge pack '{}'", packId);
        }
    }

    // Position.BOTTOM in both branches: above vanilla so the bridged assets resolve, but
    // below the packs the player enabled themselves so their own pack still wins.

    //? if >=1.21 {
    /*@Override
    public void loadPacks(Consumer<Pack> consumer) {
        if (!shouldLoad()) return;
        // 1.21 moved the id, title and source into PackLocationInfo, and the placement
        // flags into PackSelectionConfig.
        emit(consumer, Pack.readMetaAndCreate(
                new net.minecraft.server.packs.PackLocationInfo(
                        packId,
                        net.minecraft.network.chat.Component.literal(packId),
                        PackSource.BUILT_IN,
                        java.util.Optional.empty()),
                // 1.21 gave ResourcesSupplier a second method, so it is no longer a lambda.
                new Pack.ResourcesSupplier() {
                    @Override
                    public net.minecraft.server.packs.PackResources openPrimary(
                            net.minecraft.server.packs.PackLocationInfo info) {
                        return new AssetBridgePackResources(AssetBridge.assets(), kind);
                    }

                    @Override
                    public net.minecraft.server.packs.PackResources openFull(
                            net.minecraft.server.packs.PackLocationInfo info, Pack.Metadata metadata) {
                        return new AssetBridgePackResources(AssetBridge.assets(), kind);
                    }
                },
                kind == AssetPath.PackKind.SERVER
                        ? net.minecraft.server.packs.PackType.SERVER_DATA
                        : net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                new net.minecraft.server.packs.PackSelectionConfig(
                        true, Pack.Position.BOTTOM, false)
        ));
    }
    *///?} elif >=1.20 {
    /*@Override
    public void loadPacks(Consumer<Pack> consumer) {
        if (!shouldLoad()) return;
        // 1.20 folded the pack constructor into the factory and made the title a Component.
        emit(consumer, Pack.readMetaAndCreate(
                packId,
                net.minecraft.network.chat.Component.literal(packId),
                true,
                id -> new AssetBridgePackResources(AssetBridge.assets(), kind),
                kind == AssetPath.PackKind.SERVER
                        ? net.minecraft.server.packs.PackType.SERVER_DATA
                        : net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                Pack.Position.BOTTOM,
                PackSource.BUILT_IN
        ));
    }
    *///?} else {
    @Override
    public void loadPacks(Consumer<Pack> consumer, Pack.PackConstructor constructor) {
        if (!shouldLoad()) return;
        emit(consumer, Pack.create(
                packId,
                true,
                () -> new AssetBridgePackResources(AssetBridge.assets(), kind),
                constructor,
                Pack.Position.BOTTOM,
                PackSource.BUILT_IN
        ));
    }
    //?}
}
