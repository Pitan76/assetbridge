package net.pitan76.assetbridge.convert;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Normalises blockstate JSON towards the 1.18.2 spec so it can be served unchanged.
 *
 * <p>Handled differences:
 * <ul>
 *   <li>{@link AssetVersion#LEGACY}: pre-1.13 packs use the variant key {@code normal} for
 *       "no properties", where 1.13+ uses the empty key.</li>
 *   <li>{@link AssetVersion#LEGACY}: pre-1.13 model references in blockstates are relative to
 *       {@code models/block/}, so a bare {@code cube_all} means {@code block/cube_all}.</li>
 * </ul>
 */
public final class BlockStateConverter implements AssetConverter {
    @Override
    @Nullable
    public byte[] convert(AssetPath path, byte[] data, AssetVersion from) {
        if (from != AssetVersion.LEGACY) return data;

        JsonObject blockState = Json.parse(new String(data, StandardCharsets.UTF_8));
        if (blockState == null) return null;

        boolean changed = false;
        if (blockState.has("variants") && blockState.get("variants").isJsonObject()) {
            JsonObject variants = blockState.getAsJsonObject("variants");
            changed = qualifyModels(variants);
            if (variants.has("normal")) {
                JsonObject renamed = renameKey(variants, "normal", "");
                blockState.add("variants", renamed);
                changed = true;
            }
        }
        if (blockState.has("multipart") && blockState.get("multipart").isJsonArray()) {
            changed |= qualifyModels(blockState.getAsJsonArray("multipart"));
        }
        return changed ? Json.toString(blockState).getBytes(StandardCharsets.UTF_8) : data;
    }

    private static JsonObject renameKey(JsonObject object, String from, String to) {
        JsonObject renamed = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            renamed.add(entry.getKey().equals(from) ? to : entry.getKey(), entry.getValue());
        }
        return renamed;
    }

    /** Walks any nesting of objects and arrays, rewriting every {@code model} value it finds. */
    private static boolean qualifyModels(JsonElement element) {
        boolean changed = false;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                changed |= qualifyModels(child);
            }
            return changed;
        }
        if (!element.isJsonObject()) return false;

        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) {
                String model = entry.getValue().getAsString();
                String qualified = qualify(model);
                if (!qualified.equals(model)) {
                    entry.setValue(new com.google.gson.JsonPrimitive(qualified));
                    changed = true;
                }
            } else if (entry.getValue().isJsonObject() || entry.getValue().isJsonArray()) {
                changed |= qualifyModels(entry.getValue());
            }
        }
        return changed;
    }

    private static String qualify(String model) {
        int colon = model.indexOf(':');
        String namespace = colon < 0 ? "" : model.substring(0, colon + 1);
        String path = model.substring(colon + 1);
        return path.indexOf('/') < 0 ? namespace + "block/" + path : model;
    }

    /** Exposed so the pipeline can build the fallback for blockstates it cannot pass through. */
    public static JsonObject singleVariant(String model) {
        JsonObject variant = new JsonObject();
        variant.addProperty("model", model);
        JsonObject variants = new JsonObject();
        variants.add("", variant);
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return root;
    }
}
