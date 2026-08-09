package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.feature.Feature;
import net.pitan76.assetbridge.feature.FeatureContext;

/** Splits creative tabs by namespace (mod) instead of block/item. */
public class SplitTabByNamespaceFeature implements Feature {
    public static final String ID = "split_tab_by_namespace";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Split creative tabs by namespace (mod) instead of blocks and items. Off: two tabs (Blocks and Items) are created.";
    }

    @Override
    public void apply(FeatureContext context) {
        // Initialization is done in AssetBridge.init, nothing to do here.
    }
}
