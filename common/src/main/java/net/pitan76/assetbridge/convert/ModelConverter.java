package net.pitan76.assetbridge.convert;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.AssetBridge;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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

    /**
     * A texture reference. {@code blocks/} and {@code items/} are flattened whatever the
     * namespace: the sprite files themselves are relocated the same way when the archive is
     * read, because from 1.19.3 the block atlas is defined as the contents of
     * {@code textures/block/} and would never stitch a sprite left behind in the plural
     * directory. See {@code AssetPath#flattened()}.
     *
     * <p>The reference is also lowercased, namespace included: {@code ResourceLocation} and
     * 26.1's {@code TextureSlots} both reject uppercase characters anywhere. Mod ids only became
     * case-sensitive-by-rule in 1.13, so a pre-1.13 archive happily writes
     * {@code BambooMod:blocks/bamboo} and 1.13+ rejects the whole model for it. The archive's own
     * files were lowercased on the way in ({@code AssetPath}), so this is also what makes the
     * reference match where the file actually went.
     */
    private static String renameTexture(String reference) {
        String renamed = rename(reference, true).toLowerCase(Locale.ROOT);
        int colon = renamed.indexOf(':');
        String namespace = colon < 0 ? "" : renamed.substring(0, colon + 1);
        String path = renamed.substring(colon + 1);

        // A pre-1.13 mod borrows vanilla sprites by their pre-1.13 names, which 1.13 renamed as
        // well as relocated. Only vanilla's own names are touched: a mod is free to have a
        // texture called planks_oak and it means its own file.
        if (namespace.isEmpty() || namespace.equals("minecraft:")) {
            String legacy = LegacyVanillaTextures.rename(path);
            if (legacy != null) path = legacy;
        }
        return namespace + path;
    }

    /**
     * Vanilla model templates that a mod may still name by an id this version no longer has.
     *
     * <p>{@code ModelReferenceResolver} takes a {@code minecraft:} parent on trust, because
     * checking it would mean shipping a list of every vanilla model. That trust is misplaced
     * exactly here: these templates were renamed between versions, so the reference resolves
     * on the version the mod was built for and nowhere else. Minecraft then substitutes the
     * missing model, and the child inherits elements pointing at {@code #missingno} &mdash; the
     * whole block renders as the missing texture.
     *
     * <p>Only renames apply here. A template that was removed outright has no replacement to
     * name and is left alone.
     */
    private static final Map<String, String> VANILLA_PARENT_REMAP = new HashMap<>();

    static {
        // 1.13 flattening renamed the slab templates.
        VANILLA_PARENT_REMAP.put("block/half_slab", "block/slab");
        VANILLA_PARENT_REMAP.put("block/upper_slab", "block/slab_top");
        // Same texture slots (#end, #side), and the name says it is the sideways one.
        VANILLA_PARENT_REMAP.put("block/column_side", "block/cube_column_horizontal");
    }

    /**
     * 1.20 replaced the four door templates with eight, folding the open states in. A mod built
     * for 1.20 or later names the eight; before it, an open door was the opposite hinge template
     * plus a rotation, which such a mod's blockstate already carries.
     */
    private static final Map<String, String> PRE_1_20_DOOR_TEMPLATES = new HashMap<>();

    static {
        PRE_1_20_DOOR_TEMPLATES.put("block/door_bottom_left", "block/door_bottom");
        PRE_1_20_DOOR_TEMPLATES.put("block/door_bottom_left_open", "block/door_bottom_rh");
        PRE_1_20_DOOR_TEMPLATES.put("block/door_bottom_right", "block/door_bottom_rh");
        PRE_1_20_DOOR_TEMPLATES.put("block/door_bottom_right_open", "block/door_bottom");
        PRE_1_20_DOOR_TEMPLATES.put("block/door_top_left", "block/door_top");
        PRE_1_20_DOOR_TEMPLATES.put("block/door_top_left_open", "block/door_top_rh");
        PRE_1_20_DOOR_TEMPLATES.put("block/door_top_right", "block/door_top_rh");
        PRE_1_20_DOOR_TEMPLATES.put("block/door_top_right_open", "block/door_top");
    }

    /**
     * A parent reference. Only vanilla is flattened here: model directories were never
     * renamed, so a mod that happens to keep its models under {@code models/blocks/} still
     * has them there and rewriting the reference would break it.
     */
    private static String renameParent(String reference) {
        // Lowercased whole, namespace included: see renameTexture.
        String renamed = rename(reference, false).toLowerCase(Locale.ROOT);
        int colon = renamed.indexOf(':');
        String namespace = colon < 0 ? "" : renamed.substring(0, colon + 1);
        String path = renamed.substring(colon + 1);

        if (namespace.isEmpty() || namespace.equals("minecraft:")) {
            String replacement = vanillaParentFor(path);
            if (replacement != null) path = replacement;
        }
        return namespace + path;
    }

    /** @return what this version calls that template, or {@code null} if it is fine as it is */
    @Nullable
    private static String vanillaParentFor(String path) {
        String renamed = VANILLA_PARENT_REMAP.get(path);
        if (renamed != null) return renamed;
        return usesPre1_20DoorTemplates() ? PRE_1_20_DOOR_TEMPLATES.get(path) : null;
    }

    /**
     * Whether this build's Minecraft still has the four pre-1.20 door templates.
     *
     * <p>Asked of the pack format rather than {@link AssetVersion}, whose boundaries sit at
     * 1.19.3 and 1.20.5 and so cannot express "before 1.20". 15 is the resource pack format
     * 1.20 shipped with.
     */
    private static boolean usesPre1_20DoorTemplates() {
        return net.pitan76.assetbridge.asset.RuntimePack.resourcePackFormat() < 15;
    }

    private static String rename(String reference, boolean anyNamespace) {
        int colon = reference.indexOf(':');
        String namespace = colon < 0 ? "" : reference.substring(0, colon + 1);
        String path = reference.substring(colon + 1);
        boolean vanilla = namespace.isEmpty() || namespace.equals("minecraft:");
        if (!vanilla && !anyNamespace) return reference;

        if (path.startsWith("blocks/")) {
            path = "block/" + path.substring("blocks/".length());
        } else if (path.startsWith("items/")) {
            path = "item/" + path.substring("items/".length());
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
