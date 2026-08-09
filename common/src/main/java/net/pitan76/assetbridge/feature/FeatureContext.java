package net.pitan76.assetbridge.feature;

import net.pitan76.assetbridge.asset.AssetBundle;

import java.nio.file.Path;
import java.util.Set;

/**
 * Everything a {@link Feature} is allowed to see. Deliberately small: widening this is the
 * moment to ask whether the new feature really belongs outside the core.
 *
 * @param gameDir the game directory, for features that read or write files of their own
 * @param bundle  the converted assets. A feature may add resources to it — that is how
 *                generated data such as loot tables reaches the packs.
 * @param enabled the ids of every feature that is switched on this run
 */
public record FeatureContext(Path gameDir, AssetBundle bundle, Set<String> enabled) {
    /** Whether another feature is switched on, for features that build on one another. */
    public boolean isEnabled(String featureId) {
        return enabled.contains(featureId);
    }
}
