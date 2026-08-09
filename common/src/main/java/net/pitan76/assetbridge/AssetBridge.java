package net.pitan76.assetbridge;

import net.pitan76.assetbridge.archive.ArchiveScanner;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.block.BridgedBlocks;
import net.pitan76.assetbridge.block.BridgedItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

public class AssetBridge {
    public static final String MOD_NAME = "Asset Bridge";
    public static final String MOD_ID = "assetbridge";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static AssetBundle bundle = new AssetBundle();
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
     * @param namespaceInUse tells whether a namespace already belongs to a loaded mod, so
     *                       Asset Bridge never shadows a mod the player actually installed
     */
    public static void init(Path gameDir, Predicate<String> namespaceInUse) {
        closeArchives();

        // Scan assetbridge/
        archives = ArchiveScanner.scan(gameDir);

        // Build the asset bundle from the archives (assets)
        bundle = AssetPipeline.build(archives, namespaceInUse);

        // Create the bridged blocks and items
        BridgedBlocks.create(bundle);
        BridgedItems.create(bundle);
    }

    public static AssetBundle bundle() {
        return bundle;
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
