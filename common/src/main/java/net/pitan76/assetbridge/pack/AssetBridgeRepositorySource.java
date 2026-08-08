package net.pitan76.assetbridge.pack;

import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.pitan76.assetbridge.AssetBridge;

import java.util.function.Consumer;

/**
 * Injects the bridged assets as an always-enabled built-in pack.
 * Registered from {@code PackRepositoryMixin} so no loader-specific resource API is needed.
 */
public final class AssetBridgeRepositorySource implements RepositorySource {
    public static final AssetBridgeRepositorySource INSTANCE = new AssetBridgeRepositorySource();

    private AssetBridgeRepositorySource() {
    }

    @Override
    public void loadPacks(Consumer<Pack> consumer, Pack.PackConstructor constructor) {
        if (AssetBridge.bundle().isEmpty()) return;

        Pack pack = Pack.create(
                AssetBridgePackResources.PACK_ID,
                true,
                () -> new AssetBridgePackResources(AssetBridge.bundle()),
                constructor,
                Pack.Position.TOP,
                PackSource.BUILT_IN
        );
        if (pack != null) {
            consumer.accept(pack);
        } else {
            AssetBridge.LOGGER.error("Could not create the Asset Bridge resource pack");
        }
    }
}
