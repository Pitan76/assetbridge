package net.pitan76.assetbridge.pack;

import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.DataPackFeature;
import net.pitan76.assetbridge.feature.builtin.ResourcePackFeature;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Decides whether the bridged assets for one pack root should be written out, and writes them.
 *
 * <p>On later versions this class is a {@code RepositorySource}, registered into a shared
 * {@code PackRepository} by {@code PackRepositoryMixin} so the bundle shows up as an in-memory
 * pack. 1.12.2 predates that whole abstraction (introduced in 1.13, along with data packs
 * themselves), so there is nothing to register into; the bundle is written directly to a
 * directory the platform already exposes to the game's own resource loading instead. See
 * {@link AssetBridgePackResources} for the actual file writing.
 */
public class AssetBridgeRepositorySource {
    public static final AssetBridgeRepositorySource RESOURCES =
            new AssetBridgeRepositorySource(AssetPath.PackKind.CLIENT, ResourcePackFeature.ID);
    public static final AssetBridgeRepositorySource DATA =
            new AssetBridgeRepositorySource(AssetPath.PackKind.SERVER, DataPackFeature.ID);

    private final AssetPath.PackKind kind;
    private final String featureId;

    private AssetBridgeRepositorySource(AssetPath.PackKind kind, String featureId) {
        this.kind = kind;
        this.featureId = featureId;
    }

    /** Whether this source should write anything at all. */
    private boolean shouldWrite() {
        if (!Features.isEnabled(featureId)) return false;
        // An empty pack is not an error, but there is no reason to write one.
        return AssetBridge.assets().hasResources(kind);
    }

    /**
     * Writes this pack root's bridged assets under {@code target} (a directory the platform
     * already knows the game reads from), if the feature is on and there is anything to write.
     */
    public void writeTo(Path target) {
        if (!shouldWrite()) return;
        try {
            int written = AssetBridgePackResources.writeTo(target, AssetBridge.assets(), kind);
            AssetBridge.LOGGER.info("Wrote {} bridged {} resource(s) to {}",
                    written, kind == AssetPath.PackKind.SERVER ? "data" : "resource", target);
        } catch (IOException e) {
            AssetBridge.LOGGER.error("Could not write the Asset Bridge {} pack to {}", kind, target, e);
        }
    }
}
