package net.pitan76.assetbridge.util;

/**
 * Splits a plain {@code namespace:path} id string, without touching
 * {@code net.minecraft.util.ResourceLocation}: that class is not named the same on every
 * platform this module is compiled once and shared across (Legacy Fabric's Legacy Yarn mapping
 * calls it {@code Identifier}), and code that runs as a plain, unremapped dependency there
 * throws {@code NoClassDefFoundError} the moment it touches the wrong-named class.
 */
public final class Ids {
    private Ids() {
    }

    public static String namespaceOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    public static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }
}
