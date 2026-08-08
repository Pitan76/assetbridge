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
    /** 1.15 - 1.19.2: the generation the MVP targets natively (1.18.2 is pack_format 8). */
    MODERN,
    /** 1.19.3+: split item/block model handling, overlays, newer texture metadata. */
    FUTURE,
    /** No pack.mcmeta, or an unreadable one. Treated as {@link #MODERN}. */
    UNKNOWN;

    /** The pack_format of the Minecraft version this build targets. */
    public static final int CURRENT_PACK_FORMAT = 8;

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
        if (major != 1 || minor < 6) return null;

        if (minor <= 12) return LEGACY;
        if (minor <= 14) return FLATTENED;
        // 1.19.3 is where the item model and metadata changes land.
        if (minor < 19 || (minor == 19 && patch <= 2)) return MODERN;
        return FUTURE;
    }

    public static AssetVersion fromPackFormat(int packFormat) {
        if (packFormat < 0) return UNKNOWN;
        if (packFormat <= 3) return LEGACY;
        if (packFormat <= 4) return FLATTENED;
        if (packFormat <= 9) return MODERN;
        return FUTURE;
    }

    public AssetVersion resolved() {
        return this == UNKNOWN ? MODERN : this;
    }
}
