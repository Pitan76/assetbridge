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
        //? if >=26 {
        /*// 26.1 dropped the leading `1.`, so this branch has to come first: `26.1.2` also
        // satisfies `>=1.21` and would otherwise declare 1.21's format and be taken for that
        // generation. The value is `resource_major` from the version.json in the client jar --
        // 26.1 split pack_format into major/minor, and only the major is what a pack declares.
        return 84;
        *///?} elif >=1.21 {
        /*return 34;
        *///?} elif >=1.20 {
        /*return 15;
        *///?} elif >=1.19 {
        /*return 9;
        *///?} elif >=1.13 {
        /*return 4;
        *///?} else {
        // 1.6 - 1.12.2: the last pack_format before the 1.13 flattening.
        return 3;
        //?}
    }

    /** {@code pack_format} for data packs, which is versioned separately from resources. */
    public static int dataPackFormat() {
        //? if >=26 {
        /*// `data_major` from version.json. Must precede the `>=1.21` branch, as above.
        return 101;
        *///?} elif >=1.21 {
        /*return 48;
        *///?} elif >=1.20 {
        /*return 15;
        *///?} elif >=1.19 {
        /*return 10;
        *///?} elif >=1.13 {
        /*return 4;
        *///?} else {
        // Pre-1.13 has no data packs at all; mirrors the resource format so nothing that reads
        // this value (there is nothing to read on these versions) sees an inconsistent number.
        return 3;
        //?}
    }

    /**
     * The {@code pack_format_minor} for resource packs on Minecraft 26.1+, where the pack
     * format was split into an independent major and minor number. Returns 0 on older versions
     * where this field does not exist in {@code pack.mcmeta}.
     */
    public static int resourcePackFormatMinor() {
        //? if >=26 {
        /*// `resource_minor` from the version.json in the client jar.
        return 0;
        *///?} else {
        return 0;
        //?}
    }

    /**
     * The {@code pack_format_minor} for data packs on Minecraft 26.1+.
     * Returns 0 on older versions where this field does not exist.
     */
    public static int dataPackFormatMinor() {
        //? if >=26 {
        /*// `data_minor` from version.json.
        return 0;
        *///?} else {
        return 0;
        //?}
    }

    /** The asset generation this build targets, i.e. what converters must convert *to*. */
    public static AssetVersion generation() {
        return AssetVersion.fromPackFormat(resourcePackFormat());
    }
}
