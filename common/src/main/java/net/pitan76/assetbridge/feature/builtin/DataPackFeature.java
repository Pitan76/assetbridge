package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.feature.Feature;
import net.pitan76.assetbridge.feature.FeatureContext;

/**
 * Serves the generated server-side data as a built-in data pack.
 *
 * <p>The transport, not the content: this feature only opens the door, and other features —
 * loot tables today, recipes later — put things through it by adding
 * {@link AssetPath.PackKind#SERVER} resources to the bundle. Like the resource pack, the
 * pack itself is built later by {@code AssetBridgeRepositorySource}.
 */
public class DataPackFeature implements Feature {
    public static final String ID = "data_pack";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Serve generated data (loot tables and the like) as a built-in data pack. Off: nothing server-side is generated at all.";
    }

    @Override
    public void apply(FeatureContext context) {
        AssetBridge.LOGGER.info("Data pack injection is on");
    }
}
