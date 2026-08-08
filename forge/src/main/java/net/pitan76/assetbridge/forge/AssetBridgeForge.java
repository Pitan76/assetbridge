package net.pitan76.assetbridge.forge;

import net.pitan76.assetbridge.AssetBridge;
import net.minecraftforge.fml.common.Mod;

@Mod(AssetBridge.MOD_ID)
public final class AssetBridgeForge {
    public AssetBridgeForge() {
        // Run our common setup.
        AssetBridge.init();
    }
}
