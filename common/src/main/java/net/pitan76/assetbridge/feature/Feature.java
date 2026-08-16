package net.pitan76.assetbridge.feature;

/**
 * One switchable piece of Asset Bridge.
 *
 * <p>The core is only responsible for turning archives into an {@link
 * net.pitan76.assetbridge.asset.BridgedAssetManager}. Everything done with that bundle afterwards —
 * creating blocks, creating items, serving a resource pack, and later data packs, loot
 * tables and recipes — is a feature, so a new one can be added without the core learning
 * about it and a broken one can be switched off.
 *
 * <p>Features must not call each other directly. If one depends on another, it asks
 * {@link FeatureContext#isEnabled(String)} and degrades gracefully when the answer is no.
 */
public interface Feature {
    /** Stable identifier. Used as the config key and in the log, so never rename it lightly. */
    String id();

    /** One line describing what turning this off costs. Written into the config file. */
    String description();

    default boolean enabledByDefault() {
        return true;
    }

    /**
     * Runs once during mod construction, right after the bundle has been built, in
     * registration order. Registering into the game's registries is still the platform's
     * job; a feature only prepares what the platform then picks up.
     *
     * <p>Not every feature has work of its own here. Some are consulted where they apply
     * instead — while the bundle is built, or by the platform's client code — and for those
     * the switch and its description are the whole feature. They leave this as it is.
     */
    default void apply(FeatureContext context) {
    }
}
