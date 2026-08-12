package net.pitan76.assetbridge.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.BridgedAssetManager;
import net.pitan76.assetbridge.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Declares the texture directories the bridged models use, so their sprites reach the block
 * atlas.
 *
 * <p>Up to 1.19.2 the atlas was stitched from whatever the loaded models referenced, so a
 * texture resolved wherever it happened to live. From 1.19.3 the atlas is instead defined by
 * {@code atlases/blocks.json}, and vanilla's definition names exactly two directories:
 * {@code textures/block/} and {@code textures/item/}. A mod that keeps sprites anywhere else
 * — {@code textures/entities/chest/} for a chest drawn by a block entity renderer is the
 * common case — has them silently left out, and every model using one renders as missing.
 *
 * <p>Minecraft reads this file from every pack that provides it and concatenates the results,
 * so adding sources here extends vanilla's list rather than replacing it. The file is inert
 * on versions that predate atlas definitions.
 */
public class AtlasSources {
    /** Where the atlas definition has to live to be merged into the block atlas. */
    private static final AssetPath BLOCK_ATLAS =
            new AssetPath(AssetPath.PackKind.CLIENT, "minecraft", "atlases/blocks.json");

    /** Directories vanilla's own definition already covers. */
    private static final Set<String> COVERED =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("block", "item")));

    private AtlasSources() {
    }

    /**
     * @return the number of extra directories declared, or 0 if the models only used the
     *         directories vanilla already stitches
     */
    public static int declare(BridgedAssetManager assets) {
        // Sorted so the generated file does not depend on iteration order.
        Set<String> directories = new TreeSet<>();
        for (Map.Entry<AssetPath, ?> entry : assets.resources().entrySet()) {
            if (!isModel(entry.getKey())) continue;
            collectDirectories(assets, entry.getKey(), directories);
        }
        if (directories.isEmpty()) return 0;

        JsonArray sources = new JsonArray();
        for (String directory : directories) {
            JsonObject source = new JsonObject();
            source.addProperty("type", "directory");
            source.addProperty("source", directory);
            // The prefix becomes part of the sprite name, so it has to reproduce the path the
            // models refer to: 'entities/chest/ruby_chest', not just 'ruby_chest'.
            source.addProperty("prefix", directory + "/");
            sources.add(source);
        }
        JsonObject root = new JsonObject();
        root.add("sources", sources);
        assets.putResource(BLOCK_ATLAS, Json.toString(root).getBytes(StandardCharsets.UTF_8));

        AssetBridge.LOGGER.info("Added {} texture directory/directories to the block atlas: {}",
                directories.size(), directories);
        return directories.size();
    }

    private static boolean isModel(AssetPath path) {
        switch (path.category()) {
            case BLOCK_MODEL:
            case ITEM_MODEL:
            case MODEL:
                return true;
            default:
                return false;
        }
    }

    private static void collectDirectories(BridgedAssetManager assets, AssetPath path, Set<String> into) {
        byte[] data;
        try {
            data = assets.readResource(path);
        } catch (IOException e) {
            AssetBridge.LOGGER.warn("Could not re-read {} while collecting atlas sources", path, e);
            return;
        }
        if (data == null) return;

        JsonObject model = Json.parse(new String(data, StandardCharsets.UTF_8));
        if (model == null || !model.has("textures") || !model.get("textures").isJsonObject()) return;

        for (Map.Entry<String, JsonElement> texture : model.getAsJsonObject("textures").entrySet()) {
            if (!texture.getValue().isJsonPrimitive()) continue;
            String directory = directoryOf(texture.getValue().getAsString());
            if (directory != null) into.add(directory);
        }
    }

    /**
     * The directory a texture reference points into, or {@code null} when nothing has to be
     * declared for it: a texture variable, a bare name, or a directory vanilla covers.
     */
    static String directoryOf(String reference) {
        // '#name' points at another entry in the same model, not at a file.
        if (reference.startsWith("#")) return null;

        int colon = reference.indexOf(':');
        // Vanilla's own sprites are either already stitched or deliberately left out.
        if (colon >= 0 && reference.substring(0, colon).equals("minecraft")) return null;

        String path = reference.substring(colon + 1);
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) return null;

        String directory = path.substring(0, lastSlash);
        // Vanilla's sources are listed recursively, so a subdirectory of one is covered too.
        for (String covered : COVERED) {
            if (directory.equals(covered) || directory.startsWith(covered + "/")) return null;
        }
        return directory;
    }
}
