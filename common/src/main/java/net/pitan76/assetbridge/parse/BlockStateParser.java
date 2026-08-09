package net.pitan76.assetbridge.parse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.pitan76.assetbridge.util.Json;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Reduces an external blockstate JSON to the single model the MVP will render.
 *
 * <p>Blockstates describe a model per property combination; a bridged block has no
 * properties, so one representative model is picked: the empty variant when present,
 * otherwise the first declared one.
 */
public class BlockStateParser {
    private BlockStateParser() {
    }

    @Nullable
    public static String findModel(JsonObject blockState) {
        return modelOf(findVariant(blockState));
    }

    /**
     * The same choice as {@link #findModel}, but keeping the variant value whole: a weighted
     * variant is a list of models with a {@code weight} each, and collapsing it to one model
     * would throw away the randomisation the original mod intended. Callers that need a
     * stand-in for a state use this so the weights survive.
     *
     * @return a {@code {"model": ...}} object or an array of them, or {@code null}
     */
    @Nullable
    public static JsonElement findVariant(JsonObject blockState) {
        JsonObject variants = Json.object(blockState, "variants");
        if (variants != null) {
            JsonElement chosen = variants.has("") ? variants.get("") : firstValue(variants);
            return modelOf(chosen) == null ? null : chosen;
        }
        JsonArray parts = Json.array(blockState, "multipart");
        if (parts != null) {
            for (JsonElement part : parts) {
                if (!part.isJsonObject()) continue;
                // Prefer an unconditional part: it is the one always visible.
                JsonObject obj = part.getAsJsonObject();
                if (!obj.has("when") && modelOf(obj.get("apply")) != null) return obj.get("apply");
            }
            for (JsonElement part : parts) {
                if (!part.isJsonObject()) continue;
                JsonElement apply = part.getAsJsonObject().get("apply");
                if (modelOf(apply) != null) return apply;
            }
        }
        return null;
    }

    @Nullable
    private static JsonElement firstValue(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            return entry.getValue();
        }
        return null;
    }

    @Nullable
    private static String modelOf(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            return array.isEmpty() ? null : modelOf(array.get(0));
        }
        if (!element.isJsonObject()) return null;
        return Json.string(element.getAsJsonObject(), "model");
    }
}
