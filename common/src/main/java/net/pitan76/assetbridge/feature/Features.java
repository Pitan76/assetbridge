package net.pitan76.assetbridge.feature;

import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.archive.AssetArchive;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.feature.builtin.BlockFeature;
import net.pitan76.assetbridge.feature.builtin.BlockKindsFeature;
import net.pitan76.assetbridge.feature.builtin.ModelShapesFeature;
import net.pitan76.assetbridge.feature.builtin.DataPackFeature;
import net.pitan76.assetbridge.feature.builtin.ItemFeature;
import net.pitan76.assetbridge.feature.builtin.LootTableFeature;
import net.pitan76.assetbridge.feature.builtin.RecipeFeature;
import net.pitan76.assetbridge.feature.builtin.ResourcePackFeature;
import net.pitan76.assetbridge.feature.builtin.SplitTabByNamespaceFeature;
import net.pitan76.assetbridge.feature.builtin.CutoutBlocksFeature;
import net.pitan76.assetbridge.feature.builtin.MetaVariantsFeature;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/**
 * The list of features, in the order they run.
 *
 * <p>Adding a feature is a one-line change here plus one new class; nothing in the core has
 * to know what the feature does.
 */
public class Features {
    private static final List<Feature> REGISTERED = new ArrayList<>(Arrays.asList(
            new SplitTabByNamespaceFeature(),
            new CutoutBlocksFeature(),
            // Read by AssetPipeline while the bundle is built, so it must be registered even
            // though its own apply() has nothing to do.
            new MetaVariantsFeature(),
            // Read by AssetPipeline as well: what a block is and what shape it has is worked
            // out from the models, while they are still being assembled.
            new BlockKindsFeature(),
            new ModelShapesFeature(),
            new BlockFeature(),
            new ItemFeature(),
            // Runs after the blocks it generates tables for, before the packs that serve them.
            new LootTableFeature(),
            new RecipeFeature(),
            new ResourcePackFeature(),
            new DataPackFeature()
    ));

    private static Set<String> enabled = new HashSet<>();
    private static java.util.Map<String, String> configValues = java.util.Collections.emptyMap();
    private static boolean applied;

    public static String getConfigValue(String featureId) {
        return configValues.get(featureId);
    }

    public static void setConfigValues(java.util.Map<String, String> values) {
        configValues = java.util.Collections.unmodifiableMap(new java.util.HashMap<>(values));
    }

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

    public static boolean isDisabled(String featureId) {
        return !isEnabled(featureId);
    }

    public static void loadConfig(Path gameDir) {
        enabled = FeatureConfig.read(gameDir, registered());
    }

    /** Runs every switched-on feature against the freshly built bundle. */
    public static void apply(Path gameDir, BridgedAssetManager assets, List<AssetArchive> archives,
                             Predicate<String> isNamespaceUsed) {
        enabled = FeatureConfig.read(gameDir, registered());
        applied = true;

        FeatureContext context = new FeatureContext(gameDir, assets, enabled, archives, isNamespaceUsed);
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
