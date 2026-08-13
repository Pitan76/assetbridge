package net.pitan76.assetbridge.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.util.Json;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Normalises block/item model JSON towards the 1.18.2 spec.
 *
 * <p>Handled differences:
 * <ul>
 *   <li>{@link AssetVersion#LEGACY}: pre-1.13 models refer to textures as {@code blocks/stone}
 *       and parents as {@code block/cube_all} under the old directory names.</li>
 *   <li>{@link AssetVersion#ATLASES} and later: 1.19.3+ may carry keys an older model deserialiser
 *       rejects; they are stripped rather than failing the whole model.</li>
 *   <li>All versions: texture references with uppercase letters are lowercased, because
 *       {@code ResourceLocation} (and 26.1's {@code TextureSlots}) reject uppercase paths.
 *       The texture file itself was already lowercased when the archive was read
 *       ({@code AssetPath} constructor), so the reference must match.</li>
 *   <li>All versions: {@code x}/{@code y} rotation on a model and {@code rotation} on a face
 *       are rounded to the nearest multiple of 90. Legacy packs occasionally contain values
 *       like {@code 1}, which 26.1's {@code Quadrant} Codec rejects.</li>
 * </ul>
 */
public class ModelConverter implements AssetConverter {
    /** Keys introduced after 1.18.2 that the 1.18.2 model deserialiser does not understand. */
    private static final String[] UNKNOWN_FUTURE_KEYS = {"overrides_v2", "oversized_in_gui"};

    @Override
    public byte[] convert(AssetPath path, byte[] data, AssetVersion from) {
        JsonObject model = Json.parse(new String(data, StandardCharsets.UTF_8));
        if (model == null) return null;

        // Both rules are driven by the content: 'blocks/' does not exist in 1.13+, and a key
        // 1.18.2 does not know is worth dropping whatever version the archive claims to be.
        boolean changed = renameLegacyDirectories(model);
        for (String key : UNKNOWN_FUTURE_KEYS) {
            changed |= model.remove(key) != null;
        }
        changed |= sanitizeRotations(model, path);
        changed |= sanitizeElements(model);
        return changed ? Json.toString(model).getBytes(StandardCharsets.UTF_8) : data;
    }

    /** Pre-flattening packs use {@code blocks/} and {@code items/}; 1.13+ uses the singular form. */
    private static boolean renameLegacyDirectories(JsonObject model) {
        boolean changed = false;
        if (model.has("parent")) {
            String parent = model.get("parent").getAsString();
            String renamed = renameParent(parent);
            if (!renamed.equals(parent)) {
                model.addProperty("parent", renamed);
                changed = true;
            }
        }
        if (model.has("textures") && model.get("textures").isJsonObject()) {
            JsonObject textures = model.getAsJsonObject("textures");
            for (Map.Entry<String, com.google.gson.JsonElement> entry : textures.entrySet()) {
                if (!entry.getValue().isJsonPrimitive()) continue;
                String value = entry.getValue().getAsString();
                // '#name' is a texture variable reference, not a path.
                if (value.startsWith("#")) continue;
                String renamed = renameTexture(value);
                if (!renamed.equals(value)) {
                    entry.setValue(new com.google.gson.JsonPrimitive(renamed));
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static final Map<String, String> VANILLA_REMAP;
    static {
        Map<String, String> remap = new LinkedHashMap<>();
        remap.put("block/glass_white", "block/white_stained_glass");
        remap.put("block/anvil_base", "block/anvil");
        // 1.13 split the single pre-flattening "cube_column" model (rotated to a horizontal
        // orientation entirely via the blockstate variant's own x/y) into three explicit
        // orientation models; none of the "_horizontal"/"_vertical" ones exist on 1.12.2, so a
        // parent reference to them is simply unresolvable there. The blockstate variant already
        // carries the x/y rotation that gave the split models their name, so collapsing all three
        // back onto the single pre-split model reproduces the same orientation on this version.
        remap.put("block/cube_column_horizontal", "block/cube_column");
        remap.put("block/cube_column_vertical", "block/cube_column");
        // Add more common legacy mappings as needed
        VANILLA_REMAP = Collections.unmodifiableMap(remap);
    }

    /**
     * A texture reference. {@code blocks/} and {@code items/} are flattened whatever the
     * namespace: the sprite files themselves are relocated the same way when the archive is
     * read, because from 1.19.3 the block atlas is defined as the contents of
     * {@code textures/block/} and would never stitch a sprite left behind in the plural
     * directory. See {@code AssetPath#flattened()}.
     *
     * <p>The path portion is also lowercased: {@code ResourceLocation} and 26.1's
     * {@code TextureSlots} both reject uppercase characters. The referenced texture file
     * was already stored under its lowercased name when the archive was read.
     */
    private static String renameTexture(String reference) {
        String renamed = rename(reference, true, true);
        // Lowercase the path portion only; the namespace is already required to be lowercase.
        int colon = renamed.indexOf(':');
        if (colon < 0) {
            return renamed.toLowerCase(Locale.ROOT);
        }
        return renamed.substring(0, colon + 1) + renamed.substring(colon + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * A parent reference. Only vanilla is flattened here: model directories were never
     * renamed, so a mod that happens to keep its models under {@code models/blocks/} still
     * has them there and rewriting the reference would break it.
     */
    private static String renameParent(String reference) {
        String renamed = rename(reference, false, false);
        int colon = renamed.indexOf(':');
        if (colon < 0) {
            return renamed.toLowerCase(Locale.ROOT);
        }
        return renamed.substring(0, colon + 1) + renamed.substring(colon + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * @param isTextureReference whether {@code reference} points at a texture rather than a
     *                          model. Texture directory naming tracks {@link RuntimePack#generation()}
     *                          ({@code textures/blocks/} pre-1.13, {@code textures/block/} from
     *                          1.13 on -- see {@link net.pitan76.assetbridge.asset.AssetPath#flattened()});
     *                          model json always lives under the singular {@code models/block/}
     *                          directory regardless of version, so parent references are always
     *                          flattened towards singular instead.
     */
    private static String rename(String reference, boolean anyNamespace, boolean isTextureReference) {
        int colon = reference.indexOf(':');
        String namespace = colon < 0 ? "" : reference.substring(0, colon + 1);
        String path = reference.substring(colon + 1);
        boolean vanilla = namespace.isEmpty() || namespace.equals("minecraft:");
        if (!vanilla && !anyNamespace) return reference;

        if (isTextureReference && !net.pitan76.assetbridge.asset.RuntimePack.generation().isAtLeast(AssetVersion.FLATTENED)) {
            // Legacy (pre-1.13) targets only ever scan the plural texture directories; a
            // modern-format (singular) source reference is renamed down to match instead.
            if (path.startsWith("block/")) {
                path = "blocks/" + path.substring("block/".length());
            } else if (path.startsWith("item/")) {
                path = "items/" + path.substring("item/".length());
            }
        } else {
            if (path.startsWith("blocks/")) {
                path = "block/" + path.substring("blocks/".length());
            } else if (path.startsWith("items/")) {
                path = "item/" + path.substring("items/".length());
            }
        }

        // Apply legacy vanilla name mapping
        if (vanilla) {
            String remapped = VANILLA_REMAP.get(path);
            if (remapped != null) path = remapped;
        }

        return namespace + path;
    }

    /**
     * Rounds any non-Quadrant rotation value to the nearest multiple of 90 degrees.
     *
     * <p>26.1 added strict Codec-based validation for {@code x}/{@code y} on the model root
     * and {@code rotation} inside face definitions (both must be 0, 90, 180, or 270).
     * Legacy packs occasionally write {@code "x": 1} or {@code "rotation": 1}, which are
     * rejected at load time. Rounding to the nearest Quadrant is the least-surprising repair.
     *
     * @return {@code true} if any value was changed
     */
    private static boolean sanitizeRotations(JsonObject model, AssetPath path) {
        boolean changed = false;

        // Model-level x / y rotation (used in blockstate variant entries, forwarded here).
        changed |= sanitizeIntField(model, "x", path);
        changed |= sanitizeIntField(model, "y", path);

        // Element-level face rotations.
        if (model.has("elements") && model.get("elements").isJsonArray()) {
            JsonArray elements = model.getAsJsonArray("elements");
            for (JsonElement el : elements) {
                if (!el.isJsonObject()) continue;
                JsonObject element = el.getAsJsonObject();
                if (!element.has("faces") || !element.get("faces").isJsonObject()) continue;
                JsonObject faces = element.getAsJsonObject("faces");
                for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                    if (!face.getValue().isJsonObject()) continue;
                    changed |= sanitizeIntField(face.getValue().getAsJsonObject(), "rotation", path);
                }
            }
        }

        return changed;
    }

    /**
     * If the named integer field exists and is not already a multiple of 90, round it and
     * return {@code true}.
     */
    private static boolean sanitizeIntField(JsonObject obj, String key, AssetPath path) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return false;
        int raw;
        try {
            raw = obj.get(key).getAsInt();
        } catch (NumberFormatException e) {
            return false;
        }
        if (raw % 90 == 0) return false;
        int rounded = (int) (Math.round(raw / 90.0) * 90);
        AssetBridge.LOGGER.debug("Sanitized rotation {}={} \u2192 {} in {}", key, raw, rounded, path);
        obj.addProperty(key, rounded);
        return true;
    }

    private static boolean sanitizeElements(JsonObject model) {
        if (!model.has("elements") || !model.get("elements").isJsonArray()) return false;
        boolean changed = false;
        JsonArray elements = model.getAsJsonArray("elements");
        for (JsonElement el : elements) {
            if (!el.isJsonObject()) continue;
            JsonObject element = el.getAsJsonObject();
            changed |= sanitizeVector3f(element, "from");
            changed |= sanitizeVector3f(element, "to");
        }
        return changed;
    }

    private static boolean sanitizeVector3f(JsonObject element, String key) {
        if (!element.has(key) || !element.get(key).isJsonArray()) return false;
        JsonArray arr = element.getAsJsonArray(key);
        if (arr.size() != 3) return false;
        boolean changed = false;
        for (int i = 0; i < 3; i++) {
            JsonElement item = arr.get(i);
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                float val = 0.0f;
                try {
                    val = Float.parseFloat(item.getAsString());
                } catch (NumberFormatException e) {
                    // Falls back to 0.0f when coordinates are written as non-numeric strings (e.g. formulas)
                }
                arr.set(i, new com.google.gson.JsonPrimitive(val));
                changed = true;
            }
        }
        return changed;
    }
}
