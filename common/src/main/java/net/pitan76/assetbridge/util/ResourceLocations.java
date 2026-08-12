package net.pitan76.assetbridge.util;

import net.minecraft.util.ResourceLocation;

/**
 * 1.12.2's {@code ResourceLocation} has no {@code tryParse}: that convenience was only added
 * in later versions. This is the same "parse or {@code null}" behaviour, built on the plain
 * constructor that vanilla ships here.
 */
public class ResourceLocations {
    private ResourceLocations() {
    }

    public static ResourceLocation tryParse(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            return new ResourceLocation(id);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
