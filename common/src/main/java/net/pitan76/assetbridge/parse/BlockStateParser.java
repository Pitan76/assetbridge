package net.pitan76.assetbridge.parse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
        if (blockState.has("variants")) {
            JsonObject variants = blockState.getAsJsonObject("variants");
            JsonElement chosen = variants.has("") ? variants.get("") : firstValue(variants);
            return modelOf(chosen);
        }
        if (blockState.has("multipart")) {
            JsonArray parts = blockState.getAsJsonArray("multipart");
            for (JsonElement part : parts) {
                if (!part.isJsonObject()) continue;
                // Prefer an unconditional part: it is the one always visible.
                JsonObject obj = part.getAsJsonObject();
                if (!obj.has("when")) {
                    String model = modelOf(obj.get("apply"));
                    if (model != null) return model;
                }
            }
            for (JsonElement part : parts) {
                if (!part.isJsonObject()) continue;
                String model = modelOf(part.getAsJsonObject().get("apply"));
                if (model != null) return model;
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
        JsonObject object = element.getAsJsonObject();
        return object.has("model") ? object.get("model").getAsString() : null;
    }
}
