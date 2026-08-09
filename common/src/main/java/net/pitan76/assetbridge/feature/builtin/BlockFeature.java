package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.block.BridgedBlocks;
import net.pitan76.assetbridge.feature.Feature;
import net.pitan76.assetbridge.feature.FeatureContext;

/** Turns the bridged block assets into {@code Block} instances for the platform to register. */
public class BlockFeature implements Feature {
    public static final String ID = "blocks";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Register a placeable block for every bridged blockstate. Off: the assets are still served, but no block exists.";
    }

    @Override
    public void apply(FeatureContext context) {
        BridgedBlocks.create(context.bundle());
    }
}
