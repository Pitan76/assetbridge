package net.pitan76.assetbridge.feature.builtin;

import net.pitan76.assetbridge.feature.Feature;

/** Configure blocks with cutout rendering (transparent parts render properly). */
public class CutoutBlocksFeature implements Feature {
    public static final String ID = "cutout_blocks";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Configure transparent blocks (leaves, glass, etc.) with cutout rendering so they do not render with black backgrounds.";
    }
}
