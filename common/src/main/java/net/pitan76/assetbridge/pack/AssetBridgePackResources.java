package net.pitan76.assetbridge.pack;

import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetSource;
import net.pitan76.assetbridge.asset.BridgedAssetManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Writes the bridged assets straight onto disk, under a directory 1.12.2 already treats as a
 * resource domain.
 *
 * <p>Later versions serve the bundle as an in-memory {@code PackResources}, injected into a
 * shared {@code PackRepository} by {@code AssetBridgeRepositorySource}/{@code
 * PackRepositoryMixin}. 1.12.2 has neither of those types (both are 1.13+): a resource pack
 * there is just files under a directory that some {@code IResourcePack} already scans, so this
 * writes the bundle out as real files instead of trying to inject a synthetic pack. Which
 * directory that is -- the mod's own extracted assets folder, a generated resource pack folder,
 * etc. -- is a platform decision; this only knows how to lay the files out once it is given one.
 */
public class AssetBridgePackResources {
    private AssetBridgePackResources() {
    }

    /**
     * Writes every bridged resource of the given pack root under {@code target}, mirroring
     * {@link AssetPath}'s own {@code <namespace>/<path>} layout (the {@code assets}/{@code data}
     * prefix is not repeated: {@code target} is expected to already point at that root).
     *
     * @return how many resources were written
     */
    public static int writeTo(Path target, BridgedAssetManager assets, AssetPath.PackKind kind) throws IOException {
        int written = 0;
        for (Map.Entry<AssetPath, AssetSource> entry : assets.resources().entrySet()) {
            AssetPath path = entry.getKey();
            if (path.kind() != kind) continue;

            Path file = target.resolve(path.namespace()).resolve(path.path());
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);

            try (InputStream in = entry.getValue().open()) {
                Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
            }
            written++;
        }
        return written;
    }
}
