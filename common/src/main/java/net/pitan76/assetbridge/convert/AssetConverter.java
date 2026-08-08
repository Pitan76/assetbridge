package net.pitan76.assetbridge.convert;

import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import org.jetbrains.annotations.Nullable;

/**
 * Converts a single resource from the format of {@code from} into the format the running
 * Minecraft version expects.
 *
 * <p>This is the seam the requirement doc calls for: nothing outside this package should
 * know about cross-version asset spec differences. Implementations return the converted
 * bytes, or {@code null} to drop the resource entirely.
 */
public interface AssetConverter {
    @Nullable
    byte[] convert(AssetPath path, byte[] data, AssetVersion from);
}
