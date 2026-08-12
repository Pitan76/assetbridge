package net.pitan76.assetbridge.util;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.12.2's {@code ResourceLocation} has no {@code tryParse}: that convenience was only added
 * in later versions. This is the same "parse or {@code null}" behaviour, built on the plain
 * constructor that vanilla ships here.
 */
public class IdUtil {
    private IdUtil() {
    }

    public static ResourceLocation tryParse(String id) {
        //? if >=1.13 {
        return ResourceLocation.tryParse(id);
        //?} else {
        /*
        if (id == null || id.isEmpty()) return null;
        try {
            return new ResourceLocation(id);
        } catch (RuntimeException e) {
            return null;
        }
        *///?}
    }

    public static ResourceLocation of(String namespace, String path) {
        //? if >=1.21 {
        /*// 1.21 made the ResourceLocation constructor private.
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?} else {
        return new ResourceLocation(namespace, path);
        //?}
    }
}
