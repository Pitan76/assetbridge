package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.block.BridgedItems;
import net.pitan76.assetbridge.feature.Feature;
import net.pitan76.assetbridge.feature.FeatureContext;

/** Turns item models that no bridged block claims into plain {@code Item} instances. */
public class ItemFeature implements Feature {
    public static final String ID = "items";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Register a plain item for every bridged item model that is not a block item.";
    }

    @Override
    public void apply(FeatureContext context) {
        BridgedItems.create(context.bundle());
    }
}
