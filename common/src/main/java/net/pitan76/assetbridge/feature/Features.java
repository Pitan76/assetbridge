package net.pitan76.assetbridge.feature;

import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetBundle;
import net.pitan76.assetbridge.feature.builtin.BlockFeature;
import net.pitan76.assetbridge.feature.builtin.ItemFeature;
import net.pitan76.assetbridge.feature.builtin.ResourcePackFeature;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The list of features, in the order they run.
 *
 * <p>Adding a feature is a one-line change here plus one new class; nothing in the core has
 * to know what the feature does.
 */
public class Features {
    private static final List<Feature> REGISTERED = new ArrayList<>(List.of(
            new BlockFeature(),
            new ItemFeature(),
            new ResourcePackFeature()
    ));

    private static Set<String> enabled = Set.of();
    private static boolean applied;

    private Features() {
    }

    /**
     * Adds a feature. Must be called before {@link #apply}, i.e. before
     * {@link AssetBridge#init}, so an add-on registers from its own mod constructor.
     */
    public static void register(Feature feature) {
        if (applied) throw new IllegalStateException("Features have already run: " + feature.id());
        REGISTERED.add(feature);
    }

    public static List<Feature> registered() {
        return Collections.unmodifiableList(REGISTERED);
    }

    /**
     * Whether a feature is switched on. Before {@link #apply} has run nothing is on yet, so
     * callers outside the feature lifecycle — a mixin, for instance — are answered honestly
     * rather than optimistically.
     */
    public static boolean isEnabled(String featureId) {
        return enabled.contains(featureId);
    }

    /** Runs every switched-on feature against the freshly built bundle. */
    public static void apply(Path gameDir, AssetBundle bundle) {
        enabled = FeatureConfig.read(gameDir, registered());
        applied = true;

        FeatureContext context = new FeatureContext(gameDir, bundle, enabled);
        for (Feature feature : REGISTERED) {
            if (!enabled.contains(feature.id())) {
                AssetBridge.LOGGER.info("Feature '{}' is switched off", feature.id());
                continue;
            }
            try {
                feature.apply(context);
            } catch (RuntimeException e) {
                // One broken feature must not take the whole mod — and with it every other
                // mod's startup — down with it.
                AssetBridge.LOGGER.error("Feature '{}' failed and was skipped", feature.id(), e);
            }
        }
    }
}
