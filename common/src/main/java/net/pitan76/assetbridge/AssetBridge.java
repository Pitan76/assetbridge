package net.pitan76.assetbridge;

import net.pitan76.assetbridge.archive.ArchiveScanner;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.block.BridgedItemGroup;
import net.pitan76.assetbridge.feature.Features;
//? if <=1.17 {
/*import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
*///?} else {
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//?}

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

public class AssetBridge {
    public static final String MOD_NAME = "Asset Bridge";
    public static final String MOD_ID = "assetbridge";

    //? if <=1.17 {
    /*public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);
    *///?} else {
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    //?}

    private static BridgedAssetManager assets = new BridgedAssetManager();
    // Held for the lifetime of the game: the bundle serves textures straight out of these
    // archives, so closing one would break every resource it still backs.
    private static List<AssetArchive> archives = List.of();

    private AssetBridge() {
    }

    /**
     * Reads {@code mods/assetbridge/} and prepares the bridged blocks. Must run during mod
     * construction, before the block registry freezes and before resource packs are listed.
     * Registering the blocks themselves is the platform's job.
     *
     * @param isNamespaceUsed tells whether a namespace already belongs to a loaded mod, so
     *                       Asset Bridge never shadows a mod the player actually installed
     */
    public static void init(Path gameDir, Predicate<String> isNamespaceUsed) {
        closeArchives();

        Features.loadConfig(gameDir);

        archives = ArchiveScanner.scan(gameDir);
        assets = AssetPipeline.build(archives, isNamespaceUsed);

        BridgedItemGroup.initTabs(assets.namespaces(), assets.modNames());
    }

    public static void applyFeatures(Path gameDir, Predicate<String> isNamespaceUsed) {
        // Everything done with the bundle — blocks, items, packs, and whatever is added
        // later — lives behind a feature, so the core ends here.
        Features.apply(gameDir, assets, archives, isNamespaceUsed);
    }

    public static BridgedAssetManager assets() {
        return assets;
    }

    /** Only relevant if {@link #init} ever runs twice; the archives outlive it otherwise. */
    private static void closeArchives() {
        for (AssetArchive archive : archives) {
            try {
                archive.close();
            } catch (IOException e) {
                LOGGER.warn("Could not close {}", archive.fileName(), e);
            }
        }
        archives = List.of();
    }
}
