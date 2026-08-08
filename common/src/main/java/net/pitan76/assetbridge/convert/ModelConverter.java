package net.pitan76.assetbridge.convert;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.AssetPath;
import net.pitan76.assetbridge.asset.AssetVersion;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Normalises block/item model JSON towards the 1.18.2 spec.
 *
 * <p>Handled differences:
 * <ul>
 *   <li>{@link AssetVersion#LEGACY}: pre-1.13 models refer to textures as {@code blocks/stone}
 *       and parents as {@code block/cube_all} under the old directory names.</li>
 *   <li>{@link AssetVersion#FUTURE}: 1.19.3+ may carry keys 1.18.2's model deserialiser
 *       rejects; they are stripped rather than failing the whole model.</li>
 * </ul>
 */
public class ModelConverter implements AssetConverter {
    /** Keys introduced after 1.18.2 that the 1.18.2 model deserialiser does not understand. */
    private static final String[] UNKNOWN_FUTURE_KEYS = {"overrides_v2", "oversized_in_gui"};

    @Override
    @Nullable
    public byte[] convert(AssetPath path, byte[] data, AssetVersion from) {
        JsonObject model = Json.parse(new String(data, StandardCharsets.UTF_8));
        if (model == null) return null;

        boolean changed = false;
        if (from == AssetVersion.LEGACY) {
            changed = renameLegacyDirectories(model);
        }
        if (from == AssetVersion.FUTURE) {
            for (String key : UNKNOWN_FUTURE_KEYS) {
                changed |= model.remove(key) != null;
            }
        }
        return changed ? Json.toString(model).getBytes(StandardCharsets.UTF_8) : data;
    }

    /** Pre-flattening packs use {@code blocks/} and {@code items/}; 1.13+ uses the singular form. */
    private static boolean renameLegacyDirectories(JsonObject model) {
        boolean changed = false;
        if (model.has("parent")) {
            String parent = model.get("parent").getAsString();
            String renamed = renameDirectory(parent);
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
                String renamed = renameDirectory(value);
                if (!renamed.equals(value)) {
                    entry.setValue(new com.google.gson.JsonPrimitive(renamed));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static String renameDirectory(String reference) {
        int colon = reference.indexOf(':');
        String namespace = colon < 0 ? "" : reference.substring(0, colon + 1);
        String path = reference.substring(colon + 1);
        if (path.startsWith("blocks/")) return namespace + "block/" + path.substring("blocks/".length());
        if (path.startsWith("items/")) return namespace + "item/" + path.substring("items/".length());
        return reference;
    }
}
