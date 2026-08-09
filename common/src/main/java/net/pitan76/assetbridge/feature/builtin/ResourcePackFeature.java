package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.feature.Feature;
import net.pitan76.assetbridge.feature.FeatureContext;

/**
 * Serves the converted client resources as a built-in resource pack.
 *
 * <p>Unlike the other features there is nothing to prepare here: the pack is created much
 * later, when Minecraft builds a {@code PackRepository}, and
 * {@code AssetBridgeRepositorySource} asks whether this feature is on at that point.
 */
public class ResourcePackFeature implements Feature {
    public static final String ID = "resource_pack";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Serve the converted models, textures and lang files as a built-in resource pack. Off: bridged blocks render as the missing model.";
    }

    @Override
    public void apply(FeatureContext context) {
        AssetBridge.LOGGER.info("Serving {} bridged resource(s)", context.bundle().resources().size());
    }
}
