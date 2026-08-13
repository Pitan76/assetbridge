package net.pitan76.assetbridge.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.util.Json;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Normalises blockstate JSON towards the 1.18.2 spec so it can be served unchanged.
 *
 * <p>Both fixes here are driven by the shape of the content rather than by the declared
 * version, because both inputs are simply invalid in 1.13+ — if they are present, the file
 * is pre-flattening no matter what its metadata claims:
 * <ul>
 *   <li>the variant key {@code normal} meant "no properties"; 1.13+ uses the empty key</li>
 *   <li>model references were relative to {@code models/block/}, so a bare {@code cube_all}
 *       means {@code block/cube_all}</li>
 * </ul>
 */
public class BlockStateConverter implements AssetConverter {
    @Override
    public byte[] convert(AssetPath path, byte[] data, AssetVersion from) {
        JsonObject blockState = Json.parse(new String(data, StandardCharsets.UTF_8));
        if (blockState == null) return null;

        boolean changed = normaliseWeights(blockState);
        if (blockState.has("variants") && blockState.get("variants").isJsonObject()) {
            JsonObject variants = blockState.getAsJsonObject("variants");
            changed |= qualifyModels(variants);
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

    /**
     * A weighted variant is an array of models with a {@code weight} each. Minecraft picks from
     * it with a weighted random draw, so a weight that is missing, negative or not a whole
     * number makes the draw fail and the state renders as nothing. Anything unusable is pulled
     * back to the default weight of 1, which is what a single-model variant behaves like.
     */
    private static boolean normaliseWeights(JsonElement element) {
        boolean changed = false;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                changed |= normaliseWeights(child);
            }
            return changed;
        }
        if (!element.isJsonObject()) return false;

        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().equals("weight")) {
                if (weightOf(entry.getValue()) < 1) {
                    entry.setValue(new com.google.gson.JsonPrimitive(1));
                    changed = true;
                }
            } else if (entry.getValue().isJsonObject() || entry.getValue().isJsonArray()) {
                changed |= normaliseWeights(entry.getValue());
            }
        }
        return changed;
    }

    private static int weightOf(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return 0;
        double value = element.getAsDouble();
        return value == Math.floor(value) ? (int) value : 0;
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
        String qualified = path.indexOf('/') < 0 ? namespace + "block/" + path : model;
        int qColon = qualified.indexOf(':');
        if (qColon < 0) {
            return qualified.toLowerCase(java.util.Locale.ROOT);
        }
        return qualified.substring(0, qColon + 1) + qualified.substring(qColon + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /** Exposed so the pipeline can build the fallback for blockstates it cannot pass through. */
    public static JsonObject singleVariant(String model) {
        JsonObject variant = new JsonObject();
        variant.addProperty("model", model);
        return singleVariant(variant);
    }

    /**
     * Same, but for a variant value taken straight out of the source file, so a weighted list
     * of models keeps all of its entries instead of collapsing to the first one.
     */
    public static JsonObject singleVariant(JsonElement variant) {
        // 1.12.2's own blockstate loader treats a variant's "model" value as bare and relative
        // to models/block/ itself, unlike 1.13+'s fully-qualified "namespace:block/name". The
        // source archive is typically a modern-format pack whose "model" already reads
        // "sakura:block/rice_stage0"; served as-is, 1.12.2 re-prepends "block/" on top of that
        // and looks for the nonsensical "sakura:models/block/block/rice_stage0.json".
        JsonElement bare = unqualifyModels(variant);

        JsonObject variants = new JsonObject();
        // "" is vanilla/Fabric's key for a property-less block's only variant; Forge's own
        // StateMapperBase instead looks up "normal" for the same case. Serving both means the
        // single JSON works unmodified on either platform without this converter needing to know
        // which one is currently running. "inventory" is separate again: 1.12.2 blockstate files
        // resolve a block's *item* icon (creative menu, held item, dropped entity, inventory
        // slot -- everything except the block placed in the world) through that key specifically,
        // regardless of platform. Without it the world block rendered fine (it only ever needed
        // "normal"/"") while every one of its item-form appearances stayed missing.
        variants.add("", bare);
        variants.add("normal", bare);
        variants.add("inventory", bare);
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return root;
    }

    /** Whether the representative variant already carries its own fixed {@code x}/{@code y} rotation. */
    public static boolean hasOwnRotation(JsonElement variant) {
        if (variant.isJsonArray()) {
            for (JsonElement el : variant.getAsJsonArray()) {
                if (hasOwnRotation(el)) return true;
            }
            return false;
        }
        if (!variant.isJsonObject()) return false;
        JsonObject obj = variant.getAsJsonObject();
        return obj.has("x") || obj.has("y");
    }

    /**
     * Same idea as {@link #singleVariant}, but keyed by {@code BlockHorizontal.FACING}'s four
     * horizontal directions instead of a single "normal"/"" variant, each with the {@code y}
     * rotation that reproduces facing that direction -- the same convention vanilla's own
     * directional blocks (furnaces, logs placed with {@code axis=x/z}, etc.) use: north = 0,
     * east = 90, south = 180, west = 270. Only safe when the representative model has no
     * rotation of its own to compose with; see {@code BridgedBlockDefinition#supportsFacing()}.
     */
    public static JsonObject facingVariants(JsonElement variant) {
        JsonElement bare = unqualifyModels(variant);
        JsonObject variants = new JsonObject();
        variants.add("facing=north", withYRotation(bare, 0));
        variants.add("facing=east", withYRotation(bare, 90));
        variants.add("facing=south", withYRotation(bare, 180));
        variants.add("facing=west", withYRotation(bare, 270));
        // The item-form icon ignores block properties entirely and always looks up "inventory".
        variants.add("inventory", bare);
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return root;
    }

    private static JsonElement withYRotation(JsonElement variant, int y) {
        if (variant.isJsonArray()) {
            JsonArray copy = new JsonArray();
            for (JsonElement el : variant.getAsJsonArray()) {
                copy.add(withYRotation(el, y));
            }
            return copy;
        }
        if (!variant.isJsonObject()) return variant;
        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : variant.getAsJsonObject().entrySet()) {
            if (!entry.getKey().equals("y")) copy.add(entry.getKey(), entry.getValue());
        }
        if (y != 0) copy.addProperty("y", y);
        return copy;
    }

    /**
     * The inverse of {@link #qualify}: strips a leading {@code block/} path segment off any
     * {@code model} value found in {@code element} (object or array), so a fully-qualified
     * modern-format reference becomes the bare name 1.12.2's own blockstate loader expects.
     * Returns a fresh copy; {@code element} itself is left untouched.
     */
    private static JsonElement unqualifyModels(JsonElement element) {
        if (element.isJsonArray()) {
            com.google.gson.JsonArray copy = new com.google.gson.JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                copy.add(unqualifyModels(child));
            }
            return copy;
        }
        if (!element.isJsonObject()) return element;

        JsonObject copy = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) {
                String model = entry.getValue().getAsString();
                copy.addProperty("model", unqualify(model));
            } else if (entry.getValue().isJsonObject() || entry.getValue().isJsonArray()) {
                copy.add(entry.getKey(), unqualifyModels(entry.getValue()));
            } else {
                copy.add(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    private static String unqualify(String model) {
        int colon = model.indexOf(':');
        String namespace = colon < 0 ? "" : model.substring(0, colon + 1);
        String path = model.substring(colon + 1);
        return path.startsWith("block/") ? namespace + path.substring("block/".length()) : model;
    }
}
