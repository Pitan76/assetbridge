package net.pitan76.assetbridge.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.feature.Features;
import net.pitan76.assetbridge.feature.builtin.CutoutBlocksFeature;

public class AssetBridgeFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 1.12.2/Legacy Fabric has no per-block render-layer registry callable from outside the
        // block class itself (only Block#getRenderLayer(), overridable solely at construction
        // time), and BridgedBlock -- a common-module class this platform must not modify --
        // exposes no hook for it. CutoutBlocksFeature therefore has no effect on this version:
        // bridged blocks always render on the solid layer here.
        if (Features.isEnabled(CutoutBlocksFeature.ID)) {
            AssetBridge.LOGGER.info("CutoutBlocksFeature is on, but has no effect on 1.12.2: " +
                    "there is no per-block render-layer registry to hook into from platform code");
        }
    }
}
