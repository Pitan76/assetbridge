package net.pitan76.assetbridge.feature;

import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.BridgedAssetManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Everything a {@link Feature} is allowed to see.
 *
 * <p>The core only bridges the client assets. A feature that bridges another category of
 * file — recipes today, advancements or tags later — needs the archives themselves, which
 * is why they are here rather than only the finished bundle.
 *
 * @param gameDir        the game directory, for features that read or write files of their own
 * @param assets         the converted assets. A feature may add resources to it — that is how
 *                       generated data such as loot tables reaches the packs.
 * @param enabled        the ids of every feature that is switched on this run
 * @param archives       the archives, still open, in load order
 * @param isNamespaceUsed whether a namespace already belongs to a loaded mod. A feature must
 *                       honour this for the same reason the core does: never shadow a mod the
 *                       player actually installed.
 */
public record FeatureContext(Path gameDir, BridgedAssetManager assets, Set<String> enabled,
                             List<AssetArchive> archives, Predicate<String> isNamespaceUsed) {
    /** Whether another feature is switched on, for features that build on one another. */
    public boolean isEnabled(String featureId) {
        return enabled.contains(featureId);
    }

    public String getConfigValue(String featureId) {
        return Features.getConfigValue(featureId);
    }
}
