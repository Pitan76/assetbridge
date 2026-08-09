package net.pitan76.assetbridge.asset;

/**
 * Pack format numbers of the Minecraft version this build targets.
 *
 * <p>These are the only asset-spec values that must be stated per version rather than
 * derived, so the versioned comments are confined to this class. Everything else in
 * {@code asset/} and {@code convert/} stays version-independent and unit-testable.
 *
 * <p>Adding a Minecraft version means adding a branch here, not touching the converters.
 * The numbers are the ones Minecraft itself writes into {@code pack.mcmeta}: resource
 * packs and data packs are versioned separately and drift apart.
 */
public final class RuntimePack {
    private RuntimePack() {
    }

    /** {@code pack_format} for resource packs. */
    public static int resourcePackFormat() {
        //? if >=1.20 {
        return 15;
        //?} elif >=1.19 {
        /*return 9;
        *///?} else {
        /*return 8;
        *///?}
    }

    /** {@code pack_format} for data packs, which is versioned separately from resources. */
    public static int dataPackFormat() {
        //? if >=1.20 {
        return 15;
        //?} elif >=1.19 {
        /*return 10;
        *///?} else {
        /*return 9;
        *///?}
    }

    /** The asset generation this build targets, i.e. what converters must convert *to*. */
    public static AssetVersion generation() {
        return AssetVersion.fromPackFormat(resourcePackFormat());
    }
}
