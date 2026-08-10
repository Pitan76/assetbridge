package net.pitan76.assetbridge.asset;

import org.jetbrains.annotations.Nullable;

/**
 * Coarse asset-format generation, derived from a resource pack's {@code pack_format}.
 * The converter layer branches on this rather than on an exact Minecraft version,
 * because the asset spec only changes at a handful of points.
 */
public enum AssetVersion {
    /** 1.6 - 1.12: textures referenced without the {@code minecraft:} style domain split. */
    LEGACY,
    /** 1.13 - 1.14: flattening done, pre-multipart-heavy era. */
    FLATTENED,
    /** 1.15 - 1.19.2: singular {@code block/} directories, no atlas definitions. */
    MODERN,
    /** 1.19.3 - 1.20.4: {@code assets/*&#47;atlases/}, reworked item model metadata. */
    ATLASES,
    /** 1.20.5 - 1.21.3: item stack components replace NBT in item definitions. */
    COMPONENTS,
    /** 1.21.4+: {@code assets/*&#47;items/} item definitions split out of models. */
    ITEM_DEFINITIONS,
    /** No pack.mcmeta, or an unreadable one. Treated as {@link #MODERN}. */
    UNKNOWN;

    /**
     * Whether this generation is at or past {@code other}.
     *
     * <p>Converters should prefer this over equality: a rule that applies from 1.19.3
     * onwards must also apply to every later generation, and writing it as
     * {@code == ATLASES} silently stops applying when the next generation is added.
     *
     * <p>{@link #UNKNOWN} is compared as {@link #MODERN}, matching {@link #resolved()}.
     */
    public boolean isAtLeast(AssetVersion other) {
        return resolved().ordinal() >= other.resolved().ordinal();
    }

    /**
     * Maps a Minecraft version such as {@code 1.21.1} onto its asset generation.
     *
     * @return {@code null} when the string is not a recognisable release version
     */
    @Nullable
    public static AssetVersion fromMinecraftVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length < 2) return null;

        int major;
        int minor;
        int patch;
        try {
            major = Integer.parseInt(parts[0]);
            minor = Integer.parseInt(parts[1]);
            patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        } catch (NumberFormatException e) {
            return null;
        }
        // 26.1 is the first release under the year-based scheme; everything from there on is at
        // least the 1.21.4 generation. Without this, `26.1.2` fails the `major != 1` check below
        // and reports an unrecognisable version.
        if (major >= 26) return ITEM_DEFINITIONS;
        if (major != 1 || minor < 6) return null;

        if (minor <= 12) return LEGACY;
        if (minor <= 14) return FLATTENED;
        // 1.19.3 is where the atlas definitions and item model metadata changes land.
        if (minor < 19 || (minor == 19 && patch <= 2)) return MODERN;
        // 1.20.5 introduced item stack components.
        if (minor < 20 || (minor == 20 && patch <= 4)) return ATLASES;
        // 1.21.4 split item definitions out of models.
        if (minor < 21 || (minor == 21 && patch <= 3)) return COMPONENTS;
        return ITEM_DEFINITIONS;
    }

    /**
     * Maps a resource pack's {@code pack_format} onto its generation.
     *
     * <p>The boundaries are the first format of each generation: 1.19.3 is 12,
     * 1.20.5 is 32 and 1.21.4 is 46. Prefer {@link #fromMinecraftVersion(String)} when a
     * version string is available — it is exact, whereas these numbers move with every
     * snapshot and a pack may also declare a data pack format here.
     */
    public static AssetVersion fromPackFormat(int packFormat) {
        if (packFormat < 0) return UNKNOWN;
        if (packFormat <= 3) return LEGACY;
        if (packFormat <= 4) return FLATTENED;
        if (packFormat < 12) return MODERN;
        if (packFormat < 32) return ATLASES;
        if (packFormat < 46) return COMPONENTS;
        return ITEM_DEFINITIONS;
    }

    public AssetVersion resolved() {
        return this == UNKNOWN ? MODERN : this;
    }
}
