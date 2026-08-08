package net.pitan76.assetbridge;

import net.pitan76.assetbridge.archive.ArchiveScanner;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.block.BridgedBlocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class AssetBridge {
    public static final String MOD_ID = "assetbridge";
    public static final Logger LOGGER = LoggerFactory.getLogger("Asset Bridge");

    private static AssetBundle bundle = new AssetBundle();

    private AssetBridge() {
    }

    /**
     * Reads {@code mods/assetbridge/} and prepares the bridged blocks. Must run during mod
     * construction, before the block registry freezes and before resource packs are listed.
     * Registering the blocks themselves is the platform's job.
     */
    public static void init(Path gameDir) {
        bundle = AssetPipeline.build(ArchiveScanner.scan(gameDir));
        BridgedBlocks.create(bundle);
    }

    public static AssetBundle bundle() {
        return bundle;
    }
}
